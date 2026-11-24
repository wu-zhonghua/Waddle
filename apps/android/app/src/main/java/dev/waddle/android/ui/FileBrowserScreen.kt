package dev.waddle.android.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.waddle.android.files.FileBrowserController
import dev.waddle.android.files.FileEntry
import dev.waddle.android.terminal.TerminalWorkspace
import java.io.File

@Composable
fun FileBrowserScreen(workspace: TerminalWorkspace, onTerminal: () -> Unit, navigationEnabled: Boolean = true) {
    val browser = workspace.files
    val context = LocalContext.current
    var pathInput by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var storageHelp by rememberSaveable { mutableStateOf(false) }
    var importFolder by remember { mutableStateOf<FileEntry?>(null) }
    var folderActions by remember { mutableStateOf<FileEntry?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(browser::authorize) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(browser::chooseFile) }
    val storageSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { browser.refreshStorage() }
    val current = browser.current
    BackHandler(enabled = navigationEnabled && (browser.selected != null || !browser.showingLocations)) {
        when {
            browser.selected != null -> browser.dismissSelection()
            browser.path.size > 1 -> browser.up()
            else -> browser.showLocations()
        }
    }
    fun terminal(file: File, newSession: Boolean) {
        runCatching {
            if (newSession || workspace.selected == null) workspace.create(file)
            else workspace.changeDirectory(file).getOrThrow()
        }.fold(onSuccess = { onTerminal() }, onFailure = { browser.report(it.message ?: "无法打开终端。") })
    }
    fun pathLabel(index: Int, entry: FileEntry): String = when {
        index != 0 -> entry.name
        entry.local && entry.file.canonicalFile == browser.home.canonicalFile -> "~"
        entry.local -> browser.storageLocations.firstOrNull { it.root.canonicalFile == entry.file.canonicalFile }?.label ?: entry.name
        else -> entry.name
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (browser.showingLocations) Text("位置", Modifier.weight(1f).padding(8.dp), style = MaterialTheme.typography.titleMedium)
                else {
                    QuietButton(onClick = { browser.showLocations() }) { Text("位置") }
                    val scroll = rememberScrollState()
                    LaunchedEffect(browser.path, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
                    Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                        browser.path.forEachIndexed { index, entry ->
                            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            QuietButton(onClick = { browser.goTo(index) }, enabled = !browser.loading) {
                                Text(pathLabel(index, entry), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                            }
                        }
                    }
                }
                if (!browser.showingLocations) ToolIcon(if(grid) "list" else "grid", if(grid) "切换列表" else "切换网格") { grid=!grid }
                Box {
                    ToolIcon("more", "文件选项") { menu = true }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("输入路径") }, onClick = { menu = false; pathInput = current?.takeIf { it.local }?.file?.path ?: browser.home.path })
                        if (!browser.showingLocations) {
                            DropdownMenuItem(text = { Text("刷新") }, onClick = { menu = false; browser.refreshStorage(force = true) })
                            DropdownMenuItem(text = { Text(if (browser.showHidden) "隐藏隐藏文件" else "显示隐藏文件") }, onClick = { menu = false; browser.showHidden = !browser.showHidden })
                            if (current?.local == true) {
                                DropdownMenuItem(text = { Text("查看完整路径") }, onClick = { menu = false; browser.report(current.file.path) })
                                DropdownMenuItem(text = { Text("切换当前终端到此目录") }, onClick = { menu = false; browser.current?.takeIf { it.local && !browser.loading && browser.error == null }?.let { terminal(it.file, false) } })
                            }
                        }
                        DropdownMenuItem(text = { Text("手机存储授权") }, onClick = { menu = false; storageHelp = true })
                    }
                }
            }
            if (!browser.showingLocations) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(browser.locationLabel(), Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToolIcon("up", "上一级") { if (browser.path.size > 1) browser.up() else browser.showLocations() }
            }
            browser.notice?.let { notice ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(notice, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    QuietButton(onClick = { browser.clearNotice() }) { Text("收起") }
                }
            }
            if (browser.importBusy) Column(Modifier.padding(horizontal = 16.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${if (browser.cancelling) "正在取消" else "正在复制"} · ${browser.progress?.entries ?: 0} 项", Modifier.weight(1f), fontSize = 12.sp)
                    QuietButton(onClick = { browser.cancelImport() }, enabled = !browser.cancelling) { Text("取消导入") }
                }
            }
            if (browser.showingLocations) LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    LocationCard("手机存储", if (browser.storageAllowed) "已授权" else "未授权") {
                        if (browser.storageAllowed) {
                            browser.storageLocations.firstOrNull()?.let { browser.openLocal(it.root) } ?: browser.report("未发现可用的手机存储。")
                        } else storageHelp = true
                    }
                }
                if (browser.storageAllowed) items(browser.storageLocations.filter { it.volumeId != "primary" }, key = { it.volumeId }) { location ->
                    LocationCard(location.label, "存储卡或 USB · 原文件") { browser.openLocal(location.root) }
                }
                item { LocationCard("Waddle 文件", "本机") { browser.openLocal(browser.home) } }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("其他来源", style = MaterialTheme.typography.titleSmall)
                }
                items(browser.grants, key = { it.uri.toString() }) { grant ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { browser.openGrant(grant) }.padding(vertical = 12.dp)) {
                            Text(grant.name)
                            Text("系统授权来源${if (grant.persistent) "" else " · 临时"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        QuietButton(onClick = { browser.forget(grant); browser.showLocations() }, modifier = Modifier.semantics { contentDescription = "移除文件夹入口 ${grant.name}" }) { Text("移除") }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) { Text("添加来源") }
                        QuietButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("选择文件") }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        browser.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        browser.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(browser.error!!, color = MaterialTheme.colorScheme.error)
                            Text("目录不可用时不会自动创建副本。", fontSize = 12.sp)
                            if (current?.local == true && browser.locationLabel().startsWith("手机存储")) QuietButton(onClick = { storageHelp = true }) { Text("检查存储授权") }
                            QuietButton(onClick = { browser.showLocations() }) { Text("返回位置") }
                        }
                        else -> {
                            val shown = browser.entries.filter { browser.showHidden || !it.name.startsWith('.') }
                            if (shown.isEmpty()) Text("文件夹是空的", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (grid) LazyVerticalGrid(GridCells.Adaptive(148.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(shown, key = { it.uri.toString() }) { entry ->
                                    Surface(color=MaterialTheme.colorScheme.surface, shape=MaterialTheme.shapes.small) {
                                        Column {
                                            Column(Modifier.fillMaxWidth().clickable { browser.enter(entry) }.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                                FileGlyph(entry.directory)
                                                Text(entry.name, maxLines=2, overflow=TextOverflow.Ellipsis)
                                                Text(if(entry.directory) "文件夹" else formatSize(entry.size), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if(entry.directory) QuietButton(onClick={ folderActions=entry }, modifier=Modifier.semantics { contentDescription="文件夹 ${entry.name} 的操作" }) { Text("在此打开…") }
                                        }
                                    }
                                }
                            } else LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(horizontal=8.dp)) {
                                items(shown, key={ it.uri.toString() }) { entry ->
                                    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable { browser.enter(entry) }.padding(start=12.dp, end=4.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                        FileGlyph(entry.directory)
                                        Column(Modifier.weight(1f).padding(vertical=12.dp)) {
                                            Text(entry.name, maxLines=2, overflow=TextOverflow.Ellipsis)
                                            Text(if(entry.directory) "文件夹" else formatSize(entry.size), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if(entry.directory) ToolIcon("more", "文件夹 ${entry.name} 的操作") { folderActions=entry }
                                    }
                                    HorizontalDivider(Modifier.padding(start=52.dp, end=8.dp))
                                }
                            }
                        }
                    }
                }
                if (current != null && !browser.loading && browser.error == null) {
                    HorizontalDivider()
                    if (current.local) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(onClick = { browser.current?.takeIf { it.local && !browser.loading && browser.error == null }?.let { terminal(it.file, true) } }, modifier = Modifier.semantics { contentDescription = "在当前文件夹打开终端" }) { WaddleIcon("terminal", tint=MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)); Text("打开终端") }
                    } else Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("此来源需先复制到 Waddle 才能运行命令", Modifier.weight(1f), fontSize = 12.sp)
                        QuietButton(onClick = { importFolder = current }, enabled = !browser.importBusy) { Text("复制到 Waddle…") }
                    }
                }
            }
        }
    }
    if (storageHelp) AlertDialog(onDismissRequest = { storageHelp = false }, title = { Text("直接访问手机存储") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (browser.phoneStorage.supported) "在系统设置中为 Waddle 开启“所有文件访问权限”，即可直接使用下载、文档等手机原文件夹，无需复制。" else "直接访问手机存储需要 Android 11 或更新版本。当前可使用 Waddle 文件或系统选择器来源。")
            Text("授权也适用于 Waddle 中运行的终端。其他 App 的私有数据仍无法访问；可以随时在系统设置中关闭授权。", fontSize = 13.sp)
        }
    }, confirmButton = { if (browser.phoneStorage.supported) PrimaryButton(onClick = {
        storageHelp = false
        runCatching { storageSettings.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) }
            .onFailure { browser.report("无法打开授权页，请从系统设置中打开 Waddle 的所有文件访问权限。") }
    }) { Text("前往系统设置") } }, dismissButton = { QuietButton(onClick = { storageHelp = false }) { Text("暂不授权") } })
    folderActions?.let { entry ->
        AlertDialog(onDismissRequest = { folderActions = null }, title = { Text(entry.name) }, confirmButton = {
            if (entry.local) Row {
                PrimaryButton(onClick = { folderActions = null; terminal(entry.file, true) }, modifier = Modifier.semantics { contentDescription = "在所选文件夹打开终端" }) { Text("打开终端") }
            } else PrimaryButton(onClick = { folderActions = null; importFolder = entry }, modifier = Modifier.semantics { contentDescription = "复制所选文件夹" }) { Text("复制到 Waddle…") }
        }, dismissButton = { QuietButton(onClick = { folderActions = null }) { Text("取消") } })
    }
    importFolder?.let { folder ->
        AlertDialog(onDismissRequest = { importFolder = null }, title = { Text("复制到 Waddle？") }, text = {
            Text("${folder.name}\n\n这个系统来源不能直接作为命令的工作目录。复制完成后进入 Waddle 中的副本；修改不会写回原文件。手机上的普通目录可以从“位置 → 手机存储”直接打开。")
        }, confirmButton = { PrimaryButton(onClick = { importFolder = null; browser.import(folder) }, enabled = !browser.importBusy) { Text("复制并打开") } },
            dismissButton = { QuietButton(onClick = { importFolder = null }) { Text("取消") } })
    }
    pathInput?.let { value ->
        AlertDialog(onDismissRequest = { pathInput = null }, title = { Text("打开本机路径") },
            text = { OutlinedTextField(value = value, onValueChange = { pathInput = it }, singleLine = true, label = { Text("绝对路径或 ~/目录") }) },
            confirmButton = { PrimaryButton(onClick = {
                val input = value.trim()
                val file = when { input == "~" -> browser.home; input.startsWith("~/") -> File(browser.home, input.removePrefix("~/")); else -> File(input) }
                if (!file.isAbsolute) browser.report("请输入绝对路径，或使用 ~/ 开头。") else browser.openLocal(file)
                pathInput = null
            }) { Text("打开") } }, dismissButton = { QuietButton(onClick = { pathInput = null }) { Text("取消") } })
    }

}


@Composable
private fun LocationCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=16.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        WaddleIcon("folder")
        Text(title, Modifier.weight(1f))
        Text(subtitle, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun FileGlyph(folder: Boolean) { WaddleIcon(if(folder) "folder" else "file", if(folder) "文件夹" else "文件") }

internal fun formatSize(size: Long?): String = when {
    size == null -> "大小未知"
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "%.1f KiB".format(size / 1024.0)
    else -> "%.1f MiB".format(size / (1024.0 * 1024))
}
