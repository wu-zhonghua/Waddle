package dev.waddle.android.files

/** Small offline Markdown reader; raw HTML is escaped, never executed. */
object MarkdownPreview {
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun inline(text: String): String = escape(text)
        .replace(Regex("`([^`]+)`"), "<code>$1</code>")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
        .replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
        .replace(Regex("!?\\[([^]]*)]\\([^)]+\\)"), "<u>$1</u>")
    fun html(text: String): String = buildString {
        append("<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font:17px sans-serif;line-height:1.6;padding:16px;color:#e6e8ed;background:#101216;overflow-wrap:anywhere}pre,code{background:#181b21;white-space:pre-wrap}blockquote{border-left:4px solid #565d6b;padding-left:12px}table{border-collapse:collapse}td{border:1px solid #30343d;padding:6px}</style></head><body>")
        var code = false; var table = false
        text.lines().forEach { line ->
            if (line.trimStart().startsWith("```")) {
                if (table) { append("</table>"); table = false }
                append(if (code) "</code></pre>" else "<pre><code>"); code = !code
            } else if (code) append(escape(line) + "\n")
            else {
                val row = line.trim().startsWith('|') && line.trim().endsWith('|')
                if (table && !row) { append("</table>"); table = false }
                if (row) {
                    if (!table) { append("<table>"); table = true }
                    if (!line.matches(Regex("[| :\\-]+"))) append("<tr>" + line.trim().trim('|').split('|').joinToString("") { "<td>${inline(it.trim())}</td>" } + "</tr>")
                } else {
                    val heading = Regex("^(#{1,6}) +(.+)$").matchEntire(line)
                    when {
                        heading != null -> { val n = heading.groupValues[1].length; append("<h$n>${inline(heading.groupValues[2])}</h$n>") }
                        line.startsWith("> ") -> append("<blockquote>${inline(line.drop(2))}</blockquote>")
                        line.matches(Regex("^[-*_]{3,}$")) -> append("<hr>")
                        line.matches(Regex("^\\s*[-*+] .*")) -> append("<div>• ${inline(line.trim().drop(2))}</div>")
                        else -> append("<div>${inline(line).ifEmpty { "<br>" }}</div>")
                    }
                }
            }
        }
        if (code) append("</code></pre>")
        if (table) append("</table>")
        append("</body></html>")
    }
}
