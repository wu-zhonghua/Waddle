package dev.waddle.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.waddle.android.terminal.ShellSession
import dev.waddle.android.terminal.TerminalWorkspace
import kotlinx.coroutines.launch

@Composable
fun WaddleScreen(workspace: TerminalWorkspace?, onNewSession: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("terminal") }
    var lastWorkPage by rememberSaveable { mutableStateOf("terminal") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var accounts by rememberSaveable { mutableStateOf(false) }
    var aiSettings by remember { mutableStateOf(false) }
    var rememberInterface by remember { mutableStateOf(false) }
    var chosenInterface by remember { mutableStateOf("chat") }
    var help by rememberSaveable { mutableStateOf(false) }
    var closeTarget by remember { mutableStateOf<ShellSession?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val drawerVisible = drawer.isOpen || drawer.targetValue == DrawerValue.Open
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(drawerVisible) { if (drawerVisible) { keyboard?.hide(); focus.clearFocus(force = true) } }
    val scope = rememberCoroutineScope()
    val pages = rememberSaveableStateHolder()
    fun navigate(destination: String) {
        page = destination
        if (destination != "files") lastWorkPage = destination
        scope.launch { drawer.close() }
    }
    LaunchedEffect(workspace?.agents?.openRequest) {
        workspace?.agents?.openRequest?.let { workspace.agents.selected = it; navigate("codex"); workspace.agents.openRequest = null }
    }
    WaddleTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val expanded = maxWidth >= 840.dp
                LaunchedEffect(expanded) { if (expanded) drawer.snapTo(DrawerValue.Closed) }
                val navigation: @Composable ColumnScope.() -> Unit = {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment=Alignment.CenterVertically) {
                        Text("Waddle", style=MaterialTheme.typography.titleLarge)
                    }
                    NavigationRow("folder", "文件", "", page == "files", { navigate("files") })
                    HorizontalDivider(Modifier.padding(vertical=12.dp, horizontal=16.dp))
                    Text("会话", Modifier.padding(horizontal=20.dp, vertical=8.dp), style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        workspace?.sessions?.forEach { session ->
                            NavigationRow("terminal", session.label,
                                shortPath(session.cwd, workspace.runtime.home) + " · " + if (session.running) "运行中" else "已退出",
                                page == "terminal" && workspace.selected === session,
                                { workspace.select(session); navigate("terminal") }, { closeTarget = session })
                        }
                        workspace?.agents?.sessions?.forEach { session ->
                            NavigationRow("chat", session.label,
                                session.client.displayDirectory + " · " + when { session.client.approvals.isNotEmpty() -> "等待确认"; session.client.busy -> "进行中"; else -> "对话" },
                                page == "codex" && workspace.agents.selected === session,
                                { workspace.agents.selected = session; navigate("codex") })
                        }
                        QuietButton(onClick = { onNewSession(); navigate("terminal") }, enabled=workspace != null && workspace.sessions.size < 8,
                            modifier=Modifier.padding(horizontal=12.dp).fillMaxWidth()) {
                            WaddleIcon("plus"); Spacer(Modifier.width(12.dp)); Text("新建终端")
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal=16.dp))
                    NavigationRow("settings", "设置与运行环境", "", false, { scope.launch { drawer.close() }; settings = true })
                    NavigationRow("help", "帮助", "", false, { scope.launch { drawer.close() }; help = true })
                }
                val body: @Composable () -> Unit = {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().statusBarsPadding()) {
                            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(end=8.dp), verticalAlignment=Alignment.CenterVertically) {
                                if (!expanded) ToolIcon("menu", "打开导航抽屉") { scope.launch { drawer.open() } }
                                ToolIcon("folder", "打开文件") { navigate("files") }
                                Column(Modifier.weight(1f)) {
                                    Text(when(page) { "files" -> "文件"; "codex" -> workspace?.agents?.selected?.label ?: "Codex"; else -> workspace?.selected?.label ?: "终端" },
                                        style=MaterialTheme.typography.titleMedium, maxLines=1, overflow=TextOverflow.Ellipsis)
                                    val path = when(page) { "files" -> ""; "codex" -> workspace?.agents?.selected?.client?.displayDirectory.orEmpty(); else -> workspace?.let { shortPath(it.selected?.cwd, it.runtime.home) }.orEmpty() }
                                    if (path.isNotEmpty()) Text(path, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1, overflow=TextOverflow.Ellipsis)
                                }
                                if (page == "terminal") ToolIcon("plus", "新建终端", workspace != null && workspace.sessions.size < 8) { onNewSession(); navigate("terminal") }
                            }
                            HorizontalDivider()
                            Box(Modifier.weight(1f)) {
                                pages.SaveableStateProvider(if (page == "codex") "codex-${workspace?.agents?.selected?.id}" else page) {
                                    when(page) {
                                        "files" -> if (workspace != null) FileBrowserScreen(workspace, onTerminal={ navigate("terminal") }, navigationEnabled=!drawerVisible || expanded)
                                        "codex" -> workspace?.agents?.selected?.let { session ->
                                            CodexScreen(session.client, onTerminal={
                                                if(session.origin in workspace.sessions) workspace.select(session.origin)
                                                navigate("terminal")
                                            }, onAccounts={ accounts=true }, onClose={ workspace.agents.close(session); navigate("terminal") },
                                                onOpenFile={ workspace.files.openPath(it) }, onHistory={ workspace.agents.restoreHistory(session,it) })
                                        } ?: run { LaunchedEffect(workspace) { if(workspace != null) navigate("terminal") } }
                                        else -> TerminalScreen(workspace,onNewSession,onAiSettings={ if(workspace?.agents != null) accounts=true else aiSettings=true },
                                            onConversation={ workspace?.agents?.selected=it; navigate("codex") })
                                    }
                                }
                            }
                        }
                        if (!expanded && !drawerVisible) Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(16.dp).pointerInput(drawer) {
                            var distance=0f
                            detectHorizontalDragGestures(onDragStart={ distance=0f }, onHorizontalDrag={ change, amount ->
                                change.consume(); distance+=amount
                                if(distance > 48.dp.toPx()) { distance=Float.NEGATIVE_INFINITY; scope.launch { drawer.open() } }
                            })
                        })
                    }
                }
                if (expanded) Row(Modifier.fillMaxSize()) {
                    Surface(Modifier.width(256.dp).fillMaxHeight(), color=MaterialTheme.colorScheme.surface) {
                        Column(Modifier.safeDrawingPadding(), content=navigation)
                    }
                    VerticalDivider()
                    Box(Modifier.weight(1f)) { body() }
                } else ModalNavigationDrawer(drawerState=drawer, gesturesEnabled=drawerVisible, drawerContent={
                    ModalDrawerSheet(Modifier.width(304.dp).fillMaxHeight(), drawerContainerColor=MaterialTheme.colorScheme.surface, content=navigation)
                }, content=body)
                BackHandler(enabled=!drawerVisible && page=="files" && workspace?.files?.selected==null && workspace?.files?.showingLocations==true) { navigate(lastWorkPage) }
                BackHandler(enabled=drawerVisible && !expanded) { scope.launch { drawer.close() } }
            }
        }
        workspace?.files?.selected?.let { entry ->
            key(entry.uri) {
                FileViewer(entry, onClose = { workspace.files.dismissSelection() }, onImport = if (entry.local) null else ({ workspace.files.dismissSelection(); workspace.files.import(entry); navigate("files") }))
            }
        }
        closeTarget?.let { target ->
            AlertDialog(onDismissRequest = { closeTarget = null }, title = { Text("关闭 ${target.label}？") },
                text = { Text(if (target.running) "该终端仍在运行。关闭会停止其中的命令并移除输出。" else "移除这个已退出的终端及输出。") },
                confirmButton = { PrimaryButton(onClick = { workspace?.close(target); closeTarget = null }) { Text("关闭终端") } },
                dismissButton = { QuietButton(onClick = { closeTarget = null }) { Text("保留") } })
        }
        if (settings && workspace != null) SettingsPage(workspace, onAccounts={ accounts=true }, onModels={ aiSettings=true }) { settings=false }
        if (accounts && workspace != null) AccountsDialog(workspace) { accounts=false }
        if (aiSettings && workspace != null) AiSettingsDialog(workspace.suggestions.settings) { aiSettings = false }
        workspace?.agents?.launches?.firstOrNull()?.let { launch ->
            LaunchedEffect(launch) { rememberInterface = false; chosenInterface = "chat" }
            AlertDialog(onDismissRequest = { workspace.agents.answer(launch, "cancel") }, title = { Text("用哪种界面打开 Codex？") }, text = {
                Column {
                    Text(shortPath(launch.cwd.path, workspace.runtime.home))
                    listOf("chat" to "对话界面（推荐）", "terminal" to "终端界面").forEach { (mode, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(chosenInterface == mode, { chosenInterface = mode })
                            QuietButton(onClick = { chosenInterface = mode }) { Text(label) }
                        }
                    }
                    Row(Modifier.toggleable(rememberInterface, onValueChange = { rememberInterface = it }), verticalAlignment = Alignment.CenterVertically) { Checkbox(rememberInterface, null); Text("记住我的选择") }
                }
            }, confirmButton = { PrimaryButton(onClick = { workspace.agents.answer(launch, chosenInterface, rememberInterface) }) { Text("打开") } },
                dismissButton = { QuietButton(onClick = { workspace.agents.answer(launch, "cancel") }) { Text("取消") } })
        }
        workspace?.agents?.error?.let { message ->
            AlertDialog(onDismissRequest = { workspace.agents.error = null }, title = { Text("无法打开对话") }, text = { Text(message) },
                confirmButton = { PrimaryButton(onClick = { workspace.agents.error = null }) { Text("知道了") } })
        }
        BackHandler(enabled = page == "codex" && !drawerVisible && workspace?.files?.selected == null) { navigate("terminal") }
        if (help) HelpPage { help=false }
    }
}

@Composable
private fun NavigationRow(icon: String, title: String, detail: String, selected: Boolean, onClick: () -> Unit, onClose: (() -> Unit)? = null) {
    Surface(color=if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        shape=MaterialTheme.shapes.small, modifier=Modifier.padding(horizontal=8.dp, vertical=2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=52.dp), verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(onClick=onClick).padding(horizontal=12.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                WaddleIcon(icon, tint=if(selected) AccentColor else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(title, color=if(selected) AccentColor else MaterialTheme.colorScheme.onSurface, maxLines=1, overflow=TextOverflow.Ellipsis)
                    if(detail.isNotBlank()) Text(detail, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1, overflow=TextOverflow.Ellipsis)
                }
            }
            if(onClose != null) ToolIcon("close", "关闭 $title", onClick=onClose)
        }
    }
}
