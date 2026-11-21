package dev.waddle.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.waddle.android.codex.CodexController
import org.json.JSONObject
import java.io.File

@Composable
fun CodexScreen(client: CodexController, onConnect: () -> Unit, selectedFile: String? = null, clearSelectedFile: () -> Unit = {}, onNewConversation: () -> Unit) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var fileError by remember { mutableStateOf<String?>(null) }
    var setup by remember { mutableStateOf(false) }
    var deferredApproval by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val lastText = client.messages.lastOrNull()?.text
    LaunchedEffect(client.messages.size, lastText) {
        if (client.messages.isNotEmpty()) listState.scrollToItem(client.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        selectedFile?.let { path ->
            Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(12.dp)) {
                    Text("已选择本地文件或目录 · 尚未发送", fontSize = 12.sp)
                    SelectionContainer { Text(path, modifier = Modifier.heightIn(max = 80.dp).verticalScroll(rememberScrollState()), fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                    Text("输入你想做的事，再点发送。模型会按请求读取此路径。", fontSize = 12.sp)
                    TextButton(onClick = { clearSelectedFile(); fileError = null }) { Text("移除选择") }
                }
            }
        }
        fileError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        if (!client.connected) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("在 Waddle 中使用本机 Codex", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("Codex 0.170.0 已随 Waddle 安装。启动后自动使用已有登录；尚未登录时可登录 ChatGPT。", color = Color(0xFFB8C8BD))
                Text("这里与“终端”页共用文件、工具和 Codex 登录。也可以在终端直接输入 codex。模型请求需要联网。", color = Color(0xFFB8C8BD))
                Text("当前 Android 运行时不能保证工作区目录隔离。Codex 可访问 Waddle 权限允许的文件；收到审批请求时会在这里询问。", color = Color(0xFFE8C58D), fontSize = 13.sp)
                Button(onClick = onConnect, enabled = !client.connecting) { Text(if (client.connecting) "正在连接…" else "启动 Codex") }
                TextButton(onClick = { setup = true }) { Text("查看运行环境") }
            }
        } else if (!client.signedIn) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("登录 ChatGPT", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("登录由内嵌 Codex 管理，凭据保存在 Waddle 私有目录。终端和此页面共用登录状态。", color = Color(0xFFB8C8BD))
                if (client.loginCode == null) Button(onClick = { client.login() }) { Text("获取设备登录码") }
                client.loginCode?.let { code ->
                    SelectionContainer { Text(code, fontFamily = FontFamily.Monospace, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary) }
                    Button(onClick = {
                        copy(context, "Codex device code", code)
                        client.loginUrl?.let { open(context, it) }
                    }) { Text("复制登录码并打开浏览器") }
                    Text("在浏览器输入以上一次性码。完成后返回 Waddle；过期时取消并重新获取。", color = Color(0xFFB8C8BD))
                    TextButton(onClick = { client.cancelLogin() }) { Text("取消登录") }
                }
                TextButton(onClick = { client.refreshAccount() }) { Text("我已登录，刷新状态") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onNewConversation) { Text("新会话") }
                TextButton(onClick = { client.resume() }, enabled = !client.busy && client.threadId == null && client.lastThreadId != null) { Text("恢复上次") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (client.messages.isEmpty()) item {
                    Text("给 Codex 一个任务，从当前工作区开始。", color = Color(0xFFB8C8BD))
                }
                items(client.messages, key = { it.id }) { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = if (message.role == "你") Color(0xFF253B2D) else Color(0xFF19211C)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(message.role, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            if (message.role == "Waddle 原生工具") SettingsResult(message.text)
                            else if (message.role == "Waddle 亮度工具") DisplayWriteResult(message.text)
                            else SelectionContainer { Text(message.text, color = Color(0xFFE4ECE6), fontSize = 14.sp,
                                fontFamily = if (message.role == "命令") FontFamily.Monospace else FontFamily.Default) }
                        }
                    }
                }
            }
            client.approvals.firstOrNull()?.let { approval ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("等待你确认 · 尚未执行", Modifier.weight(1f), fontSize = 12.sp)
                    TextButton(onClick = { deferredApproval = null }) { Text("查看审批") }
                }
            }
            if (client.busy) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp)); Text("正在处理", color = Color(0xFFB8C8BD), fontSize = 12.sp)
                Spacer(Modifier.weight(1f)); TextButton(onClick = { client.interrupt() }) { Text("停止") }
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("给 Codex 一个任务") }, maxLines = 4)
                TextButton(onClick = {
                    val file = selectedFile?.let(::File)
                    if (file != null && (!file.exists() || !file.canRead())) fileError = "所选文件已不存在或无法读取，请重新选择。"
                    else {
                        val text = if (file == null) input else "用户选中的本地文件或目录（JSON 路径）：${JSONObject.quote(file.path)}\n用户要求：$input"
                        fileError = null; input = ""; client.send(text); clearSelectedFile()
                    }
                }, enabled = input.isNotBlank() && !client.busy) { Text("发送") }
            }
        }
        client.error?.let { Text(it, Modifier.fillMaxWidth().heightIn(max = 110.dp).verticalScroll(rememberScrollState()).padding(12.dp), color = Color(0xFFFFB4AB), fontSize = 12.sp) }
    }
    client.approvals.firstOrNull()?.takeIf { it.id.toString() != deferredApproval }?.let { approval ->
        AlertDialog(onDismissRequest = { deferredApproval = approval.id.toString() }, title = { Text(approval.title) },
            text = { SelectionContainer { Text(approval.detail, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { client.answerApproval(approval, true) }) { Text("允许本次") } },
            dismissButton = { Row {
                TextButton(onClick = { deferredApproval = approval.id.toString() }) { Text("稍后处理") }
                TextButton(onClick = { client.answerApproval(approval, false) }) { Text("拒绝") }
            } })
    }
    if (setup) CodexSettingsDialog(client) { setup = false }
}

