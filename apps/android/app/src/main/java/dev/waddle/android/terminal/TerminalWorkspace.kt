package dev.waddle.android.terminal

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TerminalColors
import java.io.File
import java.util.Properties

class ShellSession(val number: Int, val terminal: TerminalSession) {
    var revision by mutableIntStateOf(0)
    val label get() = "Shell $number"
    val running get(): Boolean { revision; return terminal.isRunning }
    val status get(): String = if (running) "RUNNING" else "EXITED · ${terminal.exitStatus}"
}

class TerminalWorkspace(context: Context, private val onStateChanged: () -> Unit = {}) :
    TerminalLog(), TerminalSessionClient {
    val sessions = mutableStateListOf<ShellSession>()
    var selectedId by mutableStateOf<String?>(null)
        private set
    private var nextNumber = 1
    var requestPaste: (() -> Unit)? = null
    private val home = File(context.filesDir, "home").apply { mkdirs() }
    private val tmp = File(context.cacheDir, "shell").apply { mkdirs() }
    private val shellConfig = File(home, ".waddlerc").apply {
        if (!exists()) writeText("# Waddle interactive shell settings\nPS1='waddle $ '\n")
    }
    val selected get() = sessions.firstOrNull { it.terminal.mHandle == selectedId }

    init {
        TerminalColors.COLOR_SCHEME.updateWith(Properties().apply {
            setProperty("background", "#101512")
            setProperty("foreground", "#e4ece6")
            setProperty("cursor", "#a7e8bd")
        })
        val welcome = File(home, "WELCOME.txt")
        if (!welcome.exists()) welcome.writeText(
            "Waddle — your local Android terminal\n\n" +
                "Try: pwd, ls, uname -a, echo hello, top\n" +
                "Long-press to select text. Use Ctrl + C to interrupt a command.\n\n" +
                "This shell runs with Waddle's app permissions.\n" +
                "A package manager, SSH and Codex are not included in this first version.\n"
        )
    }

    fun create(): ShellSession {
        val number = nextNumber++
        val env = arrayOf(
            "HOME=${home.absolutePath}", "TMPDIR=${tmp.absolutePath}",
            "PATH=/system/bin:/system/xbin", "TERM=xterm-256color", "COLORTERM=truecolor",
            "LANG=C.UTF-8", "SHELL=/system/bin/sh", "ANDROID_ROOT=/system", "ANDROID_DATA=/data",
            "ENV=${shellConfig.absolutePath}", "PS1=waddle $ ",
        )
        val startup = "printf '\\033[1;32mWaddle\\033[0m  /  Android\\n\\nYour workspace is ready.\\nTry: cat WELCOME.txt\\n\\n'; exec /system/bin/sh -i"
        val terminal = TerminalSession("/system/bin/sh", home.absolutePath, arrayOf("sh", "-c", startup), env, 4000, this)
        val session = ShellSession(number, terminal)
        sessions += session
        selectedId = terminal.mHandle
        terminal.updateSize(80, 24, 8, 16)
        onStateChanged()
        return session
    }

    fun select(session: ShellSession) { selectedId = session.terminal.mHandle }

    fun close(session: ShellSession) {
        val index = sessions.indexOf(session)
        if (index < 0) return
        session.terminal.finishIfRunning()
        sessions.removeAt(index)
        if (selectedId == session.terminal.mHandle) {
            selectedId = sessions.getOrNull(index.coerceAtMost(sessions.lastIndex))?.terminal?.mHandle
        }
        onStateChanged()
    }

    fun closeAll() {
        sessions.toList().forEach { it.terminal.finishIfRunning() }
        sessions.clear()
        selectedId = null
        onStateChanged()
    }

    override fun onTextChanged(changedSession: TerminalSession) {
        sessions.firstOrNull { it.terminal === changedSession }?.let { it.revision++ }
    }
    override fun onTitleChanged(changedSession: TerminalSession) = onTextChanged(changedSession)
    override fun onSessionFinished(finishedSession: TerminalSession) {
        onTextChanged(finishedSession)
        onStateChanged()
    }
    override fun onColorsChanged(session: TerminalSession) = onTextChanged(session)
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) = Unit
    override fun getTerminalCursorStyle(): Int = 0
    override fun onBell(session: TerminalSession) = Unit
    // Shell escape sequences must not silently read or replace the user's clipboard.
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
    override fun onPasteTextFromClipboard(session: TerminalSession?) { requestPaste?.invoke() }
}
