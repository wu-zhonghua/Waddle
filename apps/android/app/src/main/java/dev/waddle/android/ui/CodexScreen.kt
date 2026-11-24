package dev.waddle.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.waddle.android.codex.CodexController
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun CodexScreen(client: CodexController, onTerminal: () -> Unit, onAccounts: () -> Unit, onClose: () -> Unit, onOpenFile: (File) -> Unit, onHistory: (String) -> Unit) {
    val input = client.draft
    var closing by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(client) { if (client.connected) client.refreshAccount() }
    LaunchedEffect(client.messages.size) { if (client.messages.isNotEmpty()) list.animateScrollToItem(client.messages.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon("terminal", "回到终端", onClick=onTerminal)
            Text(when { client.connecting -> "正在连接…"; !client.connected -> "未连接"; !client.signedIn -> "未登录"; client.approvals.isNotEmpty() -> "等待确认"; else -> client.status }, Modifier.weight(1f), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1, overflow=TextOverflow.Ellipsis)
            ToolIcon("history", "历史对话", client.connected) { historyOpen = true; client.loadHistory() }
            ToolIcon("account", "账号", onClick=onAccounts)
            ToolIcon("close", "结束对话") { closing = true }
        }
        if (client.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), verticalArrangement=Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) {
                WaddleIcon("terminal", modifier=Modifier.size(48.dp))
                Spacer(Modifier.height(24.dp))
                Text("在项目中与 Codex 协作", style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                Column(Modifier.widthIn(max=240.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    listOf("folder" to "项目文件", "terminal" to "命令与输出", "file" to "代码改动").forEach { (icon, title) ->
                        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            WaddleIcon(icon); Text(title, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                if (!client.connected) PrimaryButton(onClick={ client.connect() }, enabled=!client.connecting) { Text("连接 Codex") }
                else if (!client.signedIn) PrimaryButton(onClick=onAccounts) { Text("去账号页登录") }
                else Text("输入任务开始", color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(client.messages, key = { it.id }) { message ->
                val tool = message.role in listOf("命令", "文件修改", "工具输出")
                Column(Modifier.fillMaxWidth(), horizontalAlignment=Alignment.CenterHorizontally) {
                    Surface(Modifier.widthIn(max=760.dp).fillMaxWidth(), color=if(tool) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background,
                        shape=MaterialTheme.shapes.extraSmall) {
                        Column(Modifier.padding(if(tool) 12.dp else 0.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                if(tool) WaddleIcon(if(message.role == "文件修改") "file" else "terminal")
                                Text(message.role, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            MessageFiles(message, client.workingDirectory, client.runtime.home, onOpenFile)
                        }
                    }
                }
            }
        }
        if (client.messages.isNotEmpty() && !client.connected) QuietButton(onClick={ client.connect() }, enabled=!client.connecting) { Text("连接 Codex") }
        if (client.connected && !client.signedIn && client.messages.isNotEmpty()) QuietButton(onClick=onAccounts) { Text("去账号页登录") }
        if (client.connected && client.signedIn && !client.busy && client.lastThreadId != null && client.threadId == null)
            QuietButton(onClick={ client.resume() }) { Text("重试恢复") }
        client.error?.let { Text(it, Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState()).padding(12.dp), color = MaterialTheme.colorScheme.error) }
        if (client.busy) QuietButton(onClick = client::interrupt) { Text("停止") }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { client.draft = it }, modifier = Modifier.weight(1f), placeholder = { Text("给 Codex 一个任务") }, maxLines = 4)
            PrimaryButton(onClick = { client.send(input); client.draft = "" }, enabled = input.isNotBlank() && client.signedIn && client.connected && !client.busy &&
                (client.lastThreadId == null || client.threadId != null)) { Text("发送") }
        }
    }
    if (historyOpen) AlertDialog(onDismissRequest = { historyOpen = false }, title = { Text("历史对话") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(client.history, key = { it.id }) { entry ->
                    OutlinedCard(onClick = { historyOpen = false; onHistory(entry.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (entry.updatedAt > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.updatedAt * 1000)) else "更新时间未知",
                                style = MaterialTheme.typography.labelSmall)
                            Text(shortPath(entry.cwd, client.runtime.home), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (client.historyCursor != null) item {
                    QuietButton(onClick = { client.loadHistory(more = true) }, enabled = !client.historyLoading) { Text("加载更早对话") }
                }
            }
            if (client.historyLoading) Text("正在加载…")
            else if (client.history.isEmpty() && client.historyError == null) Text("还没有历史对话")
            client.historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { PrimaryButton(onClick = { historyOpen = false }) { Text("关闭") } },
        dismissButton = { QuietButton(onClick = { client.loadHistory() }, enabled = client.connected && !client.historyLoading) { Text("刷新") } })
    client.approvals.firstOrNull()?.let { approval ->
        AlertDialog(onDismissRequest = {}, title = { Text(approval.title) }, text = {
            SelectionContainer { Text(shortPathsInText(approval.detail, client.runtime.home), Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), fontFamily=FontFamily.Monospace) }
        }, confirmButton = { PrimaryButton(onClick = { client.answerApproval(approval, true) }) { Text("允许本次") } },
            dismissButton = { QuietButton(onClick = { client.answerApproval(approval, false) }) { Text("拒绝") } })
    }
    if (closing) AlertDialog(onDismissRequest = { closing = false }, title = { Text("结束对话？") }, text = { Text("会停止此对话的任务；文件和登录保留。") },
        confirmButton = { PrimaryButton(onClick = onClose) { Text("结束对话") } }, dismissButton = { QuietButton(onClick = { closing = false }) { Text("保留") } })
}
