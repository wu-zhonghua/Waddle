package dev.waddle.android

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.files.FileAccess
import dev.waddle.android.files.FileBrowserController
import dev.waddle.android.terminal.TerminalWorkspace
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FileBrowserIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val access = FileAccess(context)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun document(id: String) = access.document(DocumentsContract.buildDocumentUriUsingTree(
        Uri.parse("content://dev.waddle.android.test.files/tree/root"), id))
    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>(); instrumentation.runOnMainSync { result.set(block()) }; return result.get()
    }
    private fun await(block: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) { if (main(block)) return; SystemClock.sleep(40) }
        fail("Timed out waiting for file browser or terminal")
    }
    private fun fixture(block: (File) -> Unit) {
        val root = File(context.cacheDir, "files-test-${System.nanoTime()}").apply { mkdirs() }
        try { block(root) } finally { root.deleteRecursively() }
    }

    @Test fun localBrowsingSortsFoldersAndNavigatesQuotedUnicodePaths() = fixture { root ->
        val nested = File(root, "中文 ' folder").apply { mkdir() }
        File(root, "a.txt").writeText("plain")
        File(root, ".hidden").writeText("hidden")
        assertEquals(nested.name, access.children(access.local(root)).first().name)
        val browser = main { FileBrowserController(context, root) }
        try {
            await { !browser.loading }
            assertEquals(3, main { browser.entries.size })
            main { browser.enter(browser.entries.first()) }
            await { !browser.loading && browser.current?.file?.canonicalFile == nested.canonicalFile }
            assertTrue(main { browser.entries.isEmpty() })
            main { browser.up() }
            await { !browser.loading && browser.current?.file?.canonicalFile == root.canonicalFile }
            assertEquals(3, main { browser.entries.size })
            main { browser.openLocal(File(root, "missing")) }
            await { !browser.loading && browser.error != null }
        } finally { main { browser.close() } }
    }

    @Test fun sharedTreeImportPreservesContentsAndNeverOverwrites() = fixture { root ->
        val source = document("root")
        assertTrue(source.directory)
        val first = access.import(source, root)
        val second = access.import(source, root)
        assertNotEquals(first.path, second.path)
        assertEquals("你好，shared file\n", File(first, "note '你好.txt").readText())
        assertEquals("printf 'nested\\n'\n", File(first, "子 目录/code.sh").readText())
        assertEquals(File(first, "note '你好.txt").readText(), File(second, "note '你好.txt").readText())
        assertEquals("你好，shared file\n", access.preview(document("note")).text)
    }

    @Test fun invalidNamesDuplicatesCyclesLimitsAndCancellationPublishNothing() = fixture { root ->
        for (id in listOf("unsafe", "duplicate", "cycle", "large")) {
            assertTrue("Import $id should fail", runCatching { access.import(document(id), root) }.isFailure)
            assertFalse(File(root, "Imports").exists())
        }
        var checks = 0
        assertTrue(runCatching { access.import(document("root"), root, { if (++checks > 3) throw CancellationException() }) }.isFailure)
        assertFalse(File(root, "Imports").exists())
        assertFalse(File(context.cacheDir, "escape").exists())
        assertTrue(File(context.filesDir, "import-staging").listFiles().orEmpty().isEmpty())
    }

    @Test fun controllerCompletesImportAndCancelsWithoutPublishingAnotherCopy() = fixture { root ->
        val browser = main { FileBrowserController(context, root) }
        try {
            await { !browser.loading }
            val source = document("root")
            main { browser.import(source) }
            await { !browser.importBusy }
            assertNotNull(main { browser.notice }, main { browser.imported })
            assertTrue(main { browser.imported!!.file.isDirectory })
            val before = File(root, "Imports").listFiles()!!.size
            val long = document("long")
            main { browser.import(long); browser.cancelImport() }
            await { !browser.importBusy }
            assertNull(main { browser.imported })
            assertTrue(main { browser.notice }, main { browser.notice.orEmpty().contains("取消") })
            assertEquals(before, File(root, "Imports").listFiles()!!.size)
        } finally { main { browser.close() } }
    }

    @Test fun previewsAreBoundedAndPermissionFailuresAreVisible() {
        val preview = access.preview(document("long"))
        assertTrue(preview.truncated)
        assertEquals(FileAccess.PREVIEW_BYTES, preview.text.toByteArray().size)
        assertTrue(runCatching { access.preview(document("binary")) }.isFailure)
        assertTrue(runCatching { document("denied") }.isFailure)
    }

    @Test fun openingFolderUsesOriginalShellAndRejectsRunningProgram() {
        val workspace = main { TerminalWorkspace(context) }
        val root = File(workspace.runtime.home, "图形 ' folder-${System.nanoTime()}").apply { mkdir() }
        try {
            val session = main { workspace.create() }
            await { session.promptReady }
            assertTrue(main { workspace.changeDirectory(root).isSuccess })
            await { session.promptReady && session.cwd == root.canonicalPath }
            val other = main { workspace.create(root) }
            await { other.promptReady }
            assertEquals(root.canonicalPath, main { other.cwd })
            main { other.terminal.write("read -r answer\r") }
            await { !other.promptReady }
            assertTrue(main { workspace.changeDirectory(workspace.runtime.home).isFailure })
            main { other.terminal.write("\u0003") }
            await { other.promptReady }
            assertTrue(main { runCatching { workspace.create(File(root, "missing")) }.isFailure })
        } finally { main { workspace.closeAll(); workspace.files.close() }; root.deleteRecursively() }
    }
}
