package dev.waddle.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.waddle.android.R
import java.io.File
import dev.waddle.android.terminal.ShellSession
import dev.waddle.android.terminal.TerminalWorkspace
import kotlinx.coroutines.launch

@Composable
fun WaddleScreen(workspace: TerminalWorkspace?, onNewSession: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("terminal") }
    var lastWorkPage by rememberSaveable { mutableStateOf("terminal") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var closeTarget by remember { mutableStateOf<ShellSession?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val drawerVisible = drawer.isOpen || drawer.targetValue == DrawerValue.Open
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(drawerVisible) {
        if (drawerVisible) {
            keyboard?.hide()
            focus.clearFocus(force = true)
        }
    }
    val scope = rememberCoroutineScope()
    val pages = rememberSaveableStateHolder()
    fun navigate(destination: String) {
        page = destination
        if (destination != "files") lastWorkPage = destination
        scope.launch { drawer.close() }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFA7E8BD), onPrimary = Color(0xFF101512),
        background = Color(0xFF101512), surface = Color(0xFF19211C),
        secondaryContainer = Color(0xFF253B2D), onSecondaryContainer = Color(0xFFA7E8BD),
        onBackground = Color(0xFFE4ECE6), onSurface = Color(0xFFE4ECE6),
    )) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = drawerVisible,
                drawerContent = {
                    ModalDrawerSheet(Modifier.widthIn(max = 320.dp).fillMaxHeight(), drawerContainerColor = MaterialTheme.colorScheme.surface) {
                        Text("Waddle", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
                        NavigationDrawerItem(
                            label = { Text("文件") }, selected = page == "files",
                            icon = { Icon(painterResource(R.drawable.ic_folder), null) },
                            onClick = { navigate("files") }, modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("会话", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelLarge)
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            workspace?.sessions?.forEach { session ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NavigationDrawerItem(
                                        label = { Column {
                                            Text(session.label)
                                            Text(if (session.running) "终端 · 运行中" else "终端 · 已退出", fontSize = 11.sp)
                                            Text(session.cwd ?: "正在读取路径…", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                                        } },
                                        selected = page == "terminal" && workspace.selected === session,
                                        onClick = { workspace.select(session); navigate("terminal") },
                                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                                    )
                                    TextButton(onClick = { closeTarget = session }, modifier = Modifier.size(48.dp)
                                        .semantics { contentDescription = "关闭 ${session.label}" }, contentPadding = PaddingValues(0.dp)) { Text("×", fontSize = 22.sp) }
                                }
                            }
                            TextButton(onClick = { onNewSession(); navigate("terminal") },
                                enabled = workspace != null && workspace.sessions.size < 8, modifier = Modifier.padding(horizontal = 12.dp)) { Text("＋ 新建终端") }

                        }
                        HorizontalDivider()
                        TextButton(onClick = { scope.launch { drawer.close() }; settings = true }, enabled = workspace != null,
                            modifier = Modifier.fillMaxWidth()) { Text("设置与运行环境") }
                        TextButton(onClick = { scope.launch { drawer.close() }; help = true }, modifier = Modifier.fillMaxWidth()) { Text("帮助") }
                    }
                },
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Icon(painterResource(R.drawable.ic_menu), "打开导航抽屉")
                            }
                            // Files is the first fixed destination, available even while a session is active.
                            IconButton(onClick = { navigate("files") }) {
                                Icon(painterResource(R.drawable.ic_folder), "打开文件", tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(when (page) { "files" -> "文件"; else -> workspace?.selected?.let { session -> session.label + (session.cwd?.let { " · " + java.io.File(it).name.ifEmpty { "/" } } ?: "") } ?: "终端" },
                                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(when (page) {
                                    "files" -> "选择位置，打开文件夹"
                                    else -> workspace?.selected?.cwd ?: "正在连接工作区…"
                                }, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        Box(Modifier.weight(1f)) {
                            // Preserve file navigation while shells stay in the service.
                            pages.SaveableStateProvider(page) {
                                when (page) {
                                    "files" -> if (workspace != null) FileBrowserScreen(workspace,
                                        onTerminal = { navigate("terminal") }, navigationEnabled = !drawerVisible)
                                        else Text("正在连接工作区…", Modifier.padding(24.dp))
                                    else -> TerminalScreen(workspace, onNewSession, onAiSettings = { settings = true })
                                }
                            }
                        }
                    }
                    if (!drawerVisible) Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(16.dp)
                        .pointerInput(drawer) {
                            var distance = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { distance = 0f },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    distance += amount
                                    if (distance > 48.dp.toPx()) {
                                        distance = Float.NEGATIVE_INFINITY
                                        scope.launch { drawer.open() }
                                    }
                                },
                            )
                        })
                }
            }
            // Register after child handlers so Back closes the drawer before moving the file browser up.
            BackHandler(enabled = !drawerVisible && page == "files" && workspace?.files?.selected == null && workspace?.files?.showingLocations == true) {
                navigate(lastWorkPage)
            }
            BackHandler(enabled = drawerVisible) { scope.launch { drawer.close() } }
        }
        workspace?.files?.selected?.let { entry ->
            key(entry.uri) {
                FileViewer(entry, onClose = { workspace.files.dismissSelection() }, onImport = if (entry.local) null else ({ workspace.files.dismissSelection(); workspace.files.import(entry); navigate("files") }))
            }
        }
        closeTarget?.let { target ->
            AlertDialog(onDismissRequest = { closeTarget = null }, title = { Text("关闭 ${target.label}？") },
                text = { Text(if (target.running) "该终端仍在运行。关闭会停止其中的命令并移除输出。" else "移除这个已退出的终端及输出。") },
                confirmButton = { TextButton(onClick = { workspace?.close(target); closeTarget = null }) { Text("关闭终端") } },
                dismissButton = { TextButton(onClick = { closeTarget = null }) { Text("保留") } })
        }
        if (settings && workspace != null) AiSettingsDialog(workspace.suggestions.settings) { settings = false }
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("使用 Waddle") }, text = {
            Text("左侧文件夹按钮打开文件；菜单切换终端。在文件夹底部点“打开终端”，或用卡片“在此打开…”新建终端，原会话保留。\n\n可运行 pwd、ls、bash、git、rg、curl。用 waddle-open \"文件路径\" 打开图片或文件，返回保留原终端。长按复制，双指缩放；Ctrl 作用于下一个按键，^C 中断命令，Paste 预览粘贴，Keys 打开键盘。\n\n在设置的“AI 模型”中配置服务商和 Key。底部“问 AI，写命令”使用当前路径和最近输入输出；命令可编辑，确认后只在原会话执行一次。命令出错后点“让 AI 看看”解释报错。\n\n不配置 AI 也能使用终端。系统结束应用或手机重启后 Shell 不会恢复，本机文件仍保留。\n\n终端组件：Termux（GPLv3；上游 Apache-2.0 部分）。")
        }, confirmButton = { TextButton(onClick = { help = false }) { Text("知道了") } })
    }
}
