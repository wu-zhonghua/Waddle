package dev.waddle.android

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.terminal.WaddleRuntime
import dev.waddle.android.files.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PhoneStorageIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            if (main(condition)) return
            SystemClock.sleep(50)
        }
        fail("File browser did not settle")
    }

    @Test fun documentMappingNeverEscapesVolumeOrGuessesOtherProviders() {
        val root = File(context.cacheDir, "volume-${System.nanoTime()}").apply { mkdirs() }
        try {
            val nested = File(root, "中文 ' folder").apply { mkdir() }
            assertEquals(nested.canonicalFile, PhoneStorage.checkedChild(root, nested.name))
            for (path in listOf("../outside", "/absolute", "a/../../b", "./a", "x\\y", "missing")) assertNull(path, PhoneStorage.checkedChild(root, path))
            val fake = FileEntry(Uri.parse("content://cloud.example/tree/primary%3ADownload/document/primary%3ADownload"), "cloud", true, null, DocumentsContract.Document.MIME_TYPE_DIR)
            assertNull(FileAccess(context).directDirectory(fake))
        } finally { root.deleteRecursively() }
    }

    @Test fun deniedPermissionShowsUnavailableOriginalWithoutImporting() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("waddleStorage") == "denied")
        assertFalse(PhoneStorage(context).allowed())
        val browser = main { FileBrowserController(context, File(context.filesDir, "home")) }
        try {
            main { browser.openLocal(File(Environment.getExternalStorageDirectory(), "Download/WaddleAccessProbe")) }
            await { !browser.loading }
            assertNotNull(main { browser.error })
            assertNull(main { browser.imported })
            assertFalse(main { browser.importBusy })
            assertNull(PhoneStorage(context).resolveDocument("primary:Download/WaddleAccessProbe"))
        } finally { main { browser.close() } }
    }

    @Test fun sharedOriginalSupportsBrowsingShellWithoutCopy() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("waddleStorage") == "granted")
        assertTrue("Grant all-files access on the isolated test device first", PhoneStorage(context).allowed())
        val storage = PhoneStorage(context).locations().first { it.volumeId == "primary" }
        val folder = File(storage.root, "Download/WaddleDirect-${System.nanoTime()}/中文 ' project").apply { check(mkdirs()) }
        File(folder, "input.txt").writeText("original input")
        val runtime = WaddleRuntime(context)
        val imports = File(runtime.home, "Imports").list().orEmpty().toSet()
        val browser = main { FileBrowserController(context, runtime.home) }
        try {
            main { browser.openLocal(folder) }
            await { !browser.loading && browser.current?.file == folder.canonicalFile }
            assertEquals("手机存储 · 原文件", main { browser.locationLabel() })
            assertEquals(listOf("input.txt"), main { browser.entries.map { it.name } })
            val documentId = "primary:" + folder.relativeTo(storage.root).path
            assertEquals(folder.canonicalFile, PhoneStorage(context).resolveDocument(documentId))
            val bash = runtime.command(runtime.executable("bash"), "-c", "cat input.txt > shell.txt; pwd").directory(folder).start()
            assertTrue(bash.waitFor(15, TimeUnit.SECONDS)); assertEquals(0, bash.exitValue())
            assertEquals("original input", File(folder, "shell.txt").readText())
            assertEquals("original input", File(folder, "input.txt").readText())
            assertEquals(imports, File(runtime.home, "Imports").list().orEmpty().toSet())
            assertNull(main { browser.imported })
        } finally { main { browser.close() }; folder.parentFile!!.deleteRecursively() }
    }
}