@Composable
internal fun CodexSettingsDialog(client: CodexController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var permissionError by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("当前 Codex · 设置与运行环境") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("读取手机公开设置", Modifier.weight(1f))
                Switch(checked = client.settingsEnabled, onCheckedChange = { client.settingsEnabled = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("允许修改亮度 · 每次确认", Modifier.weight(1f))
                if (client.canWriteDisplay) Switch(checked = client.displayWritesEnabled, onCheckedChange = client::updateDisplayWritesEnabled)
                else TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        .onFailure { permissionError = "无法打开授权页，请到系统设置中为 Waddle 授权。" }
                }) { Text("系统授权") }
            }
            permissionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (client.connected || client.connecting) TextButton(onClick = { client.disconnect() }) { Text("断开 Codex（保留登录）") }
            Text("Codex、Bash、Git、ripgrep、curl 和依赖库随 APK 安装。无需 Termux、外部运行命令权限或电脑服务。")
            Text("终端与 Codex 面板共用 Waddle 的私有 home。终端中可以直接输入 codex；两个入口共用登录状态。")
            SelectionContainer { Text("工作目录：${client.workingDirectory.path}\n配置目录：${client.configDirectory}", fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            Text("系统读取工具提供五个公开字段。亮度修改仅支持屏幕亮度和自动/手动模式：先授予系统权限，再开启亮度修改，每次操作都需要确认。断开会关闭修改开关。Android 授予的系统权限范围更宽，此工具仅使用这两项。")
            Text("新工具需要新建会话；旧会话可以恢复查看。脚本使用 bash script.sh 或 sh script.sh 运行；本版尚无任意软件包安装功能。")
            Text("当前内嵌运行时仅支持 ARM64。Android 的工作区目录隔离仍有限；断开后可恢复已保存的会话。", color = Color(0xFFE8C58D))
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } })

}

@Composable
private fun DisplayWriteResult(raw: String) {
    val result = remember(raw) { runCatching { JSONObject(raw).also { it.getJSONObject("before"); it.getJSONObject("values") } }.getOrNull() }
    var expanded by remember { mutableStateOf(false) }
    if (result == null) {
        Text(raw, color = Color(0xFFE8C58D), fontSize = 13.sp)
        return
    }
    val setting = result.getString("setting")
    val before = result.getJSONObject("before").getInt(setting)
    val after = result.getJSONObject("values").getInt(setting)
    fun label(value: Int) = if (setting == "brightness_mode") (if (value == 0) "手动" else "自动") else value.toString()
    Text("${if (setting == "brightness") "屏幕亮度" else "亮度模式"}：${label(before)} → ${label(after)}", color = Color(0xFFE4ECE6), fontSize = 14.sp)
    Text(if (!result.getBoolean("verified")) "读回值不符合请求，请检查系统设置。"
        else if (result.getBoolean("changed")) "已修改，并通过系统接口读回核对。" else "已经是目标值，无需修改。",
        color = if (result.getBoolean("verified")) Color(0xFF96A59B) else Color(0xFFFFB4AB), fontSize = 12.sp)
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起原始结果" else "查看原始结果", fontSize = 11.sp) }
    if (expanded) SelectionContainer { Text(raw, color = Color(0xFFB8C8BD), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
}

@Composable
private fun SettingsResult(raw: String) {
    val values = remember(raw) { runCatching { JSONObject(raw).getJSONObject("values") }.getOrNull() }
    var expanded by remember { mutableStateOf(false) }
    if (values == null) {
        Text(raw, color = Color(0xFFFFB4AB), fontSize = 13.sp)
        return
    }
    val labels = mapOf("brightness" to "亮度（系统原始值）", "brightness_mode" to "亮度模式",
        "screen_timeout_ms" to "屏幕超时", "airplane_mode" to "飞行模式", "accessibility_enabled" to "辅助功能")
    values.keys().forEach { key ->
        val value = if (values.isNull(key)) "不可用" else when (key) {
            "brightness_mode" -> when (values.optInt(key)) { 0 -> "手动"; 1 -> "自动"; else -> values.get(key).toString() }
            "airplane_mode", "accessibility_enabled" -> when (values.optInt(key)) { 0 -> "关闭"; 1 -> "开启"; else -> values.get(key).toString() }
            "screen_timeout_ms" -> "${values.get(key)} 毫秒"
            else -> values.get(key).toString()
        }
        Text("${labels[key] ?: key}：$value", color = Color(0xFFE4ECE6), fontSize = 14.sp)
    }
    Text("由 Android 系统接口读取 · 未修改设置", color = Color(0xFF96A59B), fontSize = 11.sp)
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起原始结果" else "查看原始结果", fontSize = 11.sp) }
    if (expanded) SelectionContainer { Text(raw, color = Color(0xFFB8C8BD), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
}

private fun copy(context: Context, label: String, value: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
}
private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
