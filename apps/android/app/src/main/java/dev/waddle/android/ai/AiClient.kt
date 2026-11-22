package dev.waddle.android.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class AiFailure(message: String) : Exception(message)
data class AiSuggestion(val command: String, val explanation: String)

/** One cancellable request. No tools, redirects, retries, logging, or shell access. */
class AiClient(private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null
    fun cancel() {
        cancelled.set(true)
        val active = connection
        Thread { active?.disconnect() }.apply { isDaemon = true; start() }
    }
    fun complete(credentials: AiCredentials, messages: JSONArray): String {
        if (cancelled.get()) throw AiFailure("已取消。")
        val timedOut = AtomicBoolean(false)
        val deadline = java.util.Timer(true).apply {
            schedule(object : java.util.TimerTask() {
                override fun run() { timedOut.set(true); this@AiClient.cancel() }
            }, 120_000)
        }
        try {
            val conn = open(URL(completionUrl(credentials.endpoint)))
            connection = conn
            try {
                if (cancelled.get()) throw AiFailure("已取消。")
                conn.connectTimeout = 15_000; conn.readTimeout = 90_000
                conn.instanceFollowRedirects = false
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer ${credentials.key}")
                val payload = JSONObject().put("model", credentials.model).put("stream", false).put("messages", messages).toString().toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(payload.size)
                conn.outputStream.use { it.write(payload) }
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val bytes = stream?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (cancelled.get()) throw AiFailure("已取消。")
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > 1_048_576) throw AiFailure("模型回复过长，请缩小任务。")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: byteArrayOf()
                val body = String(bytes, Charsets.UTF_8)
                if (status !in 200..299) throw AiFailure(httpError(status, body))
                if (cancelled.get()) throw AiFailure("已取消。")
                return parseCompletion(body)
            } finally { connection = null; conn.disconnect() }
        } catch (e: AiFailure) { if (timedOut.get()) throw AiFailure("回复超时，请稍后重试。"); throw e
        } catch (_: SocketTimeoutException) { throw AiFailure("连接或回复超时，请检查网络后重试。")
        } catch (_: IOException) { if (timedOut.get()) throw AiFailure("回复超时，请稍后重试。"); throw AiFailure("网络不通或 TLS 连接失败，请检查网络和接口地址。")
        } catch (_: Exception) { throw AiFailure("服务返回的格式不兼容，请检查接口地址和模型。")
        } finally { deadline.cancel() }
    }
    companion object {
        fun message(role: String, text: String) = JSONObject().put("role", role).put("content", text)
        fun httpError(status: Int, body: String): String {
            val detail = body.lowercase()
            // Never display server-supplied error text: it may echo Authorization or a prompt.
            return when {
                status == 401 -> "API Key 不正确或已失效，请在设置中更换。"
                status == 402 || listOf("insufficient_quota", "insufficient_balance", "balance", "quota_exceeded", "arrearage", "余额", "欠费", "1113").any { it in detail } -> "余额或额度不足，请到服务商平台检查账户额度。"
                status == 403 -> "没有访问权限，请检查 Key、模型授权和接口所属区域。"
                status == 429 -> "请求过于频繁或额度受限，请稍后重试并检查服务商额度。"
                status == 404 -> "接口或模型不存在，请检查接口地址和模型名称。"
                status in 300..399 -> "接口返回了重定向，请填写最终的 HTTPS 接口地址。"
                status >= 500 -> "模型服务暂时不可用（HTTP $status），请稍后重试。"
                else -> "请求失败（HTTP $status），请检查模型名称和接口兼容性。"
            }
        }
        fun parseCompletion(body: String): String {
            val choice = JSONObject(body).getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")
            if (choice.optString("finish_reason") != "stop" || message.optJSONArray("tool_calls")?.length()?.let { it > 0 } == true)
                throw AiFailure("模型回复未完成或要求调用工具，请缩小任务后重试。")
            val text = message.opt("content") as? String
            if (text.isNullOrBlank()) throw AiFailure("模型没有返回文本，请重试或更换模型。")
            return text
        }
        fun parseSuggestion(text: String): AiSuggestion {
            val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = runCatching { JSONObject(trimmed) }.getOrElse { throw AiFailure("无法读取命令建议，请重新生成。") }
            val command = json.opt("command") as? String ?: throw AiFailure("模型没有返回命令字段，请重新生成。")
            val explanation = json.opt("explanation") as? String ?: throw AiFailure("模型没有返回说明，请重新生成。")
            if (command.toByteArray(Charsets.UTF_8).size > 65_536 || command.any { it < ' ' && it != '\n' && it != '\t' || it == '\u007f' })
                throw AiFailure("命令过长或包含不支持的控制字符，请重新生成。")
            if (explanation.isBlank()) throw AiFailure("模型没有提供说明，请重新生成。")
            return AiSuggestion(command, explanation)
        }
    }
}
