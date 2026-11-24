package dev.waddle.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

internal val WorkspaceColor = Color(0xFF101216)
internal val SurfaceColor = Color(0xFF181B21)
internal val AccentColor = Color(0xFF8BAAFF)
internal val MutedColor = Color(0xFF9DA3AF)

@Composable
fun WaddleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = AccentColor, onPrimary = WorkspaceColor,
        primaryContainer = Color(0xFF263652), onPrimaryContainer = AccentColor,
        secondary = MutedColor, onSecondary = WorkspaceColor,
        secondaryContainer = Color(0xFF263652), onSecondaryContainer = AccentColor,
        tertiary = MutedColor, background = WorkspaceColor, surface = SurfaceColor,
        surfaceVariant = Color(0xFF22262E), onSurfaceVariant = MutedColor,
        onBackground = Color(0xFFE6E8ED), onSurface = Color(0xFFE6E8ED),
        outline = Color(0xFF565D6B), outlineVariant = Color(0xFF30343D),
        error = Color(0xFFF1A5A5), surfaceTint = Color.Transparent,
        surfaceDim = WorkspaceColor, surfaceBright = Color(0xFF30343D),
        surfaceContainerLowest = WorkspaceColor, surfaceContainerLow = SurfaceColor,
        surfaceContainer = SurfaceColor, surfaceContainerHigh = Color(0xFF22262E), surfaceContainerHighest = Color(0xFF30343D),
    ), shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(12.dp)),
        typography = Typography(
            titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        ), content = content)
}

internal fun shortPath(path: String?, home: File): String {
    if (path == null) return ""
    for (root in listOf(home.absolutePath, home.canonicalPath)) {
        if(path == root) return "~"
        if(path.startsWith("$root/")) return "~" + path.removePrefix(root)
    }
    return path
}


internal fun shortPathsInText(text: String, home: File): String {
    var display = text
    for(root in listOf(home.canonicalPath, home.absolutePath).distinct()) {
        display = Regex(Regex.escape(root) + "(?=/|$|[\\s\"'`<>\\)\\]])").replace(display, "~")
    }
    return display
}

@Composable
internal fun QuietButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                         shape: Shape = MaterialTheme.shapes.small,
                         colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                         contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                         content: @Composable RowScope.() -> Unit) {
    TextButton(onClick, modifier, enabled, shape, colors, contentPadding = contentPadding, content = content)
}

@Composable
internal fun PrimaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick, modifier, enabled, shape = MaterialTheme.shapes.small, content = content)
}

@Composable
internal fun SecondaryButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick, modifier, enabled, shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.onSurface), content = content)
}

/** One 24dp outline icon family. All strokes use the same 1.8dp weight. */
@Composable
internal fun WaddleIcon(name: String, description: String? = null, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        val u = size.width / 24f
        val stroke = 1.8f * u
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x*u,y*u), Offset(x2*u,y2*u), stroke)
        fun box(x: Float,y: Float,w: Float,h: Float) = drawRoundRect(tint, Offset(x*u,y*u), Size(w*u,h*u), CornerRadius(2*u), style = Stroke(stroke))
        when (name) {
            "menu" -> { line(4f,6f,20f,6f); line(4f,12f,20f,12f); line(4f,18f,20f,18f) }
            "folder" -> { val p=Path().apply { moveTo(3*u,7*u); lineTo(3*u,5*u); lineTo(10*u,5*u); lineTo(12*u,8*u); lineTo(21*u,8*u); lineTo(21*u,20*u); lineTo(3*u,20*u); close() }; drawPath(p,tint,style=Stroke(stroke)) }
            "file" -> { box(5f,3f,14f,18f); line(8f,9f,16f,9f); line(8f,13f,16f,13f); line(8f,17f,13f,17f) }
            "terminal" -> { box(2f,4f,20f,16f); line(6f,9f,9f,12f); line(9f,12f,6f,15f); line(12f,15f,17f,15f) }
            "chat" -> { box(3f,3f,18f,14f); line(6f,17f,6f,22f); line(6f,22f,12f,17f); line(7f,8f,17f,8f); line(7f,12f,14f,12f) }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            "back" -> { line(14f,5f,7f,12f); line(7f,12f,14f,19f); line(7f,12f,21f,12f) }
            "plus" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
            "history" -> { drawCircle(tint,8*u,Offset(12*u,12*u),style=Stroke(stroke)); line(12f,7f,12f,12f); line(12f,12f,16f,14f) }
            "account" -> { drawCircle(tint,3.5f*u,Offset(12*u,7*u),style=Stroke(stroke)); drawArc(tint,180f,180f,false,Offset(4*u,13*u),Size(16*u,14*u),style=Stroke(stroke)) }
            "settings" -> { for (y in listOf(6f,12f,18f)) line(3f,y,21f,y); for ((x,y) in listOf(8f to 6f,16f to 12f,9f to 18f)) { drawCircle(SurfaceColor,2.5f*u,Offset(x*u,y*u)); drawCircle(tint,2.5f*u,Offset(x*u,y*u),style=Stroke(stroke)) } }
            "help" -> { drawCircle(tint,9*u,Offset(12*u,12*u),style=Stroke(stroke)); line(12f,10f,12f,17f); drawCircle(tint,1*u,Offset(12*u,7*u)) }
            "grid" -> { for(x in listOf(4f,14f)) for(y in listOf(4f,14f)) box(x,y,6f,6f) }
            "list" -> { for(y in listOf(6f,12f,18f)) { line(8f,y,21f,y); line(3f,y,4f,y) } }
            "up" -> { line(5f,14f,12f,7f); line(12f,7f,19f,14f) }
            else -> { for(y in listOf(5f,12f,19f)) drawCircle(tint,1.3f*u,Offset(12*u,y*u)) }
        }
    }
}

@Composable
internal fun ToolIcon(name: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier=Modifier.semantics { contentDescription=label }) { WaddleIcon(name, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MutedColor.copy(alpha=.4f)) }
}

@Composable
internal fun FullPage(title: String, onClose: () -> Unit, closeLabel: String = "完成", content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(end=8.dp), verticalAlignment=Alignment.CenterVertically) {
                    ToolIcon("back", "返回$title", onClick=onClose)
                    Text(title, Modifier.weight(1f), style=MaterialTheme.typography.titleLarge)
                    QuietButton(onClick=onClose) { Text(closeLabel) }
                }
                HorizontalDivider()
                Column(Modifier.widthIn(max=760.dp).fillMaxWidth().weight(1f).align(Alignment.CenterHorizontally), content=content)
            }
        }
    }
}

@Composable
internal fun ServiceRow(name: String, status: String, action: String, icon: String = "account", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=16.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        WaddleIcon(icon)
        Column(Modifier.weight(1f)) { Text(name); Text(status, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
        QuietButton(onClick=onClick, modifier=Modifier.semantics { contentDescription=action }) { Text(when(action) { "管理 Codex 账号与界面" -> "管理"; "打开账号页" -> "打开"; else -> action }) }
    }
    HorizontalDivider(Modifier.padding(start=56.dp, end=16.dp))
}
