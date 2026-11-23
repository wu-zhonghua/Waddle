package dev.waddle.android.codex

import dev.waddle.android.terminal.WaddleRuntime
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*

class CodexMessage(val id: String, val role: String, text: String) {
    var text by mutableStateOf(text)
    var files by mutableStateOf<List<String>>(emptyList())
    var fileError by mutableStateOf<String?>(null)
    var revision by mutableStateOf(0)
}
data class CodexApproval(val id: Any, val title: String, val detail: String)
data class CodexHistoryEntry(val id: String, val title: String, val cwd: String, val updatedAt: Long)

class CodexController(
    context: Context,
    directory: File = File(context.filesDir, "home"),
    private val preferenceName: String = "codex_embedded_ui",
    private val onDirectoryChanged: () -> Unit = {},
    private val startProcess: (() -> Process)? = null,
    private val credentialState: () -> String = {
        File(context.filesDir, "home/.codex/auth.json").let { "${it.lastModified()}:${it.length()}" }
    },
    initialThreadId: String? = null,
) {
    val runtime = WaddleRuntime(context)
    private val fileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imageStorage = File(context.filesDir, "codex-images")
    private val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private var rpc: CodexRpc? = null
    private var authState = ""
    private var generation = 0
    private var turnId: String? = null
    private var loginId: String? = null
    private var startingThread = false
    private var stopRequested = false
    private var interruptedTurn: String? = null
    private var resumeAfterStop = false
    private var resumeAsStopped = false
    private val handler = Handler(Looper.getMainLooper())
    val messages = mutableStateListOf<CodexMessage>()
    val approvals = mutableStateListOf<CodexApproval>()
    val history = mutableStateListOf<CodexHistoryEntry>()
    var historyLoading by mutableStateOf(false); private set
    var historyError by mutableStateOf<String?>(null); private set
    var historyCursor by mutableStateOf<String?>(null); private set
    var connected by mutableStateOf(false); private set
    var connecting by mutableStateOf(false); private set
    var signedIn by mutableStateOf(false); private set
    var accountType by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf("尚未连接"); private set
    var error by mutableStateOf<String?>(null); private set
    var threadId by mutableStateOf<String?>(null); private set
    var lastThreadId by mutableStateOf(initialThreadId ?: prefs.getString("lastThread", null)); private set
    var loginCode by mutableStateOf<String?>(null); private set
    var loginUrl by mutableStateOf<String?>(null); private set
    var workingDirectory by mutableStateOf(directory.canonicalFile); private set
    val displayDirectory: String get() {
        val home = runtime.home.canonicalFile.path
        return when {
            workingDirectory.path == home -> "~"
            workingDirectory.path.startsWith("$home/") -> "~" + workingDirectory.path.removePrefix(home)
            else -> workingDirectory.path
        }
    }
    val configDirectory get() = runtime.config.path

    fun connect(resumeStoppedThread: Boolean = false) {
        if (connecting || connected) return
        disconnect()
        resumeAfterStop = resumeStoppedThread || lastThreadId != null
        resumeAsStopped = resumeStoppedThread
        busy = resumeAfterStop
        connecting = true; status = "正在启动 Waddle 内嵌 Codex…"; error = null
        val current = ++generation
        authState = credentialState()
        // Assets are prepared when the shared terminal workspace starts. Keep ownership
        // changes serialized with connect/disconnect so a stale launch cannot stop a new one.
        runCatching { checkedDirectory(workingDirectory); startProcess?.invoke() ?: runtime.start(workingDirectory = workingDirectory) }.fold(onSuccess = { process ->
            rpc = CodexRpc(process,
                onReady = { if (current == generation) initialize() },
                onMessage = { if (current == generation) receive(it) },
                onDisconnected = { if (current == generation) fail(it) })
            rpc?.connect()
        }, onFailure = { fail("内嵌 Codex 启动失败：${it.message}") })
    }

    private fun initialize() {
        call("initialize", JSONObject().put("clientInfo", JSONObject().put("name", "waddle_android").put("version", "0.6.0"))
            .put("capabilities", JSONObject().put("experimentalApi", true))) { result ->
            if (!result.optString("userAgent").contains("/0.170.0 ")) {
                fail("当前适配 Codex 0.170.0。内嵌运行时版本不匹配，请重新构建或安装 Waddle。"); return@call
            }
            rpc?.notify("initialized")
            connected = true; connecting = false; status = "已连接 · Waddle 本机"
            refreshAccount()
        }
    }

    fun refreshAccount() {
        if (!connected) return
        // app-server caches auth per process. Another panel or the CLI may have
        // logged in/out since this one started. Reopen only idle stale processes.
        val state = credentialState()
        if (state != authState && loginId == null) {
            if (!busy || resumeAfterStop) { disconnect(); connect() }
            return
        }
        call("account/read", JSONObject().put("refreshToken", false)) {
            authState = state
            signedIn = it.optJSONObject("account") != null
            accountType = it.optJSONObject("account")?.optString("type").orEmpty()
            status = if (signedIn) "已就绪 · Waddle 本机" else "已连接，请登录 ChatGPT"
            if (signedIn) { loginCode = null; loginUrl = null; loginId = null }
            if (resumeAfterStop) {
                busy = false
                if (signedIn) { resumeAfterStop = false; resume(stopped = resumeAsStopped) }
            }
        }
    }

    fun login() {
        if (!connected || loginId != null) return
        error = null
        call("account/login/start", JSONObject().put("type", "chatgptDeviceCode")) {
            val url = it.optString("verificationUrl")
            if (url != "https://auth.openai.com/codex/device") { error = "登录服务返回了不支持的地址。"; return@call }
            loginId = it.optString("loginId"); loginCode = it.optString("userCode"); loginUrl = url
            status = "等待浏览器登录"
        }
    }

    fun loginWithKey(key: String) {
        if (!connected || key.isBlank()) return
        call("account/login/start", JSONObject().put("type", "apiKey").put("apiKey", key)) { refreshAccount() }
    }

    fun logout() {
        cancelLogin()
        call("account/logout") { refreshAccount() }
    }

    fun cancelLogin() {
        loginId?.let { call("account/login/cancel", JSONObject().put("loginId", it)) {} }
        loginId = null; loginCode = null; loginUrl = null
    }

    fun send(text: String) {
        if (text.isBlank() || busy || !connected || !signedIn) return
        if (threadId == null && lastThreadId != null) {
            error = "请先恢复这个对话，或在终端重新打开对话。"; return
        }
        if (!validateDirectory()) return
        error = null; busy = true; stopRequested = false; interruptedTurn = null; turnId = null
        messages += CodexMessage("user-${System.nanoTime()}", "你", text)
        if (threadId == null) {
            startingThread = true
            val params = JSONObject().put("cwd", workingDirectory.path)
                .put("approvalPolicy", "untrusted").put("approvalsReviewer", "user").put("sandbox", "read-only")
                .put("dynamicTools", JSONArray())
                .put("developerInstructions", "You run inside Waddle on Android, in the supplied cwd, with shared HOME and login. Available: bash, git, rg, curl. Invoke scripts with bash. Respect approval rejections; never retry rejected actions by another path. Stay within the requested workspace; never read auth files. Answer in the user’s language.")
            call("thread/start", params) {
                startingThread = false
                rememberThread(it.getJSONObject("thread").getString("id"))
                prefs.edit().putInt("toolsVersion", 2).apply()
                if (stopRequested) {
                    busy = false; stopRequested = false; status = "已停止"
                } else startTurn(text)
            }
        } else startTurn(text)
    }

    private fun startTurn(text: String) = call("turn/start", JSONObject().put("threadId", threadId).put("cwd", workingDirectory.path)
        .put("input", JSONArray().put(JSONObject().put("type", "text").put("text", text)))) {
        if (busy) {
            turnId = it.optJSONObject("turn")?.optString("id")
            status = if (stopRequested) "正在停止…" else "Codex 正在处理…"
            interruptWhenReady()
        }
    }

    fun interrupt() {
        if (!busy) return
        stopRequested = true; status = "正在停止…"
        // Resolve outstanding approvals first so the server cannot remain blocked on a client request.
        approvals.toList().forEach { answerApproval(it, false) }
        interruptWhenReady()
    }

    private fun interruptWhenReady() {
        val turn = turnId ?: return
        if (!stopRequested || interruptedTurn == turn) return
        // A stop click may arrive before turn/start responds. Send it once the ID is known.
        interruptedTurn = turn
        call("turn/interrupt", JSONObject().put("threadId", threadId).put("turnId", turn)) {}
        val current = generation
        handler.postDelayed({ if (current == generation && stopRequested) finishRequestedStop() }, 2_000)
    }

    private fun finishRequestedStop() {
        // Android Codex can acknowledge interruption while a shell still runs.
        // Reap this panel's entire process tree, then reopen its saved thread.
        val current = ++generation
        rpc?.close(); rpc = null
        val shutdown = runtime.stop()
        connected = false; connecting = true; busy = true
        stopRequested = false; turnId = null; approvals.clear()
        status = "正在停止本地进程…"
        Thread {
            shutdown?.join()
            handler.post {
                if (current == generation) {
                    connecting = false
                    connect(resumeStoppedThread = true)
                }
            }
        }.start()
    }

    fun newConversation() {
        if (busy) return
        threadId = null; lastThreadId = null; prefs.edit().remove("lastThread").apply(); messages.clear(); approvals.clear()
        error = null
    }

    fun loadHistory(more: Boolean = false) {
        if (!connected || historyLoading || (more && historyCursor == null)) return
        historyLoading = true; historyError = null
        if (!more) { history.clear(); historyCursor = null }
        val params = JSONObject().put("limit", 30).put("sortKey", "updated_at").put("sortDirection", "desc")
            .put("modelProviders", JSONArray())
            .put("sourceKinds", JSONArray(listOf("cli", "vscode", "exec", "appServer", "unknown")))
        if (more) params.put("cursor", historyCursor)
        // A history failure must not reset a running turn's busy/approval state.
        rpc?.request("thread/list", params) { result ->
            result.fold(onSuccess = { response ->
                runCatching {
                    val data = response.getJSONArray("data")
                    val entries = (0 until data.length()).map { index ->
                        val thread = data.getJSONObject(index)
                        val name = if (thread.isNull("name")) "" else thread.optString("name")
                        CodexHistoryEntry(thread.getString("id"),
                            (name.takeIf { it.isNotBlank() } ?: thread.optString("preview").takeIf { it.isNotBlank() }
                                ?: "未命名对话").replace(Regex("\\s+"), " ").take(160),
                            thread.getString("cwd"), thread.optLong("updatedAt"))
                    }
                    history.addAll(entries.filter { entry -> history.none { it.id == entry.id } }.distinctBy { it.id })
                    historyCursor = if (response.isNull("nextCursor")) null else response.optString("nextCursor").takeIf { it.isNotBlank() }
                }.onFailure { historyError = "无法读取历史对话，请重试。" }
            }, onFailure = { historyError = "无法加载历史对话：${it.message}" })
            historyLoading = false
        }
    }

    fun resume(stopped: Boolean = false) {
        val previous = lastThreadId ?: return
        if (!connected || !signedIn || busy) return
        busy = true; error = null
        // Read metadata first: never resume an old thread into the currently selected folder.
        call("thread/read", JSONObject().put("threadId", previous).put("includeTurns", false)) read@{ metadata ->
            val directory = runCatching { checkedDirectory(File(metadata.getJSONObject("thread").getString("cwd"))) }
                .getOrElse { error = "无法恢复会话：${it.message}"; busy = false; return@read }
            call("thread/resume", JSONObject().put("threadId", previous).put("cwd", directory.path)
                .put("approvalPolicy", "untrusted").put("approvalsReviewer", "user").put("sandbox", "read-only")) {
                val thread = it.getJSONObject("thread")
                workingDirectory = directory
                rememberThread(thread.getString("id")); onDirectoryChanged(); messages.clear()
                val turns = thread.optJSONArray("turns") ?: JSONArray()
                for (i in 0 until turns.length()) {
                    val turn = turns.getJSONObject(i)
                    val items = turn.optJSONArray("items") ?: JSONArray()
                    for (j in 0 until items.length()) renderItem(items.getJSONObject(j))
                }
                busy = false; status = if (stopped) "已停止" else "已恢复上次会话"
            }
        }
    }

    private fun rememberThread(id: String) {
        threadId = id; lastThreadId = id
        prefs.edit().putString("lastThread", id).apply()
    }

    private fun validateDirectory(): Boolean = runCatching { checkedDirectory(workingDirectory) }.fold(
        onSuccess = { true }, onFailure = { error = it.message; false })

    internal fun forgetSavedPanel() { prefs.edit().clear().apply() }

    companion object {
        fun checkedDirectory(directory: File): File {
            require(directory.isAbsolute) { "请输入本地目录的完整路径。" }
            val canonical = directory.canonicalFile
            require(canonical.isDirectory) { "目录不存在或不是文件夹：${directory.path}" }
            require(canonical.canRead() && canonical.canExecute()) { "没有权限访问目录：${directory.path}" }
            return canonical
        }
    }

    private fun receive(message: JSONObject) {
        val method = message.optString("method")
        val p = message.optJSONObject("params") ?: JSONObject()
        if (message.has("id")) { handleRequest(message.get("id"), method, p); return }
        if (method == "account/login/completed") {
            if (loginId != null && p.optString("loginId") == loginId) {
                if (!p.optBoolean("success")) error = p.optString("error", "登录未完成")
                loginId = null; loginCode = null; loginUrl = null; refreshAccount()
            }
            return
        }
        if (method == "account/updated") { refreshAccount(); return }
        if (method == "error") {
            error = p.optJSONObject("error")?.optString("message") ?: "Codex 返回错误"
            return
        }
        if (p.optString("threadId") != threadId) return
        when (method) {
            "turn/started" -> { busy = true; turnId = p.getJSONObject("turn").getString("id"); interruptWhenReady() }
            "turn/completed" -> {
                if (stopRequested) { finishRequestedStop(); return }
                busy = false; turnId = null; stopRequested = false; interruptedTurn = null; approvals.clear()
                val turn = p.getJSONObject("turn")
                error = turn.optJSONObject("error")?.optString("message")
                status = if (turn.optString("status") == "interrupted") "已停止" else "本轮完成"
            }
            "item/agentMessage/delta" -> {
                val id = p.getString("itemId")
                val item = messages.firstOrNull { it.id == id } ?: CodexMessage(id, "Codex", "").also { messages += it }
                item.text = (item.text + p.optString("delta")).takeLast(100_000)
            }
            "item/started", "item/completed" -> renderItem(p.getJSONObject("item"))
        }
    }

    private fun renderItem(item: JSONObject) {
        val id = item.optString("id"); val type = item.optString("type")
        val role: String; val text: String
        when (type) {
            "agentMessage" -> { role = "Codex"; text = item.optString("text") }
            "userMessage" -> { role = "你"; val content = item.optJSONArray("content") ?: JSONArray()
                text = (0 until content.length()).joinToString("\n") { content.optJSONObject(it)?.optString("text", "") ?: "" } }
            "commandExecution" -> { role = "命令"; text = item.optString("command") + "\n" + item.optString("status") + "\n" + item.optString("aggregatedOutput", "") }
            "imageView" -> { role = "图片"; text = item.optString("path") }
            "imageGeneration" -> { role = "生成图片"; text = "图片生成 · " + item.optString("status") }
            "functionCallOutput" -> { role = "工具输出"; text = item.optString("name", "工具") + "\n" + outputText(item.opt("output")) }
            "mcpToolCall" -> { role = "工具输出"; text = item.optString("tool") + " · " + item.optString("status") + "\n" + outputText(item.optJSONObject("result")?.opt("content")) }
            "fileChange" -> { role = "文件修改"; text = item.optString("status") + "\n" + (item.optJSONArray("changes")?.toString() ?: "") }
            else -> return
        }
        if (text.isBlank()) return
        if (type == "userMessage") messages.removeAll { it.id.startsWith("user-") && it.text == text }
        val existing = messages.firstOrNull { it.id == id }
        if (existing != null) existing.text = text.take(100_000) else messages += CodexMessage(id, role, text.take(100_000))
        val message = messages.first { it.id == id }
        message.revision++
        val revision = message.revision
        val cwd = item.optString("cwd").takeIf { it.startsWith('/') }?.let(::File) ?: workingDirectory
        fileScope.launch {
            val result = withContext(Dispatchers.IO) { CodexFiles.read(item, cwd, runtime.home, imageStorage) }
            if (message in messages && message.revision == revision) { message.files = result.paths; message.fileError = result.error }
        }
        if (messages.size > 300) messages.removeAt(0)
    }

    private fun outputText(value: Any?): String = when (value) {
        is String -> value.take(100_000)
        is JSONArray -> (0 until minOf(value.length(), 100)).mapNotNull { index ->
            val item = value.optJSONObject(index)
            item?.optString("text")?.takeIf { it.isNotBlank() }
        }.joinToString("\n").take(100_000)
        else -> ""
    }

    private fun handleRequest(id: Any, method: String, params: JSONObject) {
        val currentThread = params.optString("threadId") == threadId && threadId != null
        if (stopRequested) {
            when (method) {
                "item/tool/call" -> rpc?.reply(id, JSONObject().put("success", false).put("contentItems",
                    JSONArray().put(JSONObject().put("type", "inputText").put("text", "用户已停止任务，本次工具调用未执行。"))))
                "item/commandExecution/requestApproval", "item/fileChange/requestApproval" -> rpc?.reply(id, JSONObject().put("decision", "decline"))
                else -> rpc?.rejectUnknown(id)
            }
            return
        }
        when (method) {
            "item/commandExecution/requestApproval", "item/fileChange/requestApproval" -> {
                if (!currentThread || !busy || params.optString("turnId") != turnId) { rpc?.reply(id, JSONObject().put("decision", "decline")); return }
                val item = messages.firstOrNull { it.id == params.optString("itemId") }?.text.orEmpty()
                val detail = listOf(params.optString("command", ""), params.optString("reason", ""),
                    params.optString("cwd", ""), params.optString("grantRoot", ""), item).filter { it.isNotBlank() }.joinToString("\n").take(30_000)
                approvals += CodexApproval(id, if (method.contains("commandExecution")) "允许执行命令？" else "允许修改文件？", detail)
            }
            "item/permissions/requestApproval" -> rpc?.reply(id, JSONObject().put("permissions", JSONObject()).put("scope", "turn"))
            else -> { rpc?.rejectUnknown(id); error = "Codex 请求了当前版本不支持的交互：$method，已拒绝。" }
        }
    }

    fun answerApproval(approval: CodexApproval, allow: Boolean) {
        if (!approvals.remove(approval)) return
        rpc?.reply(approval.id, JSONObject().put("decision", if (allow) "accept" else "decline"))
    }

    private fun call(method: String, params: JSONObject = JSONObject(), success: (JSONObject) -> Unit) {
        rpc?.request(method, params) { result ->
            result.fold(onSuccess = { response -> runCatching { success(response) }.onFailure { error = "无法处理服务响应：$method"; busy = false; startingThread = false } },
                onFailure = { error = it.message; busy = false; startingThread = false; if (connecting) fail(it.message ?: "连接失败") })
        }
    }
    private fun fail(message: String) { disconnect(); error = message; status = "连接失败" }
    fun disconnect() {
        generation++; rpc?.close(); rpc = null; runtime.stop()
        connected = false; connecting = false; signedIn = false; busy = false; startingThread = false
        turnId = null; threadId = null; stopRequested = false; interruptedTurn = null
        resumeAfterStop = false; handler.removeCallbacksAndMessages(null); fileScope.coroutineContext.cancelChildren()
        approvals.clear(); loginId = null; loginCode = null; loginUrl = null
        historyLoading = false
        status = "已断开，可重新连接后恢复会话"
    }
}
