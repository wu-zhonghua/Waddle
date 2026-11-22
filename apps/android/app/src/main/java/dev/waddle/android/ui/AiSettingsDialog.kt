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
fun AiSettingsDialog(store: AiSettings, onDismiss: () -> Unit) {
    var provider by remember { mutableStateOf(store.selected) }
    var endpoint by remember { mutableStateOf(store.profile().endpoint) }
    var model by remember { mutableStateOf(store.profile().model) }
    var hasKey by remember { mutableStateOf(store.profile().hasKey) }
    var key by remember { mutableStateOf("") }
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf<AiClient?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { active?.cancel(); key = "" } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("AI 模型") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("选择自己的服务商。Key 仅加密保存在这台手机，不会显示已保存的明文。")
            Box {
                OutlinedButton(onClick = { providerMenu = true }, enabled = !testing) { Text(provider.label) }
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
            Text("可填 Base URL 或完整 /chat/completions 地址。Key 必须属于该地址。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(model, { model = it; message = null }, enabled = !testing, singleLine = true,
                label = { Text("模型名称") }, modifier = Modifier.fillMaxWidth())
            if (provider.models.isNotEmpty()) Box {
                TextButton(onClick = { modelMenu = true }, enabled = !testing) { Text("选择模型") }
                DropdownMenu(modelMenu, { modelMenu = false }) {
                    provider.models.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { model = item; modelMenu = false; message = null }) }
                }
            }
            OutlinedTextField(key, { key = it; message = null }, enabled = !testing, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text(if (hasKey) "Key 已保存 · 留空保留，输入替换" else "API Key") },
                modifier = Modifier.fillMaxWidth())
            if (hasKey) TextButton(onClick = {
                runCatching { store.deleteKey(provider) }.fold(onSuccess = { hasKey = false; key = ""; message = "已删除本机 Key。" }, onFailure = { message = it.message })
            }, enabled = !testing) { Text("删除已保存的 Key") }
            Text("生成或诊断时会把当前路径与最近 12,000 字符终端内容发给所选服务商；可在发送前查看。测试连接只发送一句测试消息，可能消耗少量服务商额度。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching { store.save(provider, endpoint, model, key) }.fold(onSuccess = {
                        key = ""; hasKey = true; message = "已保存，终端 AI 将使用此模型。"
                    }, onFailure = { message = it.message ?: "保存失败，请重试。" })
                }, enabled = !testing) { Text("保存") }
                OutlinedButton(onClick = {
                    val credentials = runCatching { store.credentials(provider, endpoint, model, key) }.getOrElse { message = it.message; return@OutlinedButton }
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
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })
}
