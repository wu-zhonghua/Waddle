package dev.waddle.android.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProvider(val label: String, val endpoint: String, val models: List<String>) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", listOf("deepseek-flash", "deepseek-v4-pro")),
    QWEN("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", listOf("qwen-plus", "qwen-turbo")),
    KIMI("Kimi", "https://api.moonshot.cn/v1", listOf("kimi-k3.1-pro", "kimi-k2.5")),
    ZHIPU("智谱", "https://open.bigmodel.cn/api/paas/v4", listOf("glm-5.3", "glm-4.7-flash")),
    CUSTOM("自定义（OpenAI 兼容）", "", emptyList()),
}

data class AiProfile(val provider: AiProvider, val endpoint: String, val model: String, val hasKey: Boolean)
// Deliberately not a data class: never include credentials in generated toString/log messages.
class AiCredentials(val endpoint: String, val model: String, val key: String)

fun completionUrl(endpoint: String): String {
    val uri = runCatching { URI(endpoint.trim()) }.getOrNull()
    require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
        uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
        "请填写 HTTPS 接口地址，不要包含账号、Key、查询参数或片段。"
    }
    val base = endpoint.trim().trimEnd('/')
    return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
}

/** Keys are encrypted with a non-exportable Android Keystore key; backups are disabled. */
class AiSettings(context: Context, private val namespace: String = "ai_models") {
    private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val alias = "waddle.$namespace"
    val selected: AiProvider get() = runCatching { AiProvider.valueOf(prefs.getString("selected", "DEEPSEEK")!!) }.getOrDefault(AiProvider.DEEPSEEK)
    fun profile(provider: AiProvider = selected) = AiProfile(provider,
        prefs.getString("${provider.name}.endpoint", provider.endpoint)!!,
        prefs.getString("${provider.name}.model", provider.models.firstOrNull().orEmpty())!!,
        prefs.contains("${provider.name}.key"))
    fun configured() = profile().let { it.hasKey && it.model.isNotBlank() && it.endpoint.isNotBlank() }
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun credentials(provider: AiProvider = selected, endpoint: String = profile(provider).endpoint,
                    model: String = profile(provider).model, replacement: String = ""): AiCredentials {
        completionUrl(endpoint)
        require(model.isNotBlank() && model.length <= 200 && model.none { it < ' ' }) { "请填写有效的模型名称。" }
        val key = replacement.trim().ifEmpty {
            require(endpoint.trim().trimEnd('/') == profile(provider).endpoint.trimEnd('/')) { "接口地址已改变，请重新填写 Key。" }
            val encrypted = prefs.getString("${provider.name}.key", null) ?: error("请先填写 API Key。")
            try {
                val parts = encrypted.split(':')
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
                String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            } catch (_: Exception) { error("无法读取本机 Key，请删除后重新填写。") }
        }
        require(key.isNotBlank() && key.none { it <= ' ' || it == '\u007f' }) { "API Key 格式不正确，请检查空格和换行。" }
        return AiCredentials(endpoint.trim().trimEnd('/'), model.trim(), key)
    }
    fun save(provider: AiProvider, endpoint: String, model: String, replacement: String) {
        val credentials = credentials(provider, endpoint, model, replacement)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(credentials.key.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("selected", provider.name)
            .putString("${provider.name}.endpoint", credentials.endpoint).putString("${provider.name}.model", credentials.model)
            .putString("${provider.name}.key", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "保存失败，请重试。"
        }
    }
    fun deleteKey(provider: AiProvider) {
        check(prefs.edit().remove("${provider.name}.key").commit()) { "删除失败，请重试。" }
    }
}
