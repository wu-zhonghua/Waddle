package dev.waddle.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.waddle.android.terminal.TerminalWorkspace

@Composable
internal fun SettingsPage(workspace: TerminalWorkspace, onAccounts: () -> Unit, onModels: () -> Unit, onClose: () -> Unit) {
    FullPage("设置与运行环境", onClose) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("服务")
            ServiceRow("账号", "登录与服务状态", "打开账号页", onClick=onAccounts)
            ServiceRow("AI 模型", workspace.suggestions.settings.selected.label, "配置 Key 和模型", "settings", onModels)
            SectionLabel("终端")
            ListItem(headlineContent={ Text("运行环境") }, supportingContent={ Text("Bash · Git · ripgrep · curl") }, leadingContent={ WaddleIcon("terminal") })
            ListItem(headlineContent={ Text("本机文件") }, supportingContent={ Text("~") }, leadingContent={ WaddleIcon("folder") })
            SectionLabel("关于")
            ListItem(headlineContent={ Text("Waddle ${dev.waddle.android.BuildConfig.VERSION_NAME}") }, supportingContent={ Text("南溟科技 · Nanming Tech") })
            ListItem(headlineContent={ Text("开源许可") }, supportingContent={ Text("GPL-3.0-only · Termux 终端组件（含 Apache-2.0 部分）") })
        }
    }
}

@Composable
internal fun SectionLabel(title: String) {
    Text(title, Modifier.padding(start=16.dp, top=24.dp, bottom=8.dp), style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun HelpPage(onClose: () -> Unit) {
    FullPage("使用 Waddle", onClose, "知道了") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("终端与文件", style=MaterialTheme.typography.titleMedium)
            Text("顶部文件夹打开文件；左侧导航切换会话。在当前文件夹或目录菜单中打开新终端，原会话保持运行。输入 pwd、ls、bash、git、rg、curl 开始使用。")
            HorizontalDivider()
            Text("键盘与手势", style=MaterialTheme.typography.titleMedium)
            Text("长按复制，双指调整字号。Ctrl 作用于下一个按键，^C 中断命令，Paste 预览粘贴，Keys 打开键盘。左边缘滑动打开导航。")
            HorizontalDivider()
            Text("问 AI", style=MaterialTheme.typography.titleMedium)
            Text("在账号页配置模型。终端底部打开“问 AI，写命令”；报错后点“让 AI 看看”。可先检查发送的上下文，生成命令可编辑，确认后只在原终端执行一次。")
            HorizontalDivider()
            Text("预览与保存", style=MaterialTheme.typography.titleMedium)
            Text("用 waddle-open \"文件路径\" 查看图片、PDF、音视频和文档。文件操作菜单可分享、保存到手机或在其他应用打开。云来源须先确认复制到 Waddle，修改不会同步回原文件。")
            HorizontalDivider()
            Text("会话与数据", style=MaterialTheme.typography.titleMedium)
            Text("切换应用或展开屏幕不会主动重建会话。系统结束进程或手机重启后，运行中的 Shell 不会恢复；本机文件保留。不配置 AI 也能完整使用终端。通知允许时可从通知管理会话。")
            Text("终端组件：Termux（GPLv3；含上游 Apache-2.0 部分）。", style=MaterialTheme.typography.bodySmall)
        }
    }
}
