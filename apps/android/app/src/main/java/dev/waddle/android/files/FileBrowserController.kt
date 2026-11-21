package dev.waddle.android.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class GrantedFolder(val uri: Uri, val name: String, val persistent: Boolean)

class FileBrowserController(private val context: Context, val home: File) {
    private val access = FileAccess(context)
    val phoneStorage = PhoneStorage(context)
    var storageAllowed by mutableStateOf(phoneStorage.allowed()); private set
    var storageLocations by mutableStateOf(phoneStorage.locations()); private set
    var showingLocations by mutableStateOf(true); private set
    fun showLocations() { dismissSelection(); clearNotice(); showingLocations = true }
    fun refreshStorage(force: Boolean = false) {
        val allowed = phoneStorage.allowed()
        val locations = phoneStorage.locations()
        val changed = allowed != storageAllowed || locations != storageLocations
        storageAllowed = allowed
        storageLocations = locations
        if (!changed && !force) return
        if (!showingLocations && current?.local == false && storageAllowed) {
            scope.launch {
                val entry = current ?: return@launch
                val direct = withContext(Dispatchers.IO) { access.directDirectory(entry) }
                if (current == entry && !showingLocations && direct != null) openLocal(direct)
            }
        } else if (!showingLocations) refresh()
    }
    fun locationLabel(): String {
        val folder = current ?: return "文件"
        if (!folder.local) return "其他来源 · 原文件"
        val file = folder.file.canonicalFile
        return when {
            file.toPath().startsWith(File(home, "Imports").canonicalFile.toPath()) -> "Waddle 文件 · 副本"
            file.toPath().startsWith(home.canonicalFile.toPath()) -> "Waddle 文件"
            storageLocations.any { file.toPath().startsWith(it.root.canonicalFile.toPath()) } -> "手机存储 · 原文件"
            else -> "本机路径"
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences("file_browser", Context.MODE_PRIVATE)
    private var listing: Job? = null
    private var previewJob: Job? = null
    private var importing: Job? = null
    private var importVersion = 0
    private var listingVersion = 0
    private var previewVersion = 0
    val grants = mutableStateListOf<GrantedFolder>()
    var path by mutableStateOf<List<FileEntry>>(emptyList()); private set
    var entries by mutableStateOf<List<FileEntry>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var selected by mutableStateOf<FileEntry?>(null); private set
    var preview by mutableStateOf<FilePreview?>(null); private set
    var previewLoading by mutableStateOf(false); private set
    var previewError by mutableStateOf<String?>(null); private set
    var progress by mutableStateOf<ImportProgress?>(null); private set
    var importBusy by mutableStateOf(false); private set
    var cancelling by mutableStateOf(false); private set
    var imported by mutableStateOf<FileEntry?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var showHidden by mutableStateOf(false)
    val current get() = path.lastOrNull()

    init {
        runCatching {
            val saved = JSONArray(prefs.getString("folders", "[]"))
            for (i in 0 until saved.length()) {
                val item = saved.getJSONObject(i)
                grants += GrantedFolder(Uri.parse(item.getString("uri")), item.getString("name"), true)
            }
        }
        openLocal(home)
        showingLocations = true
    }

    private fun message(e: Throwable) = when (e) {
        is SecurityException -> if (e.message?.contains("手机存储") == true) e.message!! else "访问权限已失效，请重新选择文件或授权文件夹。"
        is java.nio.file.AccessDeniedException -> "没有权限直接访问此目录，请使用手机文件夹或选择手机文件入口授权。"
        is java.nio.file.NoSuchFileException -> "文件或目录已不存在，请刷新后重新选择。"
        else -> e.message ?: "无法访问，请检查文件、授权和存储设备是否可用。"
    }
    fun clearNotice() { notice = null; imported = null }
    fun report(text: String) { imported = null; notice = text }

    fun openLocal(file: File) = load {
        val target = access.local(file.canonicalFile)
        check(target.directory) { "请选择一个文件夹。" }
        val base = if (target.file.toPath().startsWith(home.canonicalFile.toPath())) home.canonicalFile
            else storageLocations.map { it.root.canonicalFile }.filter { target.file.toPath().startsWith(it.toPath()) }.maxByOrNull { it.path.length } ?: File("/")
        val chain = mutableListOf(target)
        var parent = target.file
        while (parent != base) { parent = parent.parentFile ?: break; chain.add(0, access.local(parent)) }
        chain
    }
    fun openGrant(grant: GrantedFolder) = load {
        val doc = DocumentsContract.buildDocumentUriUsingTree(grant.uri, DocumentsContract.getTreeDocumentId(grant.uri))
        val entry = access.document(doc)
        val direct = access.directDirectory(entry)
        listOf(if (direct != null) access.local(direct) else entry)
    }
    fun enter(entry: FileEntry) {
        if (entry.directory) { val destination = path + entry; load { destination } } else select(entry)
    }
    fun goTo(index: Int) { if (index in path.indices) { val destination = path.take(index + 1); load { destination } } }
    fun up() { if (path.size > 1) goTo(path.lastIndex - 1) }
    fun refresh() { val destination = path; if (destination.isNotEmpty()) load { destination } }

    private fun load(destination: () -> List<FileEntry>) {
        showingLocations = false
        clearNotice()
        listing?.cancel()
        val version = ++listingVersion
        dismissSelection()
        loading = true; error = null; entries = emptyList()
        listing = scope.launch {
            try {
                val chain = withContext(Dispatchers.IO) { destination() }
                if (version == listingVersion) path = chain
                val children = withContext(Dispatchers.IO) { access.children(chain.last()) }
                if (version == listingVersion) entries = children
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (version == listingVersion) error = message(e) }
            finally { if (version == listingVersion) loading = false }
        }
    }

    fun authorize(uri: Uri) {
        scope.launch {
            try {
                val grant = withContext(Dispatchers.IO) {
                    val entry = access.document(DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)))
                    val persistent = runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
                    GrantedFolder(uri, entry.name, persistent)
                }
                grants.removeAll { it.uri == uri }; grants += grant; saveGrants()
                if (!grant.persistent) notice = "此来源只提供临时访问，重开应用后需要重新选择。"
                openGrant(grant)
            } catch (e: Exception) { notice = message(e) }
        }
    }
    fun forget(grant: GrantedFolder) {
        if (importBusy) { notice = "请先完成或取消导入。"; return }
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.releasePersistableUriPermission(grant.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            grants.remove(grant); saveGrants()
            dismissSelection(); openLocal(home)
            notice = "已移除文件夹入口；之前导入的副本仍保留在 Waddle。"
        }
    }
    private fun saveGrants() {
        val saved = JSONArray()
        grants.filter { it.persistent }.forEach { saved.put(JSONObject().put("uri", it.uri.toString()).put("name", it.name)) }
        prefs.edit().putString("folders", saved.toString()).apply()
    }

