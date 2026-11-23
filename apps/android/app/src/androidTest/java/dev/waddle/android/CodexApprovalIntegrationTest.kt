package dev.waddle.android

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexController
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
class CodexApprovalIntegrationTest {
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
        var historyPages = emptyMap<String, JSONObject>()
        var failHistory = false
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
        private fun thread() = JSONObject().put("id", id).put("cwd", cwd.path).put("turns", JSONArray().put(
            JSONObject().put("items", JSONArray().put(JSONObject().put("id", "saved-message").put("type", "agentMessage").put("text", "saved history message")))))
        private fun receive(message: JSONObject) {
            received += message
            if (!message.has("method") || !message.has("id")) return
            val params = message.optJSONObject("params") ?: JSONObject()
            if (message.getString("method") == "thread/list" && failHistory) {
                emit(JSONObject().put("id", message.get("id")).put("error", JSONObject().put("message", "fixture history failure")))
                return
            }
            val result = when (message.getString("method")) {
                "thread/list" -> historyPages.getValue(params.optString("cursor"))
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
            await("Decline was not sent") { pa.requests.any { it.optString("id") == "approval-a" && it.optJSONObject("result")?.optString("decision") == "decline" } }
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

    @Test fun historyPagesIncludeOtherDirectoriesAndErrorsDoNotInterruptTurns() {
        val root = File(context.cacheDir, "history-pages-${System.nanoTime()}").apply { mkdirs() }
        val peer = Peer("current", root)
        fun entry(id: String, title: Any) = JSONObject().put("id", id).put("name", title)
            .put("preview", "first message $id").put("cwd", "/another/$id").put("updatedAt", 1234)
        peer.historyPages = mapOf(
            "" to JSONObject().put("data", JSONArray().put(entry("a", JSONObject.NULL))).put("nextCursor", "page-two"),
            "page-two" to JSONObject().put("data", JSONArray().put(entry("a", "duplicate")).put(entry("b", "Named session"))).put("nextCursor", JSONObject.NULL))
        val namespace = "test_history_pages_${System.nanoTime()}"
        val client = main { CodexController(context, root, namespace, startProcess = { peer }) }
        try {
            main { client.connect() }; await("Peer not ready") { client.signedIn }
            main { client.loadHistory() }; await("First page missing") { !client.historyLoading }
            assertEquals("first message a", main { client.history.single().title })
            main { client.loadHistory(more = true) }; await("Next page missing") { !client.historyLoading }
            assertEquals(listOf("a", "b"), main { client.history.map { it.id } })
            assertNull(main { client.historyCursor })
            val requests = peer.requests.filter { it.optString("method") == "thread/list" }.map { it.getJSONObject("params") }
            assertFalse(requests.first().has("cwd")); assertEquals("updated_at", requests.first().getString("sortKey"))
            assertEquals("page-two", requests.last().getString("cursor"))
            main { client.send("keep running") }; await("Turn missing") { client.status == "Codex 正在处理…" }
            peer.approval(); await("Approval missing") { client.approvals.size == 1 }
            peer.failHistory = true
            main { client.loadHistory() }; await("Error missing") { client.historyError != null }
            assertTrue(main { client.busy }); assertEquals(1, main { client.approvals.size }); assertNull(main { client.error })
        } finally { main { client.disconnect() }; context.getSharedPreferences(namespace, 0).edit().clear().commit(); root.deleteRecursively() }
    }

    @Test fun missingHistoryDirectoryNeverFallsBackAndRetryRestoresOriginalCwd() {
        val root = File(context.cacheDir, "history-cwd-${System.nanoTime()}").apply { mkdirs() }
        val original = File(root, "original 中文 folder")
        val peer = Peer("old-session", original)
        val namespace = "test_history_cwd_${System.nanoTime()}"
        val client = main { CodexController(context, root, namespace, startProcess = { peer }, initialThreadId = "old-session") }
        try {
            main { client.connect() }; await("Missing directory was not rejected") { client.error != null && !client.busy }
            assertEquals(root.canonicalFile, main { client.workingDirectory })
            assertNull(main { client.threadId }); assertTrue(peer.requests.none { it.optString("method") == "thread/resume" })
            original.mkdir()
            main { client.resume() }; await("Resume failed") { client.threadId == "old-session" && !client.busy }
            assertEquals(original.canonicalFile, main { client.workingDirectory })
            assertEquals("saved history message", main { client.messages.single().text })
            main { client.send("continue") }; await("Turn missing") { client.status == "Codex 正在处理…" }
            for (method in listOf("thread/resume", "turn/start")) {
                assertEquals(original.canonicalPath, peer.requests.last { it.optString("method") == method }.getJSONObject("params").getString("cwd"))
            }
        } finally { main { client.disconnect() }; context.getSharedPreferences(namespace, 0).edit().clear().commit(); root.deleteRecursively() }
    }

}
