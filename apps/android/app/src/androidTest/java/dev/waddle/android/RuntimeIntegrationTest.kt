package dev.waddle.android
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.terminal.WaddleRuntime
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
class RuntimeIntegrationTest {
    @Test fun bundledToolsRunAsWaddleInTheSharedHome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = WaddleRuntime(context)
        runtime.prepare()
        assertEquals(File(context.filesDir, "home"), runtime.home)
        val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.any { it.startsWith("com.termux.") })
        fun run(vararg command: String): String {
            val process = runtime.command(*command).redirectErrorStream(true).start()
            assertTrue("Process timed out", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            return output
        }
        assertEquals(android.os.Process.myUid().toString(), run("/system/bin/id", "-u").trim())
        if (BuildConfig.BUNDLED_AGENTS) assertTrue(run(runtime.executable("codex"), "--version").contains("0.170.0"))
        else for (name in listOf("codex", "claude", "agy")) {
            val absent = runtime.command(runtime.executable("bash"), "-c", "$name --version").redirectErrorStream(true).start()
            assertTrue(absent.waitFor(10, TimeUnit.SECONDS)); assertEquals(127, absent.exitValue())
            assertTrue(absent.inputStream.bufferedReader().readText().contains("command not found"))
        }
        assertTrue(run(runtime.executable("bash"), "--version").contains("GNU bash"))
        assertTrue(run(runtime.executable("git"), "--version").contains("git version"))
        assertTrue(run(runtime.executable("rg"), "--version").contains("ripgrep"))
        assertTrue(run(runtime.executable("curl"), "--version").contains("curl"))
        if (InstrumentationRegistry.getArguments().getString("waddleNetwork") == "true") {
            assertTrue(run(runtime.executable("curl"), "--fail", "--head", "--max-time", "20", "https://example.com").contains("HTTP/"))
            assertTrue(run(runtime.executable("git"), "ls-remote", "https://github.com/wu-zhonghua/Waddle.git", "HEAD").contains("HEAD"))
        }
        val fixture = File(runtime.home, "embedded-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(fixture, "message.txt").writeText("WADDLE_EMBEDDED_OK")
            assertTrue(run(runtime.executable("rg"), "WADDLE_EMBEDDED_OK", fixture.path).contains("message.txt"))
            run(runtime.executable("git"), "init", fixture.path)
            run(runtime.executable("git"), "-C", fixture.path, "add", "message.txt")
            assertTrue(run(runtime.executable("git"), "-C", fixture.path, "status", "--porcelain").contains("message.txt"))
        } finally { fixture.deleteRecursively() }
    }

}
