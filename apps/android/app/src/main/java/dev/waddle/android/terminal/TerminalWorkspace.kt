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
import java.util.UUID
import dev.waddle.android.codex.CommandSuggester
import dev.waddle.android.files.FileBrowserController
import dev.waddle.android.codex.WaddleRuntime

class ShellSession(val number: Int, val terminal: TerminalSession, internal val directory: File, internal val nonce: String) {
    var promptReady by mutableStateOf(false); internal set
    var promptEpoch by mutableStateOf(0L); internal set
    var cwd by mutableStateOf<String?>(null); internal set
    var commandError by mutableStateOf<String?>(null); internal set
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
    val runtime = WaddleRuntime(context).apply { prepare() }
    val suggestions = CommandSuggester(context)
    val files = FileBrowserController(context, runtime.home)
    private val shellRoot = File(context.cacheDir, "terminal-sessions").apply { mkdirs() }
    private val home = runtime.home
    private val shellConfig = File(home, ".waddlerc").apply {
        ShellIntegration.prepareConfig(this)
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
                "Bundled commands: bash, git, rg, curl, codex.\n" +
                "The Codex tab shares this home and login with terminal codex.\n" +
                "Run local scripts using bash script.sh or sh script.sh.\n"
        )
    }

    fun create(workingDirectory: File = home): ShellSession {
        check(sessions.size < 8) { "最多同时打开 8 个终端，请先关闭一个。" }
        val cwd = checkedDirectory(workingDirectory)
        val number = nextNumber++
        val nonce = UUID.randomUUID().toString()
        val directory = File(shellRoot, nonce).apply { mkdirs() }
        val rc = File(directory, "init.bash").apply { writeText(ShellIntegration.rc(shellConfig, directory, nonce)) }
        val env = runtime.environment().map { "${it.key}=${it.value}" }.toTypedArray()
        val terminal = TerminalSession(runtime.executable("bash"), cwd.path,
            arrayOf("bash", "--noprofile", "--rcfile", rc.absolutePath, "-i"), env, 4000, this)
        val session = ShellSession(number, terminal, directory, nonce)
        sessions += session
        suggestions.cancel()
        selectedId = terminal.mHandle
        terminal.updateSize(80, 24, 8, 16)
        onStateChanged()
        return session
    }

    private fun checkedDirectory(file: File): File {
        val directory = file.canonicalFile
        check(directory.isDirectory && directory.canRead() && directory.canExecute()) { "目录不存在或没有访问权限：${file.path}" }
        return directory
    }

    fun changeDirectory(directory: File): Result<Unit> = runCatching {
        val target = checkedDirectory(directory)
        val source = captureContext()
        runSuggestion(source, "builtin cd -- ${ShellIntegration.quote(target.path)}").getOrThrow()
    }

    fun select(session: ShellSession) {
        if (selected !== session) suggestions.cancel()
        selectedId = session.terminal.mHandle
    }

    fun close(session: ShellSession) {
        val index = sessions.indexOf(session)
        if (index < 0) return
        if (selected === session) suggestions.cancel()
        session.terminal.finishIfRunning()
        session.directory.deleteRecursively()
        sessions.removeAt(index)
        if (selectedId == session.terminal.mHandle) {
            selectedId = sessions.getOrNull(index.coerceAtMost(sessions.lastIndex))?.terminal?.mHandle
        }
        onStateChanged()
    }

    fun closeAll() {
        suggestions.cancel()
        sessions.toList().forEach { it.terminal.finishIfRunning(); it.directory.deleteRecursively() }
        sessions.clear()
        selectedId = null
        onStateChanged()
    }

    fun captureContext(): TerminalContext {
        val session = selected ?: error("请先打开一个终端会话。")
        check(session.promptReady && session.terminal.isShellForeground && !session.terminal.emulator.isAlternateBufferActive) {
            "请等待当前命令结束，回到 Bash 提示符后再操作，或打开新终端。"
        }
        val cwd = session.terminal.cwd ?: error("暂时无法读取当前路径。")
        check(!cwd.contains('\n') && !cwd.contains('\r')) { "暂不支持路径名中包含换行符的目录。" }
        return TerminalContext(session.terminal.mHandle, session.label, cwd, session.promptEpoch,
            session.terminal.emulator.screen.transcriptText.takeLast(12_000))
    }

    fun contextError(context: TerminalContext): String? {
        val session = selected
        if (session == null || session.terminal.mHandle != context.sessionId) return "会话已经切换或关闭，请重新生成。"
        if (!session.promptReady || !session.terminal.isShellForeground || session.terminal.emulator.isAlternateBufferActive)
            return "终端正在运行命令或等待输入，请回到 Bash 提示符后重新生成。"
        if (session.promptEpoch != context.epoch || session.terminal.cwd != context.cwd)
            return "终端状态或路径已经改变，请重新生成。"
        return null
    }

    fun runSuggestion(context: TerminalContext, command: String): Result<Unit> = runCatching {
        contextError(context)?.let { error(it) }
        require(command.isNotBlank()) { "命令不能为空。" }
        require(command.toByteArray(Charsets.UTF_8).size <= 65_536) { "命令不能超过 64 KiB，请缩短后再运行。" }
        require(command.none { it < ' ' && it != '\n' && it != '\t' || it == '\u007f' }) { "命令包含不支持的控制字符。" }
        val session = selected!!
        val script = File(session.directory, "command-${context.epoch}.bash")
        script.writeText(command + "\n")
        File(session.directory, "pending").writeText("${context.epoch}\n${context.cwd}\nbuiltin source ${ShellIntegration.quote(script.path)}\n")
        session.commandError = null
        session.promptReady = false // consume this draft before any subsequent tap/Enter can submit it
        session.terminal.write(ShellIntegration.RUN_KEYS)
    }

    override fun onShellIntegration(session: TerminalSession, value: String) {
        val shell = sessions.firstOrNull { it.terminal === session } ?: return
        val prefix = "waddle;${shell.nonce};"
        if (!value.startsWith(prefix)) return
        val event = value.removePrefix(prefix)
        when {
            event.startsWith("prompt;") -> {
                val epoch = event.substringAfter(';').toLongOrNull() ?: return
                shell.promptEpoch = epoch
                shell.promptReady = true
                shell.cwd = session.cwd
            }
            event == "running" || event == "continuation" || event == "unavailable" -> shell.promptReady = false
            event == "rejected" -> shell.commandError = "终端状态已经改变，命令未执行。请重新生成。"
        }
        shell.revision++
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
