package dev.waddle.android.codex

import android.content.Context
import androidx.compose.runtime.*
import dev.waddle.android.terminal.ShellSession
import java.io.File
import java.util.UUID

class CodexSession(val id: String, val number: Int, val origin: ShellSession, val client: CodexController) {
    val label get() = "${client.workingDirectory.name.takeUnless { it == "home" } ?: "工作区"} · Codex ($number)"
}
class CodexLaunch(val origin: ShellSession, val cwd: File, val request: File, val mode: String)

/** Service-owned conversations. Only the auth directory is shared between processes. */
class CodexWorkspace(private val context: Context) {
    private val prefs = context.getSharedPreferences("agent_interfaces", Context.MODE_PRIVATE)
    val sessions = mutableStateListOf<CodexSession>()
    val launches = mutableStateListOf<CodexLaunch>()
    val account = CodexController(context, preferenceName = "codex_account")
    var selected by mutableStateOf<CodexSession?>(null)
    var openRequest by mutableStateOf<CodexSession?>(null)
    var preferred by mutableStateOf(prefs.getString("codex", "ask")!!); private set
    var error by mutableStateOf<String?>(null)
    private var sequence = 0

    fun preference(mode: String) {
        require(mode in listOf("ask", "chat", "terminal"))
        preferred = mode; prefs.edit().putString("codex", mode).apply()
    }
    fun request(launch: CodexLaunch) {
        val mode = launch.mode.takeIf { it in listOf("chat", "terminal") } ?: preferred
        if (mode == "ask") launches += launch else answer(launch, mode)
    }
    fun answer(launch: CodexLaunch, mode: String, remember: Boolean = false) {
        launches.remove(launch)
        if (!launch.request.exists() || !launch.origin.running) return
        runCatching {
            if (mode == "chat") {
                check(sessions.size < 8) { "最多同时打开 8 个 Codex 对话，请先关闭一个。" }
                val id = UUID.randomUUID().toString()
                val client = CodexController(context, CodexController.checkedDirectory(launch.cwd), "codex_session_$id")
                val session = CodexSession(id, ++sequence, launch.origin, client)
                sessions += session; selected = session; openRequest = session
                client.connect()
            }
            if (remember) preference(mode)
            File(launch.request.path + ".reply").writeText(mode + "\n")
        }.onFailure {
            error = it.message
            File(launch.request.path + ".reply").writeText("cancel\n")
        }
    }
    fun close(session: CodexSession) {
        session.client.disconnect(); session.client.forgetSavedPanel(); sessions.remove(session)
        if (selected === session) selected = null
    }
    fun restoreHistory(origin: CodexSession, threadId: String) {
        sessions.firstOrNull { it.client.threadId == threadId || it.client.lastThreadId == threadId }?.let {
            selected = it; openRequest = it
            return
        }
        runCatching {
            check(sessions.size < 8) { "最多同时打开 8 个 Codex 对话，请先关闭一个。" }
            val id = UUID.randomUUID().toString()
            // The controller reads authoritative cwd metadata before resuming. Keep the
            // source panel and its draft untouched, even when restoration fails.
            val client = CodexController(context, preferenceName = "codex_session_$id", initialThreadId = threadId)
            val session = CodexSession(id, ++sequence, origin.origin, client)
            sessions += session; selected = session; openRequest = session
            client.connect()
        }.onFailure { error = it.message }
    }
    fun refreshAccounts() {
        sessions.forEach { if (it.client.connected) it.client.refreshAccount() }
    }
    fun closeAll() {
        launches.toList().forEach { answer(it, "cancel") }
        sessions.toList().forEach(::close)
        account.disconnect()
    }
}
