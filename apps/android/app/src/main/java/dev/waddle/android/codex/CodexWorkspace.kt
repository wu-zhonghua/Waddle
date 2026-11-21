package dev.waddle.android.codex

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Each panel owns its process, thread, approvals and cwd. Only files and login are shared. */
class CodexSession(val id: String, val number: Int, val client: CodexController) {
    val label get() = if (number == 1) "Codex" else "Codex $number"
    var pendingFile by mutableStateOf<String?>(null)
}

class CodexWorkspace(private val context: Context) {
    private val prefs = context.getSharedPreferences("codex_panels", Context.MODE_PRIVATE)
    private val home = File(context.filesDir, "home").apply { mkdirs() }
    val sessions = mutableStateListOf<CodexSession>()
    var selectedId by mutableStateOf(prefs.getString("selected", null)); private set
    val selected get() = sessions.firstOrNull { it.id == selectedId } ?: sessions.firstOrNull()
    private var sequence = prefs.getInt("sequence", 0)

    init {
        val saved = runCatching { JSONArray(prefs.getString("sessions", "[]")) }.getOrDefault(JSONArray())
        for (index in 0 until minOf(saved.length(), MAX_SESSIONS)) {
            runCatching {
                val value = saved.getJSONObject(index)
                val number = value.getInt("number")
                val id = value.getString("id")
                require(number > 0 && (id == "legacy" || UUID.fromString(id).toString() == id))
                require(sessions.none { it.id == id || it.number == number })
                // Keep unavailable folders visible; connecting/resuming will explain the error.
                sessions += panel(id, number, File(value.getString("cwd")))
                sequence = maxOf(sequence, number)
            }
        }
        if (sessions.isEmpty()) {
            sessions += panel("legacy", ++sequence, home)
            selectedId = sessions.first().id
        }
        persist()
    }

    private fun panel(id: String, number: Int, directory: File) = CodexSession(id, number,
        CodexController(context, directory,
            if (id == "legacy") "codex_embedded_ui" else "codex_panel_$id", onDirectoryChanged = ::persist))

    fun create(directory: File = home): CodexSession {
        check(sessions.size < MAX_SESSIONS) { "最多同时打开 $MAX_SESSIONS 个 Codex 对话，请先关闭一个。" }
        val cwd = CodexController.checkedDirectory(directory)
        return panel(UUID.randomUUID().toString(), ++sequence, cwd).also {
            sessions += it; selectedId = it.id; persist()
        }
    }

    fun select(session: CodexSession) {
        if (session in sessions) { selectedId = session.id; persist() }
    }

    fun close(session: CodexSession) {
        if (session !in sessions) return
        session.client.disconnect()
        // Saved Codex rollout files and account credentials are deliberately retained.
        session.client.forgetSavedPanel()
        sessions.remove(session)
        if (selectedId == session.id) selectedId = sessions.lastOrNull()?.id
        persist()
    }

    fun disconnectAll() = sessions.forEach { it.client.disconnect() }
    fun refreshPermissions() = sessions.forEach { it.client.refreshDisplayPermission() }

    private fun persist() {
        val values = JSONArray()
        sessions.forEach { values.put(JSONObject().put("id", it.id).put("number", it.number).put("cwd", it.client.workingDirectory.path)) }
        prefs.edit().putString("sessions", values.toString()).putString("selected", selectedId).putInt("sequence", sequence).apply()
    }

    companion object { const val MAX_SESSIONS = 4 }
}
