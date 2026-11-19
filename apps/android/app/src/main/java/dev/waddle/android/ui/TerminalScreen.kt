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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.KeyHandler
import com.termux.view.TerminalView
import dev.waddle.android.terminal.ShellSession
import dev.waddle.android.terminal.TerminalWorkspace

private val Canvas = Color(0xFF101512)
private val Panel = Color(0xFF19211C)
private val Accent = Color(0xFFA7E8BD)
private val Muted = Color(0xFF96A59B)

@Composable
fun TerminalScreen(workspace: TerminalWorkspace?, onNewSession: () -> Unit) {
    val context = LocalContext.current
    val input = remember { InputState() }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var showHelp by remember { mutableStateOf(false) }
    var closeTarget by remember { mutableStateOf<ShellSession?>(null) }
    var pasteText by remember { mutableStateOf<String?>(null) }
    val selected = workspace?.selected
    LaunchedEffect(selected?.terminal?.mHandle) { input.control = false }
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
        primary = Accent, background = Canvas, surface = Panel,
        onBackground = Color(0xFFE4ECE6), onSurface = Color(0xFFE4ECE6),
    )) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Accent), contentAlignment = Alignment.Center) {
                        Text(">_", color = Canvas, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Waddle", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Text("A little room to build.", fontSize = 11.sp, color = Muted)
                    }
                    Text("● LOCAL", color = Accent, fontSize = 10.sp, letterSpacing = 1.sp)
                    TextButton(onClick = { showHelp = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp)) {
                        Text("?", fontSize = 19.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    workspace?.sessions?.forEach { session ->
                        val active = session === selected
                        TextButton(onClick = { workspace.select(session); input.control = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = if (active) Accent else Muted),
                            shape = RoundedCornerShape(8.dp)) {
                            Text("${if (session.running) "●" else "○"}  ${session.label}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onNewSession, enabled = workspace != null && workspace.sessions.size < 8) { Text("+ New") }
                }
                if (selected != null) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ON THIS DEVICE  /  ${selected.label.uppercase()}", color = Muted, fontSize = 10.sp,
                            letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { closeTarget = selected }) { Text("Close", color = Muted, fontSize = 12.sp) }
                    }
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
                                view.requestFocus()
                            }
                            view.onScreenUpdated()
                        },
                        onRelease = { terminalView = null },
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.status, color = if (selected.running) Accent else Muted, fontSize = 9.sp,
                            letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        Text("/system/bin/sh", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
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
        if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("Your pocket workspace") },
            text = { Text("A real local Android shell.\n\nTry pwd, ls, uname -a or cat WELCOME.txt.\n\nTap the terminal to type. Pinch to resize text. Long-press to select and copy. Ctrl applies to the next key; ^C interrupts a running command.\n\nSessions stay open in the background while the service is alive. Close them from the notification. Android may still stop the app.\n\nVersion 0.1.0 · SSH, packages and AI are coming later.\nTerminal components: Termux (GPLv3; upstream Apache-2.0 portions).") },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } })
        closeTarget?.let { target ->
            AlertDialog(onDismissRequest = { closeTarget = null }, title = { Text("Close ${target.label}?") },
                text = { Text(if (target.running) "The shell will stop and this session's output will be removed." else "Remove this finished session and its output.") },
                confirmButton = { TextButton(onClick = { workspace?.close(target); closeTarget = null }) { Text("Close session") } },
                dismissButton = { TextButton(onClick = { closeTarget = null }) { Text("Keep open") } })
        }
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
