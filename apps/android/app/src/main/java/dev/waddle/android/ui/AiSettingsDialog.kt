package dev.waddle.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.waddle.android.ai.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

@Composable
fun AiSettingsDialog(store: AiSettings, initialProvider: AiProvider? = null, onDismiss: () -> Unit) {
    var provider by remember { mutableStateOf(initialProvider ?: store.selected) }
    var endpoint by remember { mutableStateOf(store.profile(initialProvider ?: store.selected).endpoint) }
    var model by remember { mutableStateOf(store.profile(initialProvider ?: store.selected).model) }
    var hasKey by remember { mutableStateOf(store.profile(initialProvider ?: store.selected).hasKey) }
    var key by remember { mutableStateOf("") }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<AiClient?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { active?.cancel(); key = "" } }
    FullPage("AI 模型", onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("服务与接口")
            Box {
                SecondaryButton(onClick = { providerMenu = true }, enabled = !testing) { Text(provider.label) }
                DropdownMenu(providerMenu, { providerMenu = false }) {
                    AiProvider.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = {
                        provider = item; val profile = store.profile(item)
                        endpoint = profile.endpoint; model = profile.model; hasKey = profile.hasKey
                        key = ""; message = null; providerMenu = false
                    }) }
                }
            }
            OutlinedTextField(endpoint, { endpoint = it; message = null }, enabled = !testing, singleLine = true,
                label = { Text("接口地址（HTTPS）") }, modifier = Modifier.fillMaxWidth())
            Text("Base URL 或 /chat/completions", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(model, { model = it; message = null }, enabled = !testing, singleLine = true,
                label = { Text("模型名称") }, modifier = Modifier.fillMaxWidth())
            if (provider.models.isNotEmpty()) Box {
                QuietButton(onClick = { modelMenu = true }, enabled = !testing) { Text("选择模型") }
                DropdownMenu(modelMenu, { modelMenu = false }) {
                    provider.models.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { model = item; modelMenu = false; message = null }) }
                }
            }
            OutlinedTextField(key, { key = it; message = null }, enabled = !testing, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text(if (hasKey) "Key 已保存 · 留空保留，输入替换" else "API Key") },
                modifier = Modifier.fillMaxWidth())
            if (hasKey) QuietButton(onClick = {
                runCatching { store.deleteKey(provider) }.fold(onSuccess = { hasKey = false; key = ""; message = "已删除本机 Key。" }, onFailure = { message = it.message })
            }, enabled = !testing) { Text("删除已保存的 Key") }
            var privacy by remember { mutableStateOf(false) }
            QuietButton(onClick={ privacy=!privacy }) { Text("Key 与上下文隐私") }
            if(privacy) Text("Key 加密保存在本机且不回显，只用于当前接口。生成和诊断发送路径及最近 12,000 字符，可在发送前查看；测试连接只发送测试消息，可能消耗服务商额度。", style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(onClick = {
                    runCatching { store.save(provider, endpoint, model, key) }.fold(onSuccess = {
                        key = ""; hasKey = true; message = "已保存，终端 AI 将使用此模型。"
                    }, onFailure = { message = it.message ?: "保存失败，请重试。" })
                }, enabled = !testing) { Text("保存") }
                SecondaryButton(onClick = {
                    val credentials = runCatching { store.credentials(provider, endpoint, model, key) }.getOrElse { message = it.message; return@SecondaryButton }
                    val client = AiClient(); active = client; testing = true; message = null
                    scope.launch {
                        try {
                            withTimeout(120_000) {
                                withContext(Dispatchers.IO) { client.complete(credentials, JSONArray().put(AiClient.message("user", "Reply OK."))) }
                            }
                            message = "连接成功。当前填写的配置可用；修改后请点保存。"
                        } catch (e: Exception) {
                            message = (e as? AiFailure)?.message ?: "测试超时或已取消，请检查网络后重试。"
                        } finally { client.cancel(); active = null; testing = false }
                    }
                }, enabled = !testing) { Text(if (testing) "测试中…" else "测试连接") }
            }
            if (testing) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
        }
    }
}
