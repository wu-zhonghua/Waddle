package dev.waddle.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.files.*
import dev.waddle.android.terminal.TerminalWorkspace
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
class FileViewingIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val access = FileAccess(context)
    private val viewing = FileViewing(context)
    private fun <T> main(block: () -> T): T { val value = AtomicReference<T>(); instrumentation.runOnMainSync { value.set(block()) }; return value.get() }
    private fun await(label: String = "viewer", block: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < until) { if (block()) return; SystemClock.sleep(80) }
        fail("Timed out: $label; UI=" + nodes().mapNotNull { it.text ?: it.contentDescription }.take(50))
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        fun walk(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
            if (node == null) return emptyList()
            node.refresh()
            return listOf(node) + (0 until node.childCount).flatMap { walk(node.getChild(it)) }
        }
        return walk(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun visible(text: String) = nodes().any { it.text?.toString() == text || it.contentDescription?.toString() == text }
    private fun click(text: String) {
        await("click $text") {
            var node = nodes().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
            var clicked = false
            while (node != null && !clicked) { if (node.isClickable && node.isEnabled) clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK); node = node.parent }
            clicked
        }
        // Media continuously schedules frames; wait for each asserted state instead of global UI idleness.
    }
    private fun fixture(block: (File) -> Unit) {
        val root = File(context.cacheDir, "viewer-test-${System.nanoTime()}").apply { mkdirs() }
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun image(root: File, name: String = "图 '片 sample.png", format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, width: Int = 240, height: Int = 180): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        return File(root, name).also { file -> file.outputStream().use { bitmap.compress(format, 85, it) }; bitmap.recycle() }
    }
    private fun pdf(root: File): File {
        val file = File(root, "多页.pdf")
        val doc = PdfDocument()
        try {
            repeat(3) { n -> val page = doc.startPage(PdfDocument.PageInfo.Builder(300, 400, n + 1).create()); page.canvas.drawColor(if (n == 0) Color.RED else Color.BLUE); doc.finishPage(page) }
            file.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
        return file
    }
    private fun workspace(scenario: ActivityScenario<MainActivity>): TerminalWorkspace {
        var workspace: TerminalWorkspace? = null
        await { scenario.onActivity { workspace = it.workspace }; workspace != null }
        main { workspace!!.files.dismissSelection() }
        return workspace!!
    }

    @Test fun referencesResolveChineseSpacesQuotesRelativePathsAndRejectRemoteUrls() {
        val cwd = File("/work/project"); val home = File("/home/user")
        val text = "[图片](</work/中文 ' image.png>) `~/文 件.txt` [目录](/work/a%20b) ./plot.webp file:///work/a+b.jpg https://example.org/no.png"
        val paths = FileReferences.find(text, cwd, home).map { it.file.path }
        assertTrue(paths.toString(), paths.contains("/work/中文 ' image.png"))
        assertTrue(paths.contains("/home/user/文 件.txt")); assertTrue(paths.contains("/work/a b"))
        assertTrue(paths.contains("/work/project/./plot.webp")); assertTrue(paths.contains("/work/a+b.jpg"))
        assertFalse(paths.toString(), paths.any { it.contains("example.org") })
        assertEquals("/work/中文 image.png", FileReferences.find("图片在 /work/中文 image.png", cwd, home).single().file.path)
        assertEquals("/work/project/assets", FileReferences.find("[目录](assets)", cwd, home).single().file.path)
        assertEquals("/work/a.kt", FileReferences.resolve("/work/a.kt:12:3", cwd, home)?.path)
        assertNull(FileReferences.resolve("printf image-bytes > generated.png && test -s generated.png", cwd, home))
        assertEquals("/work/project/a && b.png", FileReferences.find("[image](<a && b.png>)", cwd, home).single().file.path)
    }

    @Test fun decodersBoundLargeImagesHandleFormatsOrientationAndRejectCorruption() = fixture { root ->
        val images = listOf(image(root), image(root, "photo.jpg", Bitmap.CompressFormat.JPEG), image(root, "pic.webp", Bitmap.CompressFormat.WEBP))
        for (file in images) { val d = viewing.image(access.local(file), 128); assertTrue(d.intrinsicWidth <= 128); assertTrue(d.intrinsicHeight > 0) }
        val photo = images[1]
        android.media.ExifInterface(photo.path).apply { setAttribute(android.media.ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
        val rotated = viewing.image(access.local(photo), 480)
        assertTrue("EXIF portrait orientation", rotated.intrinsicHeight > rotated.intrinsicWidth)
        val gif = File(root, "small.gif").apply { writeBytes(android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", 0)) }
        assertNotNull(viewing.image(access.local(gif), 480))
        val large = image(root, "large.png", width = 6000, height = 4000)
        val decoded = viewing.image(access.local(large), 1024)
        assertTrue(decoded.intrinsicWidth <= 1024)
        val entry = access.local(images[0]); images[0].writeText("broken image")
        assertTrue(runCatching { viewing.image(entry, 480) }.isFailure)
        images[0].delete(); assertTrue(runCatching { viewing.checked(entry) }.isFailure)
    }

    @Test fun imageViewPinchAndDragChangeRenderedPixels() {
        main {
            val bitmap = Bitmap.createBitmap(240, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            android.graphics.Canvas(bitmap).drawRect(120f, 0f, 240f, 180f, android.graphics.Paint().apply { color = Color.RED })
            val view = dev.waddle.android.ui.ZoomImageView(context)
            view.layout(0, 0, 1200, 1200)
            view.show(android.graphics.drawable.BitmapDrawable(context.resources, bitmap))
            fun render(): Bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
            val before = render()
            val down = SystemClock.uptimeMillis(); var time = down
            fun touch(action: Int, xs: List<Float>) {
                val props = xs.indices.map { index -> android.view.MotionEvent.PointerProperties().apply { id = index; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
                val coords = xs.map { x -> android.view.MotionEvent.PointerCoords().apply { this.x = x * 4; y = 600f; pressure = 1f; size = 1f } }.toTypedArray()
                time += 25
                val event = android.view.MotionEvent.obtain(down, time, action, xs.size, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
                view.onTouchEvent(event); event.recycle()
            }
            touch(android.view.MotionEvent.ACTION_DOWN, listOf(120f))
            touch(android.view.MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), listOf(120f, 180f))
            for (spread in 40..120 step 10) touch(android.view.MotionEvent.ACTION_MOVE, listOf(150f - spread, 150f + spread))
            touch(android.view.MotionEvent.ACTION_POINTER_UP or (1 shl 8), listOf(30f, 270f))
            touch(android.view.MotionEvent.ACTION_UP, listOf(30f))
            val zoomed = render()
            assertNotEquals("Pinch fills previously blank margin", before.getPixel(200, 128), zoomed.getPixel(200, 128))
            touch(android.view.MotionEvent.ACTION_DOWN, listOf(150f))
            touch(android.view.MotionEvent.ACTION_MOVE, listOf(210f))
            touch(android.view.MotionEvent.ACTION_UP, listOf(210f))
            val panned = render()
            assertNotEquals("Drag moves the color boundary", zoomed.getPixel(640, 600), panned.getPixel(640, 600))
            val surrounding = Bitmap.createBitmap(1600, 1600, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(surrounding).apply { translate(200f, 200f); view.draw(this) }
            assertEquals("Zoom must not paint over controls above the view", Color.TRANSPARENT, surrounding.getPixel(800, 100))
            assertEquals("Zoom must not paint outside the view sides", Color.TRANSPARENT, surrounding.getPixel(100, 800))
            surrounding.recycle()
            view.release(); before.recycle(); zoomed.recycle(); panned.recycle(); bitmap.recycle()
        }
    }

    @Test fun sharingGrantsOnlySelectedSnapshotAndSavingNeverOverwrites() = fixture { root ->
        val file = image(root, "save-${System.nanoTime()}.png"); val entry = access.local(file)
        val uri = viewing.shareUri(entry)
        assertEquals("content", uri.scheme)
        assertArrayEquals(file.readBytes(), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        val send = viewing.intent(entry, uri, true)
        assertEquals(Intent.ACTION_SEND, send.action); assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        file.writeText("changed"); assertNotEquals(file.readText(), context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
        image(root, file.name)
        val first = viewing.save(entry); val second = viewing.save(entry)
        assertTrue(first.contains("Pictures/Waddle")); assertNotEquals(first, second)
        val resolver = context.contentResolver
        val saved = mutableListOf<Uri>()
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME), "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf(file.nameWithoutExtension + "%"), null)!!.use { cursor ->
            while (cursor.moveToNext()) saved += Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0).toString())
        }
        try { assertEquals(2, saved.size); saved.forEach { assertArrayEquals(file.readBytes(), resolver.openInputStream(it)!!.use { stream -> stream.readBytes() }) } }
        finally { saved.forEach { resolver.delete(it, null, null) } }
        val text = File(root, "download-${System.nanoTime()}.txt").apply { writeText("download data") }
        assertTrue(viewing.save(access.local(text)).contains("Download/Waddle"))
        resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(text.name))
    }

    @Test fun pdfRendersDifferentPagesAndMalformedPdfFails() = fixture { root ->
        val entry = access.local(pdf(root))
        val first = viewing.pdf(entry, 0, 400); val last = viewing.pdf(entry, 2, 400)
        assertEquals(3, first.second); assertEquals(Color.RED, first.first.getPixel(10, 10)); assertEquals(Color.BLUE, last.first.getPixel(10, 10))
        assertTrue(first.first.height <= 400); first.first.recycle(); last.first.recycle()
        entry.file.writeText("broken"); assertTrue(runCatching { viewing.pdf(entry, 0) }.isFailure)
    }

    @Test fun terminalOpenUsesOriginalPtyAndInheritedBashFunctionWhileProgramContinues() = fixture { root ->
        val workspace = main { TerminalWorkspace(context) }
        val file = image(root)
        try {
            val shell = main { workspace.create(root) }
            await { main { shell.promptReady } }
            main { shell.terminal.write("waddle-open ${quote(file.name)}; sleep 2; echo VIEWER_ALIVE\r") }
            await { main { workspace.files.selected?.file?.canonicalFile == file.canonicalFile } }
            assertTrue(main { shell.running }); main { workspace.files.dismissSelection() }
            await { main { shell.terminal.emulator.screen.transcriptText.contains("\nVIEWER_ALIVE") && shell.promptReady } }
            main { shell.terminal.write("/system/bin/setsid bash -lc ${quote("waddle-open ${quote(file.path)}; sleep 1") } </dev/null >/dev/null 2>&1\r") }
            await { main { workspace.files.selected?.file?.canonicalFile == file.canonicalFile } }
            assertTrue(main { shell.running })
        } finally { main { workspace.closeAll(); workspace.files.close() } }
    }
    private fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"

    @Test fun viewerBackRotationPdfAndTextModesPreserveShell() = fixture { root ->
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = workspace(scenario)
            val shell = main { workspace.selected ?: workspace.create() }
            val original = shell.terminal
            val picture = image(root)
            main { workspace.files.select(access.local(picture)) }
            await { visible("图片 ${picture.name}，双指缩放、拖动") }
            scenario.recreate()
            await { visible("图片 ${picture.name}，双指缩放、拖动") }
            click("返回"); assertSame(original, main { workspace.selected!!.terminal }); assertTrue(main { shell.running })
            main { workspace.files.select(access.local(pdf(root))) }
            await { visible("1 / 3") }; click("下一页"); await { visible("2 / 3") }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { main { workspace.files.selected == null } }
            for ((name, content) in listOf("readme.md" to "# 标题\n\n**加粗**\n\n|列|值|\n|--|--|\n|a|b|", "page.html" to "<h1>标题</h1><script>document.body.innerHTML='UNSAFE'</script>")) {
                val file = File(root, name).apply { writeText(content) }
                main { workspace.files.select(access.local(file)) }
                await { visible("原文") }; click("原文"); await { visible(content) }; click("排版")
                await { nodes().any { it.className?.toString() == "android.webkit.WebView" } }
                assertFalse(visible("UNSAFE")); click("返回")
            }
            assertSame(original, main { workspace.selected!!.terminal })
        }
    }

    @Test fun audioAndVideoCanPlayPauseAndSeek() = fixture { root ->
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = workspace(scenario)
            for (name in listOf("sample.mp4", "sample.wav")) {
                val file = File(root, name)
                instrumentation.context.assets.open("viewer/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
                main { workspace.files.select(access.local(file)) }
                await("media prepared $name") { visible("播放") }
                click("播放"); await { visible("暂停") }
                await { nodes().any { it.text?.toString()?.matches(Regex("[1-7] / 8 秒")) == true } }
                click("暂停"); await { visible("播放") }
                val slider = nodes().first { it.actionList.any { action -> action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id } }
                assertTrue(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, android.os.Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 2000f) }))
                await { nodes().any { it.text?.toString()?.startsWith("2 / 8") == true } }
                click("返回"); await { main { workspace.files.selected == null } }
            }
        }
    }
}
