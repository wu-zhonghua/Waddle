package dev.waddle.android.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.KeyHandler
import com.termux.view.TerminalView
import dev.waddle.android.terminal.TerminalContext
import dev.waddle.android.terminal.TerminalWorkspace

private val Canvas = Color(0xFF101512)
private val Panel = Color(0xFF19211C)
private val Accent = Color(0xFFA7E8BD)
private val Muted = Color(0xFF96A59B)

@Composable
fun TerminalScreen(workspace: TerminalWorkspace?, onNewSession: () -> Unit, onAiSettings: () -> Unit, onConversation: (dev.waddle.android.codex.CodexSession) -> Unit = {}) {
    val context = LocalContext.current
    val input = remember { InputState() }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var aiContext by remember { mutableStateOf<TerminalContext?>(null) }
    var diagnose by remember { mutableStateOf(false) }
    var needsAi by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var pasteText by remember { mutableStateOf<String?>(null) }
    val selected = workspace?.selected
    LaunchedEffect(selected?.terminal?.mHandle) {
        input.control = false
        if (aiContext?.sessionId != selected?.terminal?.mHandle) aiContext = null
    }
    fun previewPaste() {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        pasteText = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }
    DisposableEffect(workspace) {
        val callback: () -> Unit = { previewPaste() }
        workspace?.requestPaste = callback
        onDispose { if (workspace?.requestPaste === callback) workspace.requestPaste = null }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, onPrimary = Canvas, background = Canvas, surface = Panel,
        onBackground = Color(0xFFE4ECE6), onSurface = Color(0xFFE4ECE6),
    )) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                if (selected != null) {
                    val revision = selected.revision
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                        factory = { ctx ->
                            TerminalView(ctx, null).apply {
                                setTerminalViewClient(TerminalInput(this, input))
                                setTextSize((input.fontSize * resources.displayMetrics.scaledDensity).toInt())
                                setTypeface(Typeface.MONOSPACE)
                                setBackgroundColor(android.graphics.Color.rgb(16, 21, 18))
                                isFocusableInTouchMode = true
                                contentDescription = "Terminal input and output"
                                terminalView = this
                            }
                        },
                        update = { view ->
                            revision
                            val pixels = (input.fontSize * view.resources.displayMetrics.scaledDensity).toInt()
                            if (view.tag != pixels) {
                                view.setTextSize(pixels)
                                view.tag = pixels
                            }
                            if (view.currentSession !== selected.terminal) {
                                view.attachSession(selected.terminal)
                                // The first update can run before AndroidView is attached. Focus after mounting,
                                // so typing immediately after "open terminal here" goes to the shell.
                                view.post {
                                    if (view.isAttachedToWindow && view.currentSession === selected.terminal) view.requestFocus()
                                }
                            }
                            view.onScreenUpdated()
                        },
                        onRelease = { terminalView = null },
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.status, color = if (selected.running) Accent else Muted, fontSize = 9.sp,
                            letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        Text("Bash · Waddle", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(">_", color = Accent, fontSize = 42.sp, fontFamily = FontFamily.Monospace)
                            Text(if (workspace == null) "Connecting to your shell…" else "Room for a fresh start.", color = Muted)
                            if (workspace != null) Button(onClick = onNewSession) { Text("Open a terminal") }
                        }
                    }
                }
                workspace?.agents?.sessions?.filter { it.origin === selected }?.forEach { conversation ->
                    TextButton(onClick = { onConversation(conversation) }, modifier = Modifier.fillMaxWidth()) { Text("${conversation.label} · 对话进行中 · 返回对话") }
                }
                (aiError ?: selected?.commandError)?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
                }
                fun openAi(explain: Boolean) {
                    if (workspace != null && workspace.agents == null && !workspace.suggestions.settings.configured()) { needsAi = true; return }
                    runCatching { requireNotNull(workspace).captureContext() }.fold(onSuccess = { aiContext = it; diagnose = explain; aiError = null },
                        onFailure = { aiError = it.message })
                }
                if (selected?.promptReady == true && selected.lastExitCode?.let { it != 0 } == true) {
                    TextButton(onClick = { openAi(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("退出码 ${selected.lastExitCode} · 让 AI 看看")
                    }
                }
                OutlinedButton(onClick = { openAi(false) }, enabled = selected?.running == true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { Text("✦ 问 AI，写命令…") }
                HorizontalDivider(color = Color(0xFF2B352E))
                Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    KeyButton("Ctrl", selected = input.control, enabled = selected?.running == true) { input.control = !input.control }
                    listOf("Esc" to KeyEvent.KEYCODE_ESCAPE, "Tab" to KeyEvent.KEYCODE_TAB,
                        "←" to KeyEvent.KEYCODE_DPAD_LEFT, "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
                        "↑" to KeyEvent.KEYCODE_DPAD_UP, "→" to KeyEvent.KEYCODE_DPAD_RIGHT).forEach { (label, key) ->
                        KeyButton(label, enabled = selected?.running == true) {
                            terminalView?.handleKeyCode(key, if (input.control) KeyHandler.KEYMOD_CTRL else 0)
                            input.control = false
                        }
                    }
                    KeyButton("^C", enabled = selected?.running == true) { selected?.terminal?.write("\u0003"); input.control = false }
                    KeyButton("Paste", enabled = selected?.running == true) {
                        previewPaste()
                    }
                    KeyButton("Keys", enabled = selected != null) {
                        terminalView?.let { view ->
                            view.requestFocus()
                            val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            keyboard.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                }
            }
        }
        aiContext?.let { snapshot ->
            if (workspace != null) CommandSheet(workspace, snapshot, diagnose = diagnose, onAccounts = onAiSettings, onDismiss = { aiContext = null }, onRun = {
                aiContext = null
                terminalView?.requestFocus()
            })
        }
        if (needsAi) AlertDialog(onDismissRequest = { needsAi = false }, title = { Text("先配置 AI 模型") },
            text = { Text("终端可继续使用。要生成命令或解释报错，请先在设置里选择模型并填写 API Key。") },
            confirmButton = { TextButton(onClick = { needsAi = false; onAiSettings() }) { Text("去设置") } },
            dismissButton = { TextButton(onClick = { needsAi = false }) { Text("取消") } })
        pasteText?.let { text ->
            AlertDialog(onDismissRequest = { pasteText = null }, title = { Text("Paste into ${selected?.label ?: "terminal"}?") },
                text = { Column {
                    Text("Line breaks can execute commands.", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(text.take(1000), fontFamily = FontFamily.Monospace, maxLines = 10)
                } },
                confirmButton = { TextButton(onClick = {
                    if (selected?.running == true) selected.terminal.emulator?.paste(text)
                    pasteText = null
                }) { Text("Paste") } },
                dismissButton = { TextButton(onClick = { pasteText = null }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun KeyButton(label: String, selected: Boolean = false, enabled: Boolean = true, action: () -> Unit) {
    TextButton(onClick = action, enabled = enabled, modifier = Modifier.height(48.dp).widthIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 10.dp), shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) Canvas else Accent,
            containerColor = if (selected) Accent else Color.Transparent)) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
