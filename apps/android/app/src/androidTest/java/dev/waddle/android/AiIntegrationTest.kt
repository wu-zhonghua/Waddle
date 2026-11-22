package dev.waddle.android

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.ai.*
import dev.waddle.android.terminal.TerminalContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AiIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }
    private fun await(predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            if (main(predicate)) return
            SystemClock.sleep(30)
        }
        fail("AI operation did not finish")
    }
    private fun completion(content: String, finish: String = "stop") = JSONObject().put("choices", JSONArray().put(
        JSONObject().put("finish_reason", finish).put("message", AiClient.message("assistant", content)))).toString()
    private class Peer(url: URL, val status: Int, val body: String, val gate: CountDownLatch? = null) : HttpURLConnection(url) {
        val sent = ByteArrayOutputStream()
        val entered = CountDownLatch(1)
        @Volatile var closed = false
        override fun connect() {}
        override fun disconnect() { closed = true; gate?.countDown() }
        override fun usingProxy() = false
        override fun getOutputStream() = sent
        override fun getResponseCode(): Int { entered.countDown(); gate?.await(10, TimeUnit.SECONDS); return status }
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream() = inputStream
    }
    private fun withStore(block: (AiSettings, String) -> Unit) {
        val namespace = "ai_test_${System.nanoTime()}"
        try { block(AiSettings(context, namespace), namespace) }
        finally {
            context.getSharedPreferences(namespace, 0).edit().clear().commit()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("waddle.$namespace") }
        }
    }

    @Test fun keysEncryptedProviderScopedDeletedAndNeverRetargetedSilently() = withStore { settings, namespace ->
        assertFalse(settings.configured())
        settings.save(AiProvider.DEEPSEEK, AiProvider.DEEPSEEK.endpoint, "deepseek-flash", "fixture-secret-not-real")
        assertTrue(settings.configured())
        assertFalse(context.getSharedPreferences(namespace, 0).all.values.any { it.toString().contains("fixture-secret-not-real") })
        val restored = AiSettings(context, namespace)
        assertEquals("fixture-secret-not-real", restored.credentials().key)
        assertFalse(restored.profile(AiProvider.CUSTOM).hasKey)
        assertTrue(runCatching { restored.credentials(endpoint = "https://another.example/v1") }.isFailure)
        restored.save(AiProvider.DEEPSEEK, AiProvider.DEEPSEEK.endpoint, "deepseek-v4-pro", "")
        assertEquals("deepseek-v4-pro", restored.credentials().model)
        restored.deleteKey(AiProvider.DEEPSEEK)
        assertFalse(restored.configured()); assertTrue(runCatching { restored.credentials() }.isFailure)
    }

    @Test fun compatibilityRequestAndMultilineResponseUseOnlyMessages() {
        val code = "mkdir -p '中文 folder'\ncd '中文 folder'\nprintf '%s\\n' hello"
        val reply = JSONObject().put("command", code).put("explanation", "创建目录后进入。").toString()
        lateinit var peer: Peer
        val client = AiClient { Peer(it, 200, completion("```json\n$reply\n```")).also { peer = it } }
        val credentials = AiCredentials("https://compatible.example/v1/", "my-model", "not-a-real-key")
        val result = AiClient.parseSuggestion(client.complete(credentials, JSONArray().put(AiClient.message("user", "上下文"))))
        assertEquals("https://compatible.example/v1/chat/completions", peer.url.toString())
        assertFalse(peer.instanceFollowRedirects)
        assertEquals("Bearer not-a-real-key", peer.getRequestProperty("Authorization"))
        val request = JSONObject(peer.sent.toString("UTF-8"))
        assertEquals("my-model", request.getString("model")); assertFalse(request.has("tools"))
        assertFalse(request.getBoolean("stream")); assertEquals(code, result.command)
        assertEquals("https://compatible.example/v1/chat/completions", completionUrl("https://compatible.example/v1/chat/completions/"))
        listOf("http://example.com", "https://user:secret@example.com", "https://example.com?key=secret", "https://example.com#secret").forEach {
            assertTrue(runCatching { completionUrl(it) }.isFailure)
        }
    }

    @Test fun authenticationQuotaNetworkRedirectAndMalformedRepliesAreActionable() {
        val credentials = AiCredentials("https://example.com", "model", "not-real")
        listOf(401 to "Key", 402 to "余额", 403 to "权限", 429 to "频繁", 500 to "暂时", 302 to "重定向").forEach { (status, expected) ->
            val client = AiClient { Peer(it, status, "must-not-echo-secret") }
            val error = runCatching { client.complete(credentials, JSONArray()) }.exceptionOrNull()
            assertTrue(error is AiFailure); assertTrue(error!!.message!!.contains(expected)); assertFalse(error.message!!.contains("must-not-echo-secret"))
        }
        assertTrue(AiClient.httpError(429, "insufficient_quota").contains("额度不足"))
        val offline = AiClient { throw java.net.UnknownHostException("must-not-echo") }
        assertTrue(runCatching { offline.complete(credentials, JSONArray()) }.exceptionOrNull()!!.message!!.contains("网络"))
        val timeout = AiClient { throw java.net.SocketTimeoutException() }
        assertTrue(runCatching { timeout.complete(credentials, JSONArray()) }.exceptionOrNull()!!.message!!.contains("超时"))
        assertTrue(runCatching { AiClient.parseCompletion(completion("partial", "length")) }.isFailure)
        assertTrue(runCatching { AiClient.parseSuggestion("rm -rf anything") }.isFailure)
        assertTrue(runCatching { AiClient.parseSuggestion(JSONObject().put("command", "a".repeat(65_537)).put("explanation", "long").toString()) }.isFailure)
        assertTrue(runCatching { AiClient.parseSuggestion(JSONObject().put("command", "\u0003").put("explanation", "control").toString()) }.isFailure)
        assertEquals("", AiClient.parseSuggestion("{\"command\":\"\",\"explanation\":\"请先确认路径\"}").command)
    }

    @Test fun cancellationDropsLateRepliesAndNewRequestKeepsItsOwnContext() = withStore { settings, _ ->
        settings.save(AiProvider.CUSTOM, "https://compatible.example/v1", "test-model", "not-real")
        val gate = CountDownLatch(1)
        val peers = mutableListOf<Peer>()
        var count = 0
        val client = main { CommandSuggester(context, settings) {
            val first = count++ == 0
            AiClient { url -> Peer(url, 200, completion(if (first) "{\"command\":\"touch stale\",\"explanation\":\"late\"}" else
                "{\"command\":\"pwd\",\"explanation\":\"当前路径\"}"), if (first) gate else null).also { synchronized(peers) { peers += it } } }
        } }
        val snapshot = TerminalContext("session-original", "Shell 1", "/test/中文", 5, "ls missing\nNo such file", 2)
        main { client.generate(snapshot, "解释报错") }
        await { synchronized(peers) { peers.isNotEmpty() } }
        assertTrue(peers[0].entered.await(10, TimeUnit.SECONDS))
        main { client.cancel(); client.generate(snapshot.copy(sessionId = "session-next", cwd = "/new"), "显示路径") }
        await { !client.busy }
        assertEquals("pwd", main { client.command }); assertEquals("session-next", main { client.source?.sessionId })
        assertTrue(peers[0].closed)
        val payload = JSONObject(peers[0].sent.toString("UTF-8")).getJSONArray("messages").getJSONObject(1).getString("content")
        assertEquals(2, JSONObject(payload).getInt("last_exit_code"))
        assertTrue(JSONObject(payload).getString("recent_terminal_text").contains("ls missing")); assertEquals("/test/中文", JSONObject(payload).getString("cwd"))
        main { client.cancel() }
    }
}
