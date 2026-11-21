package dev.waddle.android.files

import android.net.Uri
import java.io.File

/** Does not require existence: stale references must still open a useful error screen. */
object FileReferences {
    data class Reference(val start: Int, val end: Int, val file: File)
    private val tokens = Regex(
        """!?\[[^]\n]*]\((<[^>\n]+>|[^)\n]+)\)|`([^`\n]+)`|"([^"\n]+)"|'([^'\n]+)'""" +
        """|(?:file://|sandbox:/|~/|/|\./|\.\./)[^\n`<>"'，。；！？()\[\]{}]+?\.[A-Za-z0-9]{1,12}(?=$|[\s，。；！])""" +
        """|(?:file://|sandbox:/|~/|/|\./|\.\./)[^\s`<>"'，。；！？()\[\]{}]+""" +
        """|[\p{L}\p{N}_-][\p{L}\p{N}_./-]*\.(?:png|jpg|jpeg|webp|gif|heic|pdf|md|html|txt|json|mp4|mp3|wav)(?=$|[\s，。；！])"""
    )
    fun resolve(raw: String, cwd: File, home: File, explicitLink: Boolean = false): File? {
        var value = raw.trim().removeSurrounding("<", ">")
        if (value.startsWith("sandbox:")) value = value.removePrefix("sandbox:")
        if (value.startsWith("file://")) {
            val uri = Uri.parse(value)
            if (!uri.host.isNullOrEmpty() && uri.host != "localhost") return null
            value = uri.path ?: return null
        } else {
            if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value) || value.startsWith("#")) return null
            // Uri.decode preserves literal '+' in local filenames.
            value = Uri.decode(value)
        }
        value = value.replace(Regex("(?::\\d+(?::\\d+)?|#L\\d+(?:-L?\\d+)?)$"), "")
        if (value.isBlank() || value.any { it < ' ' } || value.length > 4096) return null
        // Quoted shell bodies (for example bash -lc 'printf ... > image.png')
        // are not filenames just because the command ends in an image extension.
        // Explicit Markdown links can still name files containing these characters.
        if (!explicitLink && Regex("""(?:^|\s)(?:&&|\|\||[|;<>]|[0-2]?>{1,2}|<{1,2})(?:\s|$)""").containsMatchIn(value)) return null
        return when {
            value == "~" -> home
            value.startsWith("~/") -> File(home, value.drop(2))
            value.startsWith('/') -> File(value)
            value.startsWith("./") || value.startsWith("../") || value.contains('/') || Regex(".*\\.[A-Za-z0-9]{1,12}$").matches(value) -> File(cwd, value)
            explicitLink -> File(cwd, value)
            else -> null
        }?.absoluteFile
    }
    fun find(text: String, cwd: File, home: File): List<Reference> = tokens.findAll(text).mapNotNull { match ->
        if (match.range.first > 0 && text[match.range.first - 1] == ':') return@mapNotNull null
        val raw = (1..4).firstNotNullOfOrNull { match.groups[it]?.value } ?: match.value.trimEnd('.', ',', ';', ':', '，', '。')
        resolve(raw, cwd, home, match.groups[1] != null)?.let { Reference(match.range.first, match.range.last + 1, it) }
    }.take(64).distinctBy { Triple(it.start, it.end, it.file.path) }.toList()
}
