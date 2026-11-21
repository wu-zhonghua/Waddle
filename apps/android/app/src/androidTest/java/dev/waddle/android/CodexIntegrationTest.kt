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
import dev.waddle.android.codex.NativeSettingsTool
import dev.waddle.android.codex.WaddleRuntime
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CodexIntegrationTest {
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

    @Test fun settingsToolOnlyAcceptsTheClosedReadOnlySchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tool = NativeSettingsTool(context)
        val result = tool.read(JSONObject().put("keys", JSONArray(NativeSettingsTool.KEYS)))
        assertTrue(result.getBoolean("readOnly"))
        assertEquals(NativeSettingsTool.KEYS.toSet(), result.getJSONObject("values").keys().asSequence().toSet())
        val invalid = listOf(
            "{}", "{\"keys\":[]}", "{\"keys\":[\"android_id\"]}",
            "{\"keys\":[\"brightness\"],\"value\":255}",
            "{\"keys\":[\"brightness\",\"brightness\"]}", "{\"keys\":[42]}",
            "{\"keys\":\"brightness\"}", "{\"keys\":[\"/data/system\"]}",
        )
        for (input in invalid) assertTrue("Accepted invalid tool arguments: $input", runCatching { tool.read(JSONObject(input)) }.isFailure)
    }

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
        assertTrue(run(runtime.executable("codex"), "--version").contains("0.170.0"))
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

    @Test fun embeddedAppServerInitializesWithoutCompanionAppOrLogin() {
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

    /** Opt-in: requires a signed-in Waddle test environment. Never runs in CI by default. */
    @Test fun liveWaddleConnectsAuthenticatesAndCallsNativeSettings() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("waddleLiveCodex") == "true")
        fun grant(permission: String) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant dev.waddle.android $permission")).use { it.readBytes() }
        }
        if (Build.VERSION.SDK_INT >= 33) grant("android.permission.POST_NOTIFICATIONS")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            await("Activity failed to bind") { activity.codex != null }
            lateinit var client: CodexController
            main {
                client = activity.codex!!
                client.connect()
            }
            try {
                await("Local Codex connection or saved login failed", 50) { client.signedIn || client.error != null }
                main { assertNull(client.error); assertTrue(client.connected); assertTrue(client.signedIn) }
                main { client.send("请只调用 waddle_read_system_settings 工具读取 brightness、brightness_mode、screen_timeout_ms、airplane_mode、accessibility_enabled，随后用中文报告工具结果。不要执行命令或改文件。") }
                await("Real Codex turn did not complete", 150) { !client.busy }
                main {
                    assertNull(client.error)
                    assertTrue("Codex never invoked Waddle's native tool", client.nativeToolCalls > 0)
                    assertTrue(client.messages.any { it.role == "Waddle 原生工具" && it.text.contains("brightness") && it.text.contains("Waddle Android settings API") })
                    assertTrue(client.messages.any { it.role == "Codex" && it.text.isNotBlank() })
                    val actual = JSONObject(client.messages.last { it.role == "Waddle 原生工具" }.text).getJSONObject("values")
                    val expected = NativeSettingsTool(activity).read(JSONObject().put("keys", JSONArray(NativeSettingsTool.KEYS))).getJSONObject("values")
                    for (key in NativeSettingsTool.KEYS) assertEquals(key, expected.getLong(key), actual.getLong(key))
                }
                val fixture = File(client.runtime.home, "model-probe-${System.nanoTime()}").apply { mkdirs() }
                try {
                    File(fixture, "input.txt").writeText("WADDLE_SHARED_OK")
                    File(fixture, "result.txt").writeText("REPLACE_THIS_EXISTING_FILE")
                    File(fixture, "cli-result.txt").writeText("REPLACE_THIS_FROM_TERMINAL")
                    main { client.send("请在 ${fixture.path} 内完成这个实际文件任务：读取 input.txt，用文件工具或 shell 修改已有 result.txt，使内容与 input.txt 相同，然后运行 bash 命令 cat result.txt、pwd 和 id -u 验证结果。只能操作这个测试目录，不访问认证文件。请实际执行，不要只给建议。") }
                    await("Model file/command task did not finish", 180) { !client.busy }
                    main { assertNull(client.error) }
                    assertTrue("Model did not create its output: " + client.messages.lastOrNull()?.text, File(fixture, "result.txt").isFile)
                    assertEquals("WADDLE_SHARED_OK", File(fixture, "result.txt").readText().trim())
                    lateinit var shell: dev.waddle.android.terminal.ShellSession
                    main {
                        shell = activity.workspace!!.create()
                        shell.terminal.write("cat '${fixture.path}/result.txt'; pwd; codex --version\r")
                    }
                    await("Terminal cannot see Codex output or embedded CLI") {
                        val text = shell.terminal.emulator.screen.transcriptText
                        text.contains("WADDLE_SHARED_OK") && text.contains("0.170.0") && text.contains("/dev.waddle.android/files/home")
                    }
                    main {
                        shell.terminal.write("codex --ask-for-approval on-request exec --sandbox workspace-write --skip-git-repo-check -C '${fixture.path}' 'Read input.txt, modify existing cli-result.txt to the same content, then execute cmp input.txt cli-result.txt. Work only in this directory. Do not read credentials.' </dev/null > '${fixture.path}/cli-output.txt' 2>&1; printf '__CLI_EXIT_%s__\\n' \"\$?\"\r")
                    }
                    await("Terminal Codex did not reuse the login and finish", 180) {
                        shell.terminal.emulator.screen.transcriptText.contains("__CLI_EXIT_0__")
                    }
                    assertEquals(File(fixture, "cli-output.txt").readText().takeLast(6_000), "WADDLE_SHARED_OK", File(fixture, "cli-result.txt").readText().trim())
                    main { activity.workspace!!.close(shell) }
                } finally { fixture.deleteRecursively() }
                val thread = client.threadId
                scenario.recreate()
                scenario.onActivity { activity = it }
                await("Activity recreation lost the controller") { activity.codex === client }
                main { assertEquals(thread, activity.codex!!.threadId); client.disconnect(); client.connect() }
                await("Reconnect failed", 50) { client.signedIn || client.error != null }
                main { assertNull(client.error); client.resume() }
                await("Resume did not finish", 45) { !client.busy }
                main { assertNull(client.error); assertEquals(thread, client.threadId); assertTrue(client.messages.any { it.role == "Codex" }) }
            } finally { main { client.disconnect() } }
        }
    }
}
