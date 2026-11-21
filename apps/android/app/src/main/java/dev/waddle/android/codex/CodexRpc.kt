package dev.waddle.android.codex

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors

/** JSON-RPC over private child-process pipes. No listener, token, or companion app. */
class CodexRpc(
    private val process: Process,
    private val onReady: () -> Unit,
    private val onMessage: (JSONObject) -> Unit,
    private val onDisconnected: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()
    private val output = process.outputStream.bufferedWriter()
    private val pending = mutableMapOf<Int, (Result<JSONObject>) -> Unit>()
    private var sequence = 0
    @Volatile private var stopped = false

    fun connect() {
        Thread { runCatching { process.errorStream.use { input -> val buffer = ByteArray(4096); while (input.read(buffer) != -1) { /* drain without logging secrets */ } } } }.start()
        Thread {
            try {
                process.inputStream.bufferedReader().use { input ->
                    val line = StringBuilder()
                    val buffer = CharArray(8192)
                    while (!stopped) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        for (i in 0 until count) {
                            if (buffer[i] == '\n') {
                                val text = line.toString(); line.setLength(0)
                                if (text.isNotBlank()) handler.post { if (!stopped) receive(text) }
                            } else {
                                line.append(buffer[i])
                                check(line.length <= 4 * 1024 * 1024) { "Runtime response too large" }
                            }
                        }
                    }
                }
                if (!stopped) handler.post { fail("本机 Codex 已退出。可重新连接并恢复会话。") }
            } catch (_: Exception) {
                if (!stopped) handler.post { fail("本机 Codex 通信失败，请重新连接。") }
            }
        }.start()
        onReady()
    }

    private fun receive(text: String) {
        val message = runCatching { JSONObject(text) }.getOrElse { fail("Codex 返回了无效数据。"); return }
        if (message.has("id") && !message.has("method")) {
            val callback = pending.remove(message.optInt("id", -1)) ?: return
            val error = message.optJSONObject("error")
            callback(if (error != null) Result.failure(IllegalStateException(error.optString("message", "请求失败")))
                else Result.success(message.optJSONObject("result") ?: JSONObject()))
        } else onMessage(message)
    }

    private fun send(value: JSONObject) {
        if (stopped) return
        val line = value.toString()
        writer.execute {
            try { output.write(line); output.newLine(); output.flush() }
            catch (_: Exception) { handler.post { fail("无法向本机 Codex 发送请求。") } }
        }
    }

    fun request(method: String, params: JSONObject = JSONObject(), callback: (Result<JSONObject>) -> Unit) {
        if (stopped) { callback(Result.failure(IllegalStateException("尚未连接"))); return }
        val id = ++sequence
        pending[id] = callback
        send(JSONObject().put("id", id).put("method", method).put("params", params))
        handler.postDelayed({ pending.remove(id)?.invoke(Result.failure(IllegalStateException("请求超时：$method"))) }, 45_000)
    }
    fun notify(method: String) = send(JSONObject().put("method", method).put("params", JSONObject()))
    fun reply(id: Any, result: JSONObject) = send(JSONObject().put("id", id).put("result", result))
    fun rejectUnknown(id: Any) = send(JSONObject().put("id", id).put("error", JSONObject().put("code", -32601).put("message", "Unsupported client request")))
    private fun fail(message: String) { if (stopped) return; close(); onDisconnected(message) }
    fun close() {
        if (stopped) return
        stopped = true
        pending.clear(); handler.removeCallbacksAndMessages(null)
        writer.shutdownNow()
        runCatching { process.outputStream.close() }
    }
}
