package dev.waddle.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.waddle.android.ai.AiProvider
import dev.waddle.android.terminal.TerminalWorkspace

@Composable
fun AccountsDialog(workspace: TerminalWorkspace, onDismiss: () -> Unit) {
    val agents = workspace.agents
    val account = agents?.account
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(false) }
    var modelProvider by remember { mutableStateOf<AiProvider?>(null) }
    var codexSettings by remember { mutableStateOf(false) }
    var logout by remember { mutableStateOf(false) }
    LaunchedEffect(account) { account?.let { if (it.connected) it.refreshAccount() else it.connect() } }
    DisposableEffect(Unit) { onDispose { agents?.refreshAccounts() } }
    FullPage("账号", onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (account != null) {
                SectionLabel("编程工具")
                ServiceRow("Codex", if(account.signedIn) (if(account.accountType == "apiKey") "Key 已保存" else "已登录") else "未登录", "管理 Codex 账号与界面", "terminal") { codexSettings=true }
            }
            SectionLabel("内置模型")
            AiProvider.entries.forEach { provider ->
                val profile = workspace.suggestions.settings.profile(provider)
                ServiceRow(provider.label, if(profile.hasKey) "Key 已保存" else "未配置", "配置") { modelProvider=provider; models=true }
            }
            QuietButton(onClick={ modelProvider=null; models=true }, modifier=Modifier.padding(16.dp)) { Text("AI 模型 · 配置 Key 和模型") }
        }
    }
    if (codexSettings && account != null) FullPage("Codex 账号与界面", { codexSettings = false; agents.refreshAccounts() }, "返回账号") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text("Codex · " + if (account.signedIn) (if (account.accountType == "apiKey") "Key 已保存" else "已登录") else "未登录")
                if (!account.connected) QuietButton(onClick = { account.connect() }, enabled = !account.connecting) { Text("连接 Codex") }
                if (account.signedIn) QuietButton(onClick = { logout = true }) { Text("退出 Codex 登录") }
                else {
                    PrimaryButton(onClick = account::login, enabled = account.connected) { Text("用 ChatGPT 登录") }
                    account.loginCode?.let { code ->
                        SelectionContainer { Text(code, style = MaterialTheme.typography.headlineSmall) }
                        QuietButton(onClick = { account.loginUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } }) { Text("打开登录网页") }
                        QuietButton(onClick = account::cancelLogin) { Text("取消登录") }
                    }
                    OutlinedTextField(key, { key = it }, label = { Text("Codex API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    SecondaryButton(onClick = { account.loginWithKey(key.trim()); key = "" }, enabled = key.isNotBlank() && account.connected) { Text("保存 Codex Key") }
                }
                QuietButton(onClick = account::refreshAccount, enabled = account.connected) { Text("刷新登录状态") }
                account.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Codex 打开方式")
                listOf("ask" to "每次询问", "chat" to "对话界面（推荐）", "terminal" to "终端界面").forEach { (mode, label) ->
                    Row { RadioButton(agents.preferred == mode, { agents.preference(mode) }); QuietButton(onClick = { agents.preference(mode) }) { Text(label) } }
                }
                Text("codex --waddle-chat / --waddle-terminal", style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
        }
    }
    if (models) AiSettingsDialog(workspace.suggestions.settings, initialProvider=modelProvider) { models = false }
    if (logout && account != null) AlertDialog(onDismissRequest = { logout = false }, title = { Text("退出 Codex 登录？") }, text = { Text("将停止当前 Codex 对话，并清除共用登录。文件保留。") },
        confirmButton = { PrimaryButton(onClick = {
            agents.sessions.toList().forEach(agents::close); workspace.suggestions.cancel(); account.logout(); logout = false
        }) { Text("退出登录") } }, dismissButton = { QuietButton(onClick = { logout = false }) { Text("取消") } })
}
