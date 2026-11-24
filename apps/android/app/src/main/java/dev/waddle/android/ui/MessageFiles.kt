package dev.waddle.android.ui

import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.waddle.android.codex.CodexMessage
import dev.waddle.android.files.*
import java.io.File

@Composable
internal fun MessageFiles(message: CodexMessage, cwd: File, home: File, onOpen: (File) -> Unit) {
    val display = shortPathsInText(message.text, home)
    val references = remember(display, cwd) { FileReferences.find(display, cwd, home) }
    val color = MaterialTheme.colorScheme.onSurface
    val text = buildAnnotatedString {
        append(display)
        if(message.role == "文件修改") {
            var offset=0
            display.lines().forEach { line ->
                val lineColor = when { line.startsWith("+") -> AccentColor; line.startsWith("-") -> MaterialTheme.colorScheme.error; else -> color }
                addStyle(SpanStyle(color=lineColor, background=when { line.startsWith("+") -> AccentColor.copy(alpha=.08f); line.startsWith("-") -> MaterialTheme.colorScheme.error.copy(alpha=.08f); else -> androidx.compose.ui.graphics.Color.Transparent }), offset, offset+line.length)
                offset += line.length+1
            }
        }
        references.forEach { ref ->
            addLink(LinkAnnotation.Clickable(ref.file.path, TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))) { onOpen(ref.file) }, ref.start, ref.end)
        }
    }
    SelectionContainer { Text(text, fontSize = 14.sp, fontFamily = if (message.role in listOf("命令", "文件修改", "工具输出")) FontFamily.Monospace else FontFamily.Default) }
    val files = (message.files.map(::File) + references.map { it.file }).distinctBy { it.path }.take(24)
    files.forEach { file ->
        val entry = remember(file.path) { FileEntry(Uri.fromFile(file), file.name, false, null,
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream") }
        if (entry.viewKind() == ViewKind.IMAGE) Column(Modifier.fillMaxWidth().clickable { onOpen(file) }.padding(top = 8.dp)) {
            key(file.path, message.revision) { FileImage(entry, Modifier.fillMaxWidth().height(180.dp), thumbnail = true) }
            Text(file.name, fontSize = 12.sp)
        } else if (file.path in message.files) QuietButton(onClick = { onOpen(file) }) { Text(file.name) }
    }
    message.fileError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
}
