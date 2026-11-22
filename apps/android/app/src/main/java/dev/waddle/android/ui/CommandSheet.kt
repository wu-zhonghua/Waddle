package dev.waddle.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.waddle.android.terminal.TerminalContext
import dev.waddle.android.terminal.TerminalWorkspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandSheet(workspace: TerminalWorkspace, initialContext: TerminalContext, diagnose: Boolean = false, onDismiss: () -> Unit, onRun: () -> Unit) {
    val client = workspace.suggestions
    var snapshot by remember { mutableStateOf(initialContext) }
    var question by remember { mutableStateOf(if (diagnose) "解释刚才命令失败的原因（退出码 ${initialContext.exitCode}），给出修法；如有必要给出待确认的修复命令。" else "") }
    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showContext by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    val draftFocus = remember { FocusRequester() }
    DisposableEffect(client) { onDispose { client.cancel() } }
    // Requesting IME focus during the opening animation can move the sheet's anchors
    // while it is still hidden. Wait until the expanded sheet is actually on screen.
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.Expanded) focus.requestFocus()
    }
    LaunchedEffect(client.command) {
        draft = client.command
        if (draft.isNotBlank()) draftFocus.requestFocus()
    }
    // Observe prompt changes as well as tab changes while the drawer is open.
    workspace.selected?.revision
    val stale = workspace.contextError(snapshot)
    fun generate() {
        if (client.busy || question.isBlank()) return
        if (diagnose && stale != null) { error = stale; return }
        runCatching { workspace.captureContext() }.fold(onSuccess = {
            snapshot = it; error = null; submitted = false; draft = ""
            client.generate(it, question)
        }, onFailure = { error = it.message })
    }
    fun run() {
        if (submitted || draft.isBlank() || client.command.isBlank() || client.busy) return
        val origin = client.source ?: return
        workspace.runSuggestion(origin, draft).fold(onSuccess = {
            submitted = true; onRun()
        }, onFailure = { error = it.message })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (diagnose) "让 AI 看看 · 解释报错" else "问 AI · 生成命令", style = MaterialTheme.typography.titleLarge)
            Text("${snapshot.sessionLabel} · ${snapshot.cwd}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text("发送当前路径和最近终端内容。生成后先预览，回车才运行。", fontSize = 12.sp)
            TextButton(onClick = { showContext = !showContext }) { Text(if (showContext) "收起本次上下文" else "查看本次上下文") }
            if (showContext) {
                OutlinedTextField(value = snapshot.transcript, onValueChange = {}, readOnly = true,
                    label = { Text("最近终端内容（最多 12,000 字符）") }, maxLines = 6,
                    modifier = Modifier.fillMaxWidth(), textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp))
            }
            OutlinedTextField(value = question, onValueChange = { question = it }, enabled = !client.busy,
                label = { Text("想去哪里，或想做什么？") }, placeholder = { Text("比如：进入刚才列出的项目，再找出最大的文件") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("ai-question"), minLines = 2, maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { generate() }))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { generate() }, enabled = question.isNotBlank() && !client.busy) { Text(if (client.busy) "正在生成…" else if (diagnose) "分析报错" else "生成命令") }
                if (client.busy) TextButton(onClick = { client.cancel() }) { Text("停止生成") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
            if (client.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: client.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            if (client.explanation.isNotBlank()) Text(client.explanation, fontSize = 13.sp)
            if (client.command.isNotBlank()) {
                OutlinedTextField(value = draft, onValueChange = { draft = it },
                    label = { Text("待执行命令 · 可编辑") }, minLines = 3, maxLines = 9,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().focusRequester(draftFocus).testTag("ai-command").onPreviewKeyEvent {
                        if (it.key == Key.Enter && !it.isShiftPressed) {
                            if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) run()
                            true
                        } else false
                    }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onGo = { run() }))
                if (stale != null) Text(stale, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Text("回车运行 · Shift+回车换行 · 在原 Bash 中执行，并替换尚未提交的输入", fontSize = 11.sp)
                Button(onClick = { run() }, enabled = stale == null && draft.isNotBlank() && !submitted && !client.busy,
                    modifier = Modifier.fillMaxWidth().testTag("ai-run")) { Text("运行命令 ↵") }
            }
        }
    }
}
