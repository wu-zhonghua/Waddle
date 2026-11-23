package dev.waddle.android.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.waddle.android.codex.CodexController
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun CodexScreen(client: CodexController, onTerminal: () -> Unit, onAccounts: () -> Unit, onClose: () -> Unit, onOpenFile: (File) -> Unit, onHistory: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var closing by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(client) { if (client.connected) client.refreshAccount() }
    LaunchedEffect(client.messages.size) { if (client.messages.isNotEmpty()) list.animateScrollToItem(client.messages.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTerminal) { Text("回到终端") }
            TextButton(onClick = onAccounts) { Text("账号") }
            TextButton(onClick = { historyOpen = true; client.loadHistory() }, enabled = client.connected) { Text("历史对话") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { closing = true }) { Text("结束对话") }
        }
        Text(client.status, Modifier.padding(horizontal = 16.dp))
        if (!client.connected) Button(onClick = { client.connect() }, enabled = !client.connecting) { Text("连接 Codex") }
        else if (!client.signedIn) Button(onClick = onAccounts) { Text("去账号页登录") }
        else if (!client.busy && client.lastThreadId != null && client.threadId == null)
            TextButton(onClick = { client.resume() }) { Text("重试恢复") }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(client.messages, key = { it.id }) { message ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(message.role, color = MaterialTheme.colorScheme.primary)
                    MessageFiles(message, client.workingDirectory, client.runtime.home, onOpenFile)
                } }
            }
        }
        client.error?.let { Text(it, Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState()).padding(12.dp), color = MaterialTheme.colorScheme.error) }
        if (client.busy) TextButton(onClick = client::interrupt) { Text("停止") }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("给 Codex 一个任务") }, maxLines = 4)
            TextButton(onClick = { client.send(input); input = "" }, enabled = input.isNotBlank() && client.signedIn && client.connected && !client.busy &&
                (client.lastThreadId == null || client.threadId != null)) { Text("发送") }
        }
    }
    if (historyOpen) AlertDialog(onDismissRequest = { historyOpen = false }, title = { Text("历史对话") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("选择后在原工作目录继续，当前对话保留。")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(client.history, key = { it.id }) { entry ->
                    OutlinedCard(onClick = { historyOpen = false; onHistory(entry.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (entry.updatedAt > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.updatedAt * 1000)) else "更新时间未知",
                                style = MaterialTheme.typography.labelSmall)
                            Text(entry.cwd, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (client.historyCursor != null) item {
                    TextButton(onClick = { client.loadHistory(more = true) }, enabled = !client.historyLoading) { Text("加载更早对话") }
                }
            }
            if (client.historyLoading) Text("正在加载…")
            else if (client.history.isEmpty() && client.historyError == null) Text("还没有历史对话")
            client.historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("关闭") } },
        dismissButton = { TextButton(onClick = { client.loadHistory() }, enabled = client.connected && !client.historyLoading) { Text("刷新") } })
    client.approvals.firstOrNull()?.let { approval ->
        AlertDialog(onDismissRequest = {}, title = { Text(approval.title) }, text = {
            SelectionContainer { Text(approval.detail, Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) }
        }, confirmButton = { TextButton(onClick = { client.answerApproval(approval, true) }) { Text("允许本次") } },
            dismissButton = { TextButton(onClick = { client.answerApproval(approval, false) }) { Text("拒绝") } })
    }
    if (closing) AlertDialog(onDismissRequest = { closing = false }, title = { Text("结束对话？") }, text = { Text("会停止此对话的任务；文件和登录保留。") },
        confirmButton = { TextButton(onClick = onClose) { Text("结束对话") } }, dismissButton = { TextButton(onClick = { closing = false }) { Text("保留") } })
}
