package dev.waddle.android.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.nio.file.Files

/** Local paths stay local. Only platform external-storage documents can resolve against verified volumes; cloud URIs remain documents. */
data class FileEntry(val uri: Uri, val name: String, val directory: Boolean, val size: Long?, val mime: String) {
    val local get() = uri.scheme == "file"
    val file get() = File(requireNotNull(uri.path))
}
data class FilePreview(val text: String, val truncated: Boolean = false)
data class ImportProgress(val name: String, val entries: Int, val bytes: Long)

class FileAccess(private val context: Context) {
    private val resolver get() = context.contentResolver
    companion object {
        const val MAX_BYTES = 512L * 1024 * 1024
        const val MAX_ENTRIES = 5000
        const val MAX_DEPTH = 32
        const val PREVIEW_BYTES = 64 * 1024
        fun safeName(name: String): String {
            require(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it < ' ' || it == '\u007f' }) {
                "文件名无效，无法安全导入：${name.take(80)}"
            }
            require(name.toByteArray().size <= 240) { "文件名过长，无法导入。" }
            return name
        }
    }

    fun local(file: File): FileEntry {
        PhoneStorage(context).requireAccess(file)
        check(file.exists()) { "文件或目录已不存在。" }
        val mime = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        return FileEntry(Uri.fromFile(file.absoluteFile), file.name.ifEmpty { "/" }, file.isDirectory, if (file.isFile) file.length() else null, mime)
    }

    fun document(uri: Uri): FileEntry {
        require(uri.scheme == "content") { "不支持这个文件来源。" }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            check(cursor.moveToFirst()) { "文件已不存在，或授权已失效。" }
            return FileEntry(uri, cursor.getString(0) ?: "未命名文件", mime == DocumentsContract.Document.MIME_TYPE_DIR,
                if (cursor.isNull(1)) null else cursor.getLong(1), mime)
        }
        error("无法读取文件信息，请重新选择。")
    }

    fun children(parent: FileEntry): List<FileEntry> {
        require(parent.directory)
        val entries = if (parent.local) {
            PhoneStorage(context).requireAccess(parent.file)
            val result = mutableListOf<FileEntry>()
            Files.newDirectoryStream(parent.file.toPath()).use { stream ->
                for (path in stream) {
                    check(result.size < MAX_ENTRIES) { "此目录超过 $MAX_ENTRIES 项，请选择更小的子目录。" }
                    runCatching { local(path.toFile()) }.getOrNull()?.let { result += it }
                }
            }
            result
        } else {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(parent.uri, DocumentsContract.getDocumentId(parent.uri))
            val result = mutableListOf<FileEntry>()
            val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE)
            resolver.query(uri, columns, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    check(result.size < MAX_ENTRIES) { "此目录超过 $MAX_ENTRIES 项，请选择更小的子目录。" }
                    val mime = cursor.getString(2) ?: "application/octet-stream"
                    result += FileEntry(DocumentsContract.buildDocumentUriUsingTree(parent.uri, cursor.getString(0)),
                        cursor.getString(1) ?: "未命名文件", mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        if (cursor.isNull(3)) null else cursor.getLong(3), mime)
                }
            } ?: error("无法读取目录，请重新授权或检查存储设备。")
            result
        }
        return entries.sortedWith(compareBy<FileEntry> { !it.directory }.thenBy { it.name.lowercase() })
    }

    fun directDirectory(entry: FileEntry): File? {
        if (entry.local) return entry.file
        if (!entry.directory || entry.uri.authority != "com.android.externalstorage.documents") return null
        return runCatching { PhoneStorage(context).resolveDocument(DocumentsContract.getDocumentId(entry.uri)) }.getOrNull()
    }

    private fun open(entry: FileEntry): InputStream = if (entry.local) entry.file.inputStream()
        else resolver.openInputStream(entry.uri) ?: error("无法打开这个文件。")

    fun preview(entry: FileEntry): FilePreview {
        require(!entry.directory)
        val bytes = open(entry).use { it.readBytesLimited(PREVIEW_BYTES + 1) }
        val text = bytes.take(PREVIEW_BYTES).toByteArray().toString(Charsets.UTF_8)
        check(!text.contains('\u0000') && text.count { it == '\ufffd' } <= 1 &&
            text.count { it < ' ' && it !in "\n\r\t" } <= text.length / 50) {
            "此文件不适合文本预览。可以导入后交给支持此格式的工具处理。"
        }
        return FilePreview(text, bytes.size > PREVIEW_BYTES)
    }

    /** Atomic publication: partial files are private staging data, never shown as a completed import. */
    fun import(entry: FileEntry, home: File, checkCancelled: () -> Unit = {}, progress: (ImportProgress) -> Unit = {}): File {
        require(!entry.local) { "这个文件已经在本机目录中，无需导入。" }
        val name = safeName(entry.name)
        val stagingRoot = File(context.filesDir, "import-staging").apply { mkdirs() }
        check(stagingRoot.canonicalFile == File(context.filesDir.canonicalFile, "import-staging")) { "临时导入目录无效。" }
        val staging = File(stagingRoot, UUID.randomUUID().toString()).apply { check(mkdir()) }
        var bytes = 0L
        var count = 0
        val visited = mutableSetOf<String>()
        fun copy(source: FileEntry, destination: File, depth: Int) {
            checkCancelled()
            check(depth <= MAX_DEPTH && ++count <= MAX_ENTRIES) { "目录太大或层级超过 $MAX_DEPTH 层，请选择较小的子目录。" }
            check(visited.add(source.uri.toString())) { "文件提供方返回了重复或循环目录。" }
            progress(ImportProgress(source.name, count, bytes))
            if (source.directory) {
                check(destination.mkdir()) { "目录中有重名项目，无法导入。" }
                children(source).forEach { copy(it, File(destination, safeName(it.name)), depth + 1) }
            } else {
                check(source.size == null || source.size <= MAX_BYTES - bytes) { "单次导入不能超过 512 MiB。" }
                check(destination.createNewFile()) { "目录中有重名文件，无法导入。" }
                open(source).use { input -> destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        bytes += read
                        check(bytes <= MAX_BYTES) { "单次导入不能超过 512 MiB。" }
                        output.write(buffer, 0, read)
                        progress(ImportProgress(source.name, count, bytes))
                    }
                } }
            }
        }
        try {
            copy(entry, File(staging, name), 0)
            checkCancelled()
            val imports = File(home, "Imports")
            check(!imports.exists() || imports.isDirectory) { "Imports 已被同名文件占用。" }
            imports.mkdirs()
            check(imports.canonicalFile == File(home.canonicalFile, "Imports")) { "Imports 不能是指向其他位置的链接。" }
            val target = File(imports, "导入-${UUID.randomUUID()}")
            check(!target.exists() && staging.renameTo(target)) { "保存导入结果失败，请检查可用空间。" }
            return File(target, name)
        } finally { staging.deleteRecursively() }
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < limit) {
            val size = read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (size < 0) break
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }
}
