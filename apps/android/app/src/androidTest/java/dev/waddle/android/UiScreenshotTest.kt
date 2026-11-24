package dev.waddle.android

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexMessage
import dev.waddle.android.codex.CodexApproval
import dev.waddle.android.terminal.TerminalWorkspace
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Opt-in visual acceptance capture. Only disposable emulator data; no account or network request. */
class UiScreenshotTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun nodes(): List<AccessibilityNodeInfo> {
        fun visit(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> { if(n==null) return emptyList(); n.refresh(); return listOf(n)+(0 until n.childCount).flatMap { visit(n.getChild(it)) } }
        return visit(inst.uiAutomation.rootInActiveWindow)
    }
    private fun waitFor(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 20000
        while (!check()) { check(SystemClock.uptimeMillis() < end) { nodes().map { it.text ?: it.contentDescription }.toString() }; SystemClock.sleep(100) }
    }
    private fun visible(s: String) = nodes().any { it.text?.toString() == s || it.contentDescription?.toString() == s }
    private fun settle() { inst.waitForIdleSync(); inst.uiAutomation.waitForIdle(350, 5000) }
    private fun click(s: String) {
        settle()
        waitFor {
            var n = nodes().firstOrNull { it.text?.toString() == s || it.contentDescription?.toString() == s }
            var clicked = false
            while(n != null && !clicked) {
                if(n.isClickable && n.isEnabled) clicked=n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                n=n.parent
            }
            clicked
        }
        settle()
    }
    private fun openNavigation() { if(inst.targetContext.resources.configuration.screenWidthDp < 840) click("打开导航抽屉") else waitFor { visible("会话") } }
    private fun closeNavigation() {
        if(inst.targetContext.resources.configuration.screenWidthDp < 840) {
            waitFor { visible("Close navigation menu") }
            click("Close navigation menu")
            waitFor { !visible("会话") }
        }
    }
    private fun back() { inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle() }
    private fun dismissClipboardOverlay() {
        val info=inst.uiAutomation.serviceInfo
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        inst.uiAutomation.serviceInfo=info
        fun visit(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> { if(n==null) return emptyList(); n.refresh(); return listOf(n)+(0 until n.childCount).flatMap { visit(n.getChild(it)) } }
        inst.uiAutomation.windows.flatMap { visit(it.root) }.firstOrNull {
            it.packageName?.toString()=="com.android.systemui" &&
                (it.viewIdResourceName?.endsWith("/dismiss_button")==true || it.contentDescription?.toString()?.lowercase() in listOf("dismiss","close","关闭"))
        }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    private fun shot(name: String) {
        waitFor { nodes().any { it.packageName?.toString() == "dev.waddle.android" } }
        settle(); SystemClock.sleep(400)
        val args = InstrumentationRegistry.getArguments()
        val dir = File(inst.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "2026-11-23-02-${args.getString("waddleScreenshots")}-${args.getString("waddleSize", "phone")}-${name}.png").outputStream().use {
            check(inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
    @Test fun captureProductSurfaces() {
        assumeTrue(InstrumentationRegistry.getArguments().containsKey("waddleScreenshots"))
        assumeTrue(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var workspace: TerminalWorkspace? = null
            waitFor { scenario.onActivity { workspace = it.workspace }; workspace != null }
            val w = workspace!!
            val demo = File(w.runtime.home, "demo").apply { mkdirs() }
            File(demo, "README.md").writeText("# Waddle demo\n\nA local terminal workspace.\n")
            File(demo, "src").mkdirs()
            File(demo, "notes.txt").writeText("Sketch. Build. Ship.\n")
            scenario.onActivity { w.closeAll(); w.create(demo) }
            waitFor { w.selected?.promptReady == true }
            scenario.onActivity { w.selected!!.terminal.write("printf 'Waddle workspace\\n'; ls\r") }
            dismissClipboardOverlay(); settle(); shot("terminal")
            openNavigation(); shot("drawer"); closeNavigation()
            click("打开文件"); scenario.onActivity { w.files.showLocations() }; shot("locations")
            scenario.onActivity { w.files.openLocal(demo) }; waitFor { !w.files.loading }; shot("files")
            if(visible("切换网格")) { click("切换网格"); shot("file-grid"); click("切换列表") }
            click("文件夹 src 的操作"); shot("folder-actions"); click("取消")
            click("文件选项"); shot("file-menu"); click("输入路径"); shot("path-dialog"); click("取消")
            click("文件选项"); click("手机存储授权"); shot("storage-permission"); click("暂不授权")
            click("README.md"); shot("viewer"); click("返回")
            scenario.onActivity { w.files.openGrant(dev.waddle.android.files.GrantedFolder(android.net.Uri.parse("content://dev.waddle.android.test.files/tree/root"), "项目 中文", false)) }
            waitFor { !w.files.loading }; click("复制到 Waddle…"); shot("import-confirmation"); click("取消")
            scenario.onActivity { w.files.openLocal(demo) }; waitFor { !w.files.loading }
            openNavigation(); click("设置与运行环境"); shot("settings")
            // The redesign separates settings from accounts; the old screen already is accounts.
            val separateAccounts = visible("打开账号页")
            if (separateAccounts) click("打开账号页")
            shot("accounts")
            click("AI 模型 · 配置 Key 和模型"); shot("models"); click("完成")
            if (BuildConfig.BUNDLED_AGENTS) { click("管理 Codex 账号与界面"); shot("codex-account"); click("返回账号") }
            click("完成")
            if (separateAccounts) { waitFor { visible("打开账号页") }; click("完成") }
            openNavigation(); click("帮助"); shot("help"); click("知道了")
            openNavigation(); click(w.selected!!.label)
            waitFor { visible("问 AI，写命令") || visible("✦ 问 AI，写命令…") }
            click(if (visible("问 AI，写命令")) "问 AI，写命令" else "✦ 问 AI，写命令…"); shot("command-sheet"); back(); if (visible("生成命令")) back()
            scenario.onActivity { w.selected!!.terminal.write("false\r") }; waitFor { visible("退出码 1 · 让 AI 看看") }
            click("退出码 1 · 让 AI 看看"); shot("diagnosis-sheet"); back(); if (visible("分析报错")) back()
            if (w.agents != null) {
                scenario.onActivity { w.agents.preference("ask"); w.selected!!.terminal.write("codex\r") }
                waitFor { visible("用哪种界面打开 Codex？") }; shot("interface-choice"); click("打开")
                waitFor { w.agents.selected?.client?.connected == true }; shot("chat-empty")
                val client = w.agents.selected!!.client
                scenario.onActivity {
                    client.messages.addAll(listOf(CodexMessage("visual-user", "你", "检查项目，并更新 README。"),
                        CodexMessage("visual-command", "命令", "ls src\n\n已完成\nmain.kt"),
                        CodexMessage("visual-diff", "文件修改", "README.md\n- A local terminal workspace.\n+ A focused terminal workspace."),
                        CodexMessage("visual-agent", "Codex", "已检查项目，以上为演示内容。")))
                }
                shot("chat-messages")
                scenario.onActivity { client.approvals.add(CodexApproval("visual", "允许执行命令？", "工作目录：~/demo\n命令：git status")) }; shot("approval")
                scenario.onActivity { client.approvals.clear() }
                click("历史对话"); shot("history"); click("关闭")
                click("结束对话"); shot("end-conversation"); click("保留")
                click("回到终端"); shot("terminal-conversation")
                openNavigation(); shot("drawer-conversation"); closeNavigation()
            }
            openNavigation(); click("关闭 ${w.selected!!.label}"); shot("close-terminal"); click("保留"); closeNavigation()
            scenario.onActivity { (it.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("demo", "pwd\nls")) }
            repeat(3) { if(!visible("Paste")) { nodes().filter { it.isScrollable }.forEach { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }; settle() } }
            click("Paste"); shot("paste"); click(if(visible("Cancel")) "Cancel" else "取消")
            nodes().filter { it.isScrollable }.forEach { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }; settle()
            dismissClipboardOverlay()

        }
    }
}
