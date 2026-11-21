package dev.waddle.android

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexController
import dev.waddle.android.codex.CodexWorkspace
import dev.waddle.android.codex.WaddleRuntime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CodexFolderIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }
    private fun await(label: String, seconds: Int = 15, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + seconds * 1000
        while (SystemClock.uptimeMillis() < end) {
            if (main(condition)) return
            SystemClock.sleep(50)
        }
        fail(label)
    }

    /** Deterministic peer: exercises the controller's real JSON-RPC pipes, not a model. */
    private class Peer(val id: String, var cwd: File) : Process() {
        private val received = java.util.Collections.synchronizedList(mutableListOf<JSONObject>())
        val requests: List<JSONObject> get() = synchronized(received) { received.toList() }
        private val bytes = LinkedBlockingQueue<Int>()
        @Volatile var closed = false
        private val source = object : InputStream() {
            override fun read(): Int = bytes.take()
            override fun read(target: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                val first = read()
                if (first < 0) return -1
                target[offset] = first.toByte()
                var count = 1
                while (count < length) {
                    val next = bytes.poll() ?: break
                    if (next < 0) { bytes.offer(-1); break }
                    target[offset + count++] = next.toByte()
                }
                return count
            }
        }
        private val sink = object : ByteArrayOutputStream() {
            @Synchronized override fun flush() {
                val text = toString("UTF-8"); reset()
                text.lineSequence().filter { it.isNotBlank() }.forEach { receive(JSONObject(it)) }
            }
            override fun close() { destroy() }
        }
        fun emit(value: JSONObject) { value.toString().plus("\n").toByteArray().forEach { bytes.put(it.toInt() and 255) } }
        private fun thread() = JSONObject().put("id", id).put("cwd", cwd.path).put("turns", JSONArray())
        private fun receive(message: JSONObject) {
            received += message
            if (!message.has("method") || !message.has("id")) return
            val params = message.optJSONObject("params") ?: JSONObject()
            val result = when (message.getString("method")) {
                "initialize" -> JSONObject().put("userAgent", "codex/0.170.0 test")
                "account/read" -> JSONObject().put("account", JSONObject().put("type", "chatgpt"))
                "thread/start" -> { cwd = File(params.getString("cwd")); JSONObject().put("thread", thread()) }
                "thread/read", "thread/resume" -> JSONObject().put("thread", thread())
                "turn/start" -> JSONObject().put("turn", JSONObject().put("id", "turn-$id"))
                else -> JSONObject()
            }
            emit(JSONObject().put("id", message.get("id")).put("result", result))
        }
        fun approval() = emit(JSONObject().put("id", "approval-$id").put("method", "item/commandExecution/requestApproval")
            .put("params", JSONObject().put("threadId", id).put("turnId", "turn-$id").put("command", "fixture command")))
        override fun getInputStream(): InputStream = source
        override fun getOutputStream(): OutputStream = sink
        override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun destroy() { if (!closed) { closed = true; bytes.offer(-1) } }
    }

    @Test fun independentConversationsUseOwnCwdAndPreservePendingApproval() {
        val root = File(context.cacheDir, "codex-folders-${System.nanoTime()}").apply { mkdirs() }
        val a = File(root, "中文 ' folder").apply { mkdir() }
        val b = File(root, "other workspace").apply { mkdir() }
        val pa = Peer("a", a); val pb = Peer("b", b)
        val keys = listOf("test_codex_a_${System.nanoTime()}", "test_codex_b_${System.nanoTime()}")
        val ca = main { CodexController(context, a, keys[0], startProcess = { pa }) }
        val cb = main { CodexController(context, b, keys[1], startProcess = { pb }) }
        try {
            main { ca.connect(); cb.connect() }
            await("Peers not ready") { ca.signedIn && cb.signedIn }
            main { ca.send("task A"); cb.send("task B") }
            await("Turns not started") { ca.status == "Codex 正在处理…" && cb.status == "Codex 正在处理…" }
            pa.approval()
            await("Approval missing") { ca.approvals.size == 1 }
            main {
                assertTrue(cb.approvals.isEmpty()); assertTrue(ca.busy && cb.busy)
                assertEquals("task A", ca.messages.first().text); assertEquals("task B", cb.messages.first().text)
                cb.disconnect()
                assertTrue(ca.busy); assertEquals(1, ca.approvals.size); assertFalse(pa.closed)
                ca.answerApproval(ca.approvals.first(), false)
            }
            for ((peer, folder) in listOf(pa to a, pb to b)) {
                for (method in listOf("thread/start", "turn/start")) {
                    val sent = peer.requests.single { it.optString("method") == method }.getJSONObject("params")
                    assertEquals(folder.canonicalPath, sent.getString("cwd"))
                }
            }
        } finally {
            main { ca.disconnect(); cb.disconnect() }
            keys.forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
            root.deleteRecursively()
        }
    }

    @Test fun resumeUsesOriginalDirectoryAndMissingDirectoryNeverFallsBack() {
        val root = File(context.cacheDir, "codex-resume-${System.nanoTime()}").apply { mkdirs() }
        val original = File(root, "original 中文").apply { mkdir() }
        val requested = File(root, "different").apply { mkdir() }
        val key = "test_codex_resume_${System.nanoTime()}"
        val prefs = context.getSharedPreferences(key, 0)
        prefs.edit().putString("lastThread", "saved").commit()
        val peer = Peer("saved", original)
        val client = main { CodexController(context, requested, key, startProcess = { peer }) }
        try {
            main { client.connect() }
            await("Resume failed") { client.threadId == "saved" && !client.busy }
            assertEquals(original.canonicalFile, main { client.workingDirectory })
            assertEquals(original.canonicalPath, peer.requests.single { it.optString("method") == "thread/resume" }.getJSONObject("params").getString("cwd"))
            original.delete()
            main { client.send("must not be sent") }
            assertTrue(main { client.error.orEmpty().contains("目录不存在") })
            assertFalse(peer.requests.any { it.optString("method") == "turn/start" })
            main { client.resume() }
            await("Missing resume directory not reported") { !client.busy && client.error.orEmpty().contains("无法恢复会话") }
            assertEquals(1, peer.requests.count { it.optString("method") == "thread/resume" })
            assertEquals(original.canonicalFile, main { client.workingDirectory })
        } finally { main { client.disconnect() }; prefs.edit().clear().commit(); root.deleteRecursively() }
    }

    @Test fun workspaceRejectsInvalidDirectoriesAndCapsSessionsWithoutReplacingExistingOnes() {
        val workspace = main { CodexWorkspace(context) }
        val initial = main { workspace.sessions.toList() }
        val root = File(context.cacheDir, "codex-limit-${System.nanoTime()}").apply { mkdirs() }
        try {
            main {
                val old = workspace.selected
                assertTrue(runCatching { workspace.create(File(root, "missing")) }.isFailure)
                assertSame(old, workspace.selected)
                assertTrue(runCatching { workspace.create(File("relative")) }.isFailure)
                while (workspace.sessions.size < CodexWorkspace.MAX_SESSIONS) workspace.create(root)
                val selected = workspace.selected
                assertTrue(runCatching { workspace.create(root) }.exceptionOrNull()?.message.orEmpty().contains("4 个"))
                assertSame(selected, workspace.selected)
                val restored = CodexWorkspace(context)
                assertEquals(workspace.sessions.map { it.id }, restored.sessions.map { it.id })
                assertEquals(root.canonicalFile, restored.selected!!.client.workingDirectory)
                restored.disconnectAll()
            }
        } finally {
            main { workspace.sessions.filter { it !in initial }.toList().forEach(workspace::close); initial.firstOrNull()?.let(workspace::select) }
            root.deleteRecursively()
        }
    }

    @Test fun realBundledServerStartsInFolderAndExecutesRelativeFileOperationsWithoutLogin() {
        val runtime = WaddleRuntime(context)
        val folder = File(runtime.home, "codex actual ' 中文 ${System.nanoTime()}").apply { mkdirs() }
        File(folder, "existing.txt").writeText("read me")
        val process = runtime.start(workingDirectory = folder)
        Thread { runCatching { process.errorStream.copyTo(object : OutputStream() { override fun write(value: Int) {} }) } }.start()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val reader = process.inputStream.bufferedReader()
        val writer = process.outputStream.bufferedWriter()
        var id = 0
        fun call(method: String, params: JSONObject): JSONObject {
            val requestId = ++id
            writer.write(JSONObject().put("id", requestId).put("method", method).put("params", params).toString() + "\n"); writer.flush()
            return executor.submit<JSONObject> {
                var message: JSONObject
                do { message = JSONObject(reader.readLine()) } while (message.optInt("id") != requestId)
                check(!message.has("error")) { message.toString() }
                message.getJSONObject("result")
            }.get(30, TimeUnit.SECONDS)
        }
        try {
            call("initialize", JSONObject().put("clientInfo", JSONObject().put("name", "waddle_folder_test").put("version", "0.4.0")))
            writer.write("{\"method\":\"initialized\"}\n"); writer.flush()
            val thread = call("thread/start", JSONObject().put("cwd", folder.canonicalPath).put("approvalPolicy", "on-request").put("sandbox", "workspace-write")).getJSONObject("thread")
            assertEquals(folder.canonicalFile, File(thread.getString("cwd")).canonicalFile)
            val result = call("command/exec", JSONObject().put("command", JSONArray().put(runtime.executable("bash")).put("-c")
                .put("pwd; cat existing.txt; printf created-here > result.txt"))
                // No cwd override here: verify the actual server process's initial cwd as well.
                .put("timeoutMs", 10000).put("sandboxPolicy", JSONObject().put("type", "workspaceWrite").put("writableRoots", JSONArray().put(folder.path))))
            assertEquals(result.toString(), 0, result.getInt("exitCode"))
            assertEquals(folder.canonicalFile, File(result.getString("stdout").lineSequence().first()).canonicalFile)
            assertTrue(result.getString("stdout").contains("read me"))
            assertEquals("created-here", File(folder, "result.txt").readText())
        } finally { runtime.stop(); executor.shutdownNow(); folder.deleteRecursively() }
    }

    /** Real model check is opt-in and uses existing login only. */
    @Test fun liveModelWritesInSelectedFolder() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("waddleLiveFolder") == "true")
        val folder = File(context.filesDir, "home/live-folder-${System.nanoTime()} 中文 ' project").apply { mkdirs() }
        val key = "test_codex_live_folder_${System.nanoTime()}"
        val client = main { CodexController(context, folder, key) }
        try {
            main { client.connect() }
            await("Not signed in", 40) { client.signedIn }
            main { client.send("Run pwd, then write exactly WADDLE_FOLDER_OK to the relative file result.txt in your current working directory. Read it back and report the absolute path. Do not access other folders.") }
            await("Model did not finish", 180) { !client.busy }
            main { assertNull(client.error) }
            assertEquals("WADDLE_FOLDER_OK", File(folder, "result.txt").readText().trim())
        } finally { main { client.disconnect() }; context.getSharedPreferences(key, 0).edit().clear().commit(); folder.deleteRecursively() }
    }
}
