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
import dev.waddle.android.codex.CodexWorkspace
import dev.waddle.android.codex.CodexSession
import java.io.File
import dev.waddle.android.terminal.ShellSession
import dev.waddle.android.terminal.TerminalWorkspace
import kotlinx.coroutines.launch

@Composable
fun WaddleScreen(workspace: TerminalWorkspace?, codexWorkspace: CodexWorkspace?, onNewSession: () -> Unit, onConnectCodex: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("terminal") }
    var lastWorkPage by rememberSaveable { mutableStateOf("terminal") }
    val codexSession = codexWorkspace?.selected
    val codex = codexSession?.client
    var newCodexDirectory by rememberSaveable { mutableStateOf<String?>(null) }
    var newCodexError by remember { mutableStateOf<String?>(null) }
    var closeCodex by remember { mutableStateOf<CodexSession?>(null) }
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
    fun openCodex(directory: File): Boolean = runCatching {
        checkNotNull(codexWorkspace) { "工作区尚未连接。" }.create(directory)
    }.fold(onSuccess = { navigate("codex"); onConnectCodex(); true },
        onFailure = { newCodexError = it.message; false })
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
                            codexWorkspace?.sessions?.forEach { session ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NavigationDrawerItem(label = { Column {
                                        Text(session.label)
                                        Text(when {
                                            session.client.approvals.isNotEmpty() -> "等待审批"
                                            session.client.busy -> "正在处理"
                                            else -> session.client.status
                                        }, fontSize = 11.sp)
                                        Text(session.client.displayDirectory, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
                                    } }, selected = page == "codex" && codexSession === session,
                                        onClick = { codexWorkspace.select(session); navigate("codex") }, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                    TextButton(onClick = { closeCodex = session }, modifier = Modifier.size(48.dp)
                                        .semantics { contentDescription = "关闭 ${session.label}" }, contentPadding = PaddingValues(0.dp)) { Text("×", fontSize = 22.sp) }
                                }
                            }
                            TextButton(onClick = { onNewSession(); navigate("terminal") },
                                enabled = workspace != null && workspace.sessions.size < 8, modifier = Modifier.padding(horizontal = 12.dp)) { Text("＋ 新建终端") }
                            TextButton(onClick = {
                                newCodexDirectory = workspace?.files?.home?.path.orEmpty(); newCodexError = null
                                scope.launch { drawer.close() }
                            }, enabled = codexWorkspace != null, modifier = Modifier.padding(horizontal = 12.dp)) { Text("＋ 新建 Codex") }
                        }
                        HorizontalDivider()
                        TextButton(onClick = { scope.launch { drawer.close() }; settings = true }, enabled = codex != null,
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
                                Text(when (page) { "files" -> "文件"; "codex" -> codexSession?.let { "${it.label} · ${it.client.workingDirectory.name.ifEmpty { "/" }}" } ?: "Codex"; else -> workspace?.selected?.let { session -> session.label + (session.cwd?.let { " · " + java.io.File(it).name.ifEmpty { "/" } } ?: "") } ?: "终端" },
                                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(when (page) {
                                    "files" -> "选择位置，打开文件夹"
                                    "codex" -> codex?.let { "${it.status} · ${it.displayDirectory}" } ?: "从文件夹或抽屉新建 Codex"
                                    else -> workspace?.selected?.cwd ?: "正在连接工作区…"
                                }, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        Box(Modifier.weight(1f)) {
                            // Save drafts/scroll state when a page leaves composition. Shells and Codex stay in the service.
                            pages.SaveableStateProvider(if (page == "codex") "codex-${codexSession?.id}" else page) {
                                when (page) {
                                    "files" -> if (workspace != null) FileBrowserScreen(workspace,
                                        onTerminal = { navigate("terminal") }, onCodex = { file ->
                                            val target = codexWorkspace?.selected ?: codexWorkspace?.create()
                                            target?.pendingFile = file.path; navigate("codex")
                                        }, onOpenCodex = { directory ->
                                            if (!openCodex(directory)) workspace.files.report(newCodexError ?: "无法打开 Codex。")
                                        }, navigationEnabled = !drawerVisible)
                                        else Text("正在连接工作区…", Modifier.padding(24.dp))
                                    "codex" -> if (codex != null) CodexScreen(codex, onConnectCodex, codexSession.pendingFile,
                                        clearSelectedFile = { codexSession.pendingFile = null },
                                        onNewConversation = { newCodexDirectory = codex.workingDirectory.path; newCodexError = null })
                                        else Text("从文件夹或左侧抽屉新建 Codex 对话。", Modifier.padding(24.dp))
                                    else -> TerminalScreen(workspace, onNewSession)
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
        closeTarget?.let { target ->
            AlertDialog(onDismissRequest = { closeTarget = null }, title = { Text("关闭 ${target.label}？") },
                text = { Text(if (target.running) "该终端仍在运行。关闭会停止其中的命令并移除输出。" else "移除这个已退出的终端及输出。") },
                confirmButton = { TextButton(onClick = { workspace?.close(target); closeTarget = null }) { Text("关闭终端") } },
                dismissButton = { TextButton(onClick = { closeTarget = null }) { Text("保留") } })
        }
        closeCodex?.let { target ->
            AlertDialog(onDismissRequest = { closeCodex = null }, title = { Text("关闭 ${target.label}？") },
                text = { Text("关闭会停止这个对话的任务及待审批请求。其他会话、文件和登录不受影响，已保存的对话记录仍留在本机。") },
                confirmButton = { TextButton(onClick = { codexWorkspace?.close(target); closeCodex = null }) { Text("关闭对话") } },
                dismissButton = { TextButton(onClick = { closeCodex = null }) { Text("保留") } })
        }
        newCodexDirectory?.let { directory ->
            AlertDialog(onDismissRequest = { newCodexDirectory = null }, title = { Text("新建 Codex 对话") }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("从这个目录开始，原对话保留。")
                    OutlinedTextField(directory, { newCodexDirectory = it; newCodexError = null }, label = { Text("工作目录完整路径") })
                    TextButton(onClick = { newCodexDirectory = workspace?.files?.home?.path.orEmpty(); newCodexError = null }) { Text("默认工作区") }
                    TextButton(onClick = { newCodexDirectory = workspace?.selected?.cwd; newCodexError = null }, enabled = workspace?.selected?.cwd != null) { Text("当前终端目录") }
                    newCodexError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }, confirmButton = { TextButton(onClick = { if (openCodex(File(directory))) newCodexDirectory = null }) { Text("打开 Codex") } },
                dismissButton = { TextButton(onClick = { newCodexDirectory = null }) { Text("取消") } })
        }
        if (settings && codex != null) CodexSettingsDialog(codex) { settings = false }
        if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("使用 Waddle") }, text = {
            Text("左侧文件夹按钮打开文件；菜单切换终端和 Codex。浏览文件不会改变终端路径，进入文件夹后点底部“打开终端”，或用卡片“在此打开…”菜单，新终端会从该目录开始，原会话保留。“在此打开 Codex”同样新建独立对话，从所选目录读写文件和执行命令。Codex 最多同时打开 4 个对话，可在抽屉切换或关闭。\n\n终端可运行 pwd、ls、codex。长按复制，双指缩放；Ctrl 作用于下一个按键，^C 中断命令，Paste 预览粘贴，Keys 打开键盘。\n\n底部“问 Codex，写命令”使用当前终端上下文，确认命令后回车执行。\n\n会话在后台服务存活期间保留；系统结束应用或手机重启后 Shell 不会恢复。文件和 Codex 登录保存在本机。\n\n版本 0.4.0 · 终端组件：Termux（GPLv3；上游 Apache-2.0 部分）。")
        }, confirmButton = { TextButton(onClick = { help = false }) { Text("知道了") } })
    }
}
