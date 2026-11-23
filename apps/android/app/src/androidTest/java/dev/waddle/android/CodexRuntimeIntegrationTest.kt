package dev.waddle.android

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexController
import dev.waddle.android.terminal.WaddleRuntime
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CodexRuntimeIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(message: String, seconds: Int = 40, predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + seconds * 1000
        while (SystemClock.uptimeMillis() < until) {
            var ready = false
            main { ready = predicate() }
            if (ready) return
            SystemClock.sleep(100)
        }
        fail(message)
    }

    @Test fun embeddedAppServerInitializesWithoutCompanionAppOrLogin() {
        assumeTrue(BuildConfig.BUNDLED_AGENTS)
        val runtime = WaddleRuntime(ApplicationProvider.getApplicationContext())
        val process = runtime.start()
        Thread { runCatching { process.errorStream.use { it.copyTo(object : java.io.OutputStream() { override fun write(value: Int) {} }) } } }.start()
        val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
        val input = process.inputStream.bufferedReader()
        val fixture = File(runtime.home, "rpc-probe-${System.nanoTime()}.txt")
        try {
            val writer = process.outputStream.bufferedWriter()
            writer.write("{\"id\":1,\"method\":\"initialize\",\"params\":{\"clientInfo\":{\"name\":\"waddle_test\",\"version\":\"0.3.0\"}}}\n")
            writer.flush()
            val line = reader.submit<String> { input.readLine() }.get(20, TimeUnit.SECONDS)
            val response = JSONObject(line)
            assertEquals(1, response.getInt("id"))
            assertTrue(response.getJSONObject("result").getString("userAgent").contains("0.170.0"))
            writer.write("{\"method\":\"initialized\"}\n")
            val command = JSONArray().put(runtime.executable("bash")).put("-c")
                .put("printf WADDLE_RPC_OK > '${fixture.path}'; id -u; pwd")
            writer.write(JSONObject().put("id", 2).put("method", "command/exec").put("params", JSONObject()
                .put("command", command).put("cwd", runtime.home.path).put("timeoutMs", 10000)
                .put("sandboxPolicy", JSONObject().put("type", "workspaceWrite").put("writableRoots", JSONArray().put(runtime.home.path))))
                .toString() + "\n")
            writer.flush()
            val result = reader.submit<JSONObject> {
                var message: JSONObject
                do { message = JSONObject(input.readLine()) } while (message.optInt("id") != 2)
                message.getJSONObject("result")
            }.get(20, TimeUnit.SECONDS)
            assertEquals(result.toString(), 0, result.getInt("exitCode"))
            assertTrue(result.getString("stdout").contains(android.os.Process.myUid().toString()))
            assertEquals(runtime.home.canonicalFile, File(result.getString("stdout").trim().lines().last()).canonicalFile)
            assertEquals("WADDLE_RPC_OK", fixture.readText())
        } finally { runtime.stop(); reader.shutdownNow(); fixture.delete() }
        assertTrue("Supervisor did not exit", process.waitFor(8, TimeUnit.SECONDS))
    }

    @Test fun supervisorStopsShellsThatCreatedTheirOwnSession() {
        assumeTrue(BuildConfig.BUNDLED_AGENTS)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = WaddleRuntime(context)
        val fixture = File(runtime.home, "supervisor-probe-${System.nanoTime()}").apply { mkdirs() }
        val script = File(fixture, "detached.sh").apply {
            writeText("printf started > '${fixture.path}/started'\nsleep 5\nprintf UNEXPECTED > '${fixture.path}/finished'\n")
        }
        val process = runtime.command(File(context.applicationInfo.nativeLibraryDir, "libwaddle_supervisor.so").path,
            runtime.executable("bash"), "-c", "/system/bin/setsid '${runtime.executable("bash")}' '${script.path}' & wait").start()
        try {
            await("Detached shell did not start", 10) { File(fixture, "started").exists() }
            process.destroy()
            assertTrue("Supervisor failed to reap detached shell", process.waitFor(8, TimeUnit.SECONDS))
            SystemClock.sleep(5_500)
            assertFalse("Detached shell survived shutdown", File(fixture, "finished").exists())
        } finally { process.destroy(); fixture.deleteRecursively() }
    }

}
