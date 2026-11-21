package dev.waddle.android.codex

import android.content.Context
import android.system.Os
import org.json.JSONObject
import java.io.File

/** Shared, app-owned environment for PTY shells, CLI Codex and the Codex panel. */
class WaddleRuntime(private val context: Context) {
    val home = File(context.filesDir, "home").apply { mkdirs() }
    val config = File(home, ".codex").apply { mkdirs() }
    val prefix = File(context.filesDir, "usr")
    val bin = File(prefix, "bin")
    private val data = File(prefix, "share/waddle")
    private val tmp = File(context.cacheDir, "shell").apply { mkdirs() }
    private val native = File(context.applicationInfo.nativeLibraryDir)
    private var process: Process? = null

    companion object { private val installLock = Any() }

    fun prepare() = synchronized(installLock) {
        val manifestText = context.assets.open("runtime/manifest.json").bufferedReader().use { it.readText() }
        val manifest = JSONObject(manifestText)
        val marker = File(data, ".manifest")
        if (!marker.exists() || marker.readText() != manifestText) {
            data.mkdirs()
            val files = manifest.getJSONArray("files")
            for (index in 0 until files.length()) {
                val path = files.getString(index)
                require(!path.startsWith('/') && path.split('/').none { it == ".." })
                val target = File(data, path)
                target.parentFile!!.mkdirs()
                context.assets.open("runtime/$path").use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            marker.writeText(manifestText)
        }
        bin.mkdirs()
        val commands = manifest.getJSONObject("commands")
        for (name in commands.keys()) {
            val source = File(native, commands.getString(name))
            check(source.canExecute()) { "内嵌运行时文件缺失：$name" }
            val link = File(bin, name)
            val current = runCatching { Os.readlink(link.path) }.getOrNull()
            if (current != source.path) {
                check(!link.exists() || link.isFile) { "运行时命令路径冲突：$name" }
                link.delete()
                Os.symlink(source.path, link.path)
            }
        }
    }

    fun environment(): Map<String, String> = mapOf(
        "HOME" to home.path, "CODEX_HOME" to config.path, "TMPDIR" to tmp.path,
        "PATH" to "${bin.path}:/system/bin", "SHELL" to File(bin, "bash").path,
        "LD_LIBRARY_PATH" to native.path, "PREFIX" to prefix.path,
        "SSL_CERT_FILE" to File(data, "cert.pem").path,
        "CURL_CA_BUNDLE" to File(data, "cert.pem").path,
        "GIT_SSL_CAINFO" to File(data, "cert.pem").path,
        "OPENSSL_CONF" to "/dev/null", "GIT_EXEC_PATH" to bin.path,
        "GIT_TEMPLATE_DIR" to File(data, "git-template").path,
        "GIT_PAGER" to "cat", "PAGER" to "cat",
        "TERMINFO" to File(data, "terminfo").path,
        "TERM" to "xterm-256color", "COLORTERM" to "truecolor", "LANG" to "C.UTF-8",
        "ANDROID_ROOT" to "/system", "ANDROID_DATA" to "/data",
    )

    fun command(vararg args: String): ProcessBuilder {
        prepare()
        return ProcessBuilder(*args).directory(home).apply { environment().putAll(this@WaddleRuntime.environment()) }
    }

    fun executable(name: String) = File(bin, name).path

    @Synchronized fun start(suggestionsOnly: Boolean = false, workingDirectory: File = home): Process {
        stop()
        val options = if (suggestionsOnly) listOf(
            "features.shell_tool=false", "features.code_mode=false", "features.code_mode_only=false",
            "features.apps=false", "features.plugins=false", "features.hooks=false",
            "features.multi_agent=false", "features.multi_agent_v2=false", "agents.enabled=false",
            "web_search=\"disabled\"", "tools.update_plan.enabled=false", "tools.experimental_request_user_input.enabled=false",
            "project_doc_max_bytes=0",
        ).flatMap { listOf("-c", it) } else emptyList()
        val builder = command(*(listOf(File(native, "libwaddle_supervisor.so").path, executable("codex")) +
            options + listOf("app-server", "--listen", "stdio://")).toTypedArray())
        builder.directory(CodexController.checkedDirectory(workingDirectory))
        if (suggestionsOnly) builder.directory(File(context.cacheDir, "command-suggestions").apply { mkdirs() })
        val started = builder.start()
        process = started
        return started
    }

    @Synchronized fun stop(): Thread? {
        val running = process ?: return null
        process = null
        running.destroy()
        return Thread {
            if (!running.waitFor(9, java.util.concurrent.TimeUnit.SECONDS)) {
                running.destroyForcibly()
                running.waitFor()
            }
        }.apply { start() }
    }
}