    fun chooseFile(uri: Uri) {
        scope.launch {
            try { select(withContext(Dispatchers.IO) { access.document(uri) }) }
            catch (e: Exception) { notice = message(e) }
        }
    }
    fun select(entry: FileEntry) {
        dismissSelection()
        selected = entry
        if (entry.directory || entry.viewKind() !in setOf(ViewKind.TEXT, ViewKind.MARKDOWN, ViewKind.HTML)) return
        previewLoading = true
        val version = previewVersion
        previewJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { access.preview(entry) }
                if (version == previewVersion) preview = result
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (version == previewVersion) previewError = message(e) }
            finally { if (version == previewVersion) previewLoading = false }
        }
    }
    fun openPath(file: File, onDirectory: () -> Unit = {}) {
        scope.launch {
            try {
                val entry = withContext(Dispatchers.IO) { access.local(file) }
                if (entry.directory) { openLocal(file); onDirectory() } else select(entry)
            } catch (e: Exception) {
                // Keep the failed file visible, so retry and external-open remain available.
                select(FileEntry(Uri.fromFile(file.absoluteFile), file.name, false, null,
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"))
                previewError = fileError(e)
            }
        }
    }

    fun dismissSelection() {
        previewVersion++; previewJob?.cancel()
        selected = null; preview = null; previewError = null; previewLoading = false
    }

    fun import(entry: FileEntry) {
        if (importBusy) return
        importBusy = true; cancelling = false; imported = null; notice = null
        progress = ImportProgress(entry.name, 0, 0)
        val version = ++importVersion
        importing = scope.launch {
            var published: File? = null
            try {
                val jobContext = currentCoroutineContext()
                var lastUpdate = 0L
                val result = withContext(Dispatchers.IO) {
                    access.import(entry, home, { jobContext.ensureActive() }) { update ->
                        val now = SystemClock.uptimeMillis()
                        if (now - lastUpdate > 100) {
                            lastUpdate = now
                            handler.post { if (importBusy && version == importVersion) progress = update }
                        }
                    }.also { published = it }
                }
                val copy = access.local(result)
                openLocal(if (copy.directory) result else result.parentFile!!)
                imported = copy
                notice = "已进入 Waddle 副本 · 修改不会写回原文件"
                dismissSelection()
            } catch (_: CancellationException) {
                // Cancellation can arrive after the atomic rename but before withContext returns.
                withContext(NonCancellable) {
                    val removed = withContext(Dispatchers.IO) { published?.parentFile?.deleteRecursively() ?: true }
                    notice = if (removed) "导入已取消，未完成的副本已清理。" else "导入已停止，但未能清理副本：${published?.parent}"
                }
            } catch (e: Exception) { notice = "导入失败：${message(e)}" }
            finally { importBusy = false; cancelling = false; progress = null }
        }
    }
    fun cancelImport() { if (importBusy) { cancelling = true; importing?.cancel() } }
    fun close() { scope.cancel(); handler.removeCallbacksAndMessages(null) }
}
