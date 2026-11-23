package dev.waddle.android.codex

import android.util.Base64
import dev.waddle.android.files.FileReferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Local, bounded extraction of image tool outputs. No URL fetch and no automatic export to phone storage. */
internal object CodexFiles {
    data class Result(val paths: List<String>, val error: String?)
    fun read(item: JSONObject, cwd: File, home: File, storage: File): Result {
        val paths = linkedSetOf<String>()
        var error: String? = null
        fun path(value: String) { FileReferences.resolve(value, cwd, home)?.let { paths += it.path } }
        fun image(raw: String, mime: String = "image/png") {
            if (raw.isBlank()) return
            if (raw.startsWith('/') || raw.startsWith("file:") || raw.startsWith("sandbox:")) { path(raw); return }
            if (raw.startsWith("http:" ) || raw.startsWith("https:")) { error = "此图片只有在线地址，请让 Codex 保存为本机文件后查看。"; return }
            try {
                check(raw.length <= 32 * 1024 * 1024) { "工具图片超过预览限制，请让 Codex 保存为本机文件。" }
                val data = if (raw.startsWith("data:")) raw.substringAfter("base64,", "") else raw
                check(data.isNotBlank()) { "图片数据无效。" }
                val bytes = Base64.decode(data, Base64.DEFAULT)
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val ext = when { mime.contains("jpeg") || raw.startsWith("data:image/jpeg") -> "jpg"; mime.contains("gif") -> "gif"; mime.contains("webp") -> "webp"; else -> "png" }
                storage.mkdirs()
                val file = File(storage, "$hash.$ext")
                if (!file.exists()) {
                    val temp = File.createTempFile("image-", ".tmp", storage)
                    try { temp.writeBytes(bytes); check(temp.renameTo(file)) { "无法保存工具图片。" } } finally { temp.delete() }
                }
                paths += file.path
            } catch (e: Exception) { error = dev.waddle.android.files.fileError(e) }
        }
        fun visit(value: Any?, depth: Int = 0) {
            if (depth > 12 || paths.size >= 24) return
            when (value) {
                is JSONArray -> for (i in 0 until minOf(value.length(), 100)) visit(value.opt(i), depth + 1)
                is JSONObject -> {
                    val type = value.optString("type")
                    if (type in setOf("image", "input_image", "image_url")) {
                        val raw = value.optString("data").ifBlank { value.optString("image_url").ifBlank { value.optString("imageUrl").ifBlank { value.optString("url") } } }
                        image(raw, value.optString("mimeType", "image/png")); return
                    }
                    for (key in value.keys()) {
                        val child = value.opt(key)
                        if (key in setOf("path", "savedPath", "output_path", "image_path") && child is String) path(child)
                        else if (key in setOf("text", "output", "aggregatedOutput") && child is String) {
                            FileReferences.find(child, cwd, home).forEach { paths += it.file.path }
                            if (child.trimStart().startsWith('{') || child.trimStart().startsWith('[')) runCatching { visit(org.json.JSONTokener(child).nextValue(), depth + 1) }
                        } else if (child is JSONArray || child is JSONObject) visit(child, depth + 1)
                    }
                }
            }
        }
        if (item.optString("type") == "imageGeneration") {
            val saved = item.optString("savedPath").takeUnless { it == "null" }.orEmpty()
            if (saved.isNotBlank()) path(saved)
            else if (item.optString("result").isNotBlank()) image(item.getString("result"))
            else if (item.optString("status") == "completed") error = "此生成结果没有本机图片数据，请让 Codex 将图片保存为本机文件。"
        }
        visit(item)
        if (item.optString("type") == "commandExecution") {
            val command = item.optString("command")
            Regex("[\"']([^\"'\n;()]+\\.(?:png|jpe?g|webp|gif|heic|heif))[\"']", RegexOption.IGNORE_CASE).findAll(command).take(24).forEach { path(it.groupValues[1]) }
        }
        return Result(paths.toList().take(24), error)
    }
}
