package dev.waddle.android.files

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

enum class ViewKind { IMAGE, PDF, MEDIA, MARKDOWN, HTML, TEXT, OTHER }

/** The same classification is used by messages and the file browser. */
fun FileEntry.viewKind(): ViewKind {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp", "gif", "heic", "heif", "bmp") -> ViewKind.IMAGE
        mime == "application/pdf" || ext == "pdf" -> ViewKind.PDF
        mime.startsWith("video/") || mime.startsWith("audio/") || ext in setOf("mp4", "m4v", "webm", "mkv", "mp3", "m4a", "wav", "ogg", "flac") -> ViewKind.MEDIA
        ext in setOf("md", "markdown") -> ViewKind.MARKDOWN
        ext in setOf("html", "htm") || mime == "text/html" -> ViewKind.HTML
        mime.startsWith("text/") || ext in setOf("json", "yaml", "yml", "toml", "xml", "kt", "java", "py", "sh", "js", "ts", "css", "log", "csv", "rs", "go", "c", "h") || ext.isEmpty() -> ViewKind.TEXT
        else -> ViewKind.OTHER
    }
}

fun fileError(error: Throwable): String = when (error) {
    is SecurityException -> "没有读取权限，或授权已失效，请重新选择文件或检查手机存储授权。"
    is java.io.FileNotFoundException, is java.nio.file.NoSuchFileException -> "文件已不存在、被删除，或无法读取。请刷新后重试。"
    is OutOfMemoryError -> "文件太大，无法预览。可尝试用其他应用打开。"
    else -> error.message?.takeIf { it.any { c -> c.code in 0x4e00..0x9fff } }
        ?: "无法读取此文件，可能格式损坏、不受支持或没有权限。可尝试用其他应用打开。"
}

class FileViewing(private val context: Context) {
    private val access = FileAccess(context)
    fun checked(entry: FileEntry): FileEntry {
        val fresh = if (entry.local) access.local(entry.file) else access.document(entry.uri)
        require(!fresh.directory) { "请选择文件。" }
        access.open(fresh).use { it.read() }
        return fresh
    }

    fun image(entry: FileEntry, edge: Int): Drawable {
        checked(entry)
        if (Build.VERSION.SDK_INT >= 28) {
            val source = if (entry.local) ImageDecoder.createSource(entry.file) else ImageDecoder.createSource(context.contentResolver, entry.uri)
            return ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val ratio = minOf(1.0, edge.toDouble() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * ratio).roundToInt()), maxOf(1, (info.size.height * ratio).roundToInt()))
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        access.open(entry).use { BitmapFactory.decodeStream(it, null, options) }
        check(options.outWidth > 0 && options.outHeight > 0) { "图片格式损坏或不受支持。" }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > edge) options.inSampleSize *= 2
        val bitmap = access.open(entry).use { BitmapFactory.decodeStream(it, null, options) } ?: error("图片无法解码。")
        val orientation = access.open(entry).use { stream -> runCatching {
            android.media.ExifInterface(stream).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        }.getOrDefault(1) }
        val matrix = android.graphics.Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        val oriented = if (orientation in 2..8) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true) else bitmap
        if (oriented !== bitmap) bitmap.recycle()
        return BitmapDrawable(context.resources, oriented)
    }

    // Copy only the explicitly selected file. Never expose home, credentials, or a writable root URI.
    fun shareUri(entry: FileEntry): Uri {
        checked(entry)
        val root = File(context.cacheDir, "file-exports").apply { mkdirs() }
        root.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.deleteRecursively() }
        val dir = File(root, UUID.randomUUID().toString()).apply { check(mkdir()) }
        val target = File(dir, FileAccess.safeName(entry.name))
        try { access.open(entry).use { input -> target.outputStream().use { input.copyTo(it) } } }
        catch (e: Exception) { dir.deleteRecursively(); throw e }
        return FileProvider.getUriForFile(context, "${context.packageName}.file-exports", target)
    }

    fun intent(entry: FileEntry, uri: Uri, share: Boolean): Intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
        if (share) { type = entry.mime; putExtra(Intent.EXTRA_STREAM, uri) } else setDataAndType(uri, entry.mime)
        clipData = ClipData.newUri(context.contentResolver, entry.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Pending rows are published only after a complete copy; MediaStore allocates unique filenames. */
    fun save(entry: FileEntry): String {
        checked(entry)
        if (Build.VERSION.SDK_INT < 29) error("此 Android 版本请使用系统选择器保存到相册或下载目录。")
        val images = entry.viewKind() == ViewKind.IMAGE
        val folder = if (images) "${Environment.DIRECTORY_PICTURES}/Waddle" else "${Environment.DIRECTORY_DOWNLOADS}/Waddle"
        val collection = if (images) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FileAccess.safeName(entry.name))
            put(MediaStore.MediaColumns.MIME_TYPE, entry.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: error("无法保存，请检查手机可用空间。")
        try {
            resolver.openOutputStream(uri)?.use { output -> access.open(entry).use { it.copyTo(output) } } ?: error("无法写入手机存储。")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0)
            val name = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: entry.name
            return "已保存到 $folder/$name${if (images) "，可在相册中查看" else "，可在手机文件应用中查看"}。"
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }

    fun saveTo(entry: FileEntry, uri: Uri) {
        checked(entry)
        context.contentResolver.openOutputStream(uri)?.use { output -> access.open(entry).use { it.copyTo(output) } } ?: error("无法保存文件。")
    }

    fun pdf(entry: FileEntry, pageIndex: Int, edge: Int = 2400): Pair<Bitmap, Int> {
        checked(entry)
        // SAF providers can return pipes. A bounded temporary copy guarantees PdfRenderer a seekable FD.
        val temp = if (!entry.local) File.createTempFile("pdf-", ".pdf", context.cacheDir) else null
        try {
            if (temp != null) access.open(entry).use { input -> temp.outputStream().use { output ->
                val buffer = ByteArray(65536); var total = 0L
                while (true) { val n = input.read(buffer); if (n < 0) break; total += n; check(total <= FileAccess.MAX_BYTES) { "PDF 超过 512 MiB，请用其他应用打开。" }; output.write(buffer, 0, n) }
            } }
            ParcelFileDescriptor.open(temp ?: entry.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    check(renderer.pageCount > 0) { "PDF 没有可显示的页面。" }
                    renderer.openPage(pageIndex.coerceIn(0, renderer.pageCount - 1)).use { page ->
                        val ratio = minOf(3.0, edge.toDouble() / maxOf(page.width, page.height))
                        val bitmap = Bitmap.createBitmap(maxOf(1, (page.width * ratio).toInt()), maxOf(1, (page.height * ratio).toInt()), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap to renderer.pageCount
                    }
                }
            }
        } finally { temp?.delete() }
    }
}
