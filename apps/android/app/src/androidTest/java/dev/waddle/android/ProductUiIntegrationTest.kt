package dev.waddle.android

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexController
import dev.waddle.android.codex.CodexMessage
import dev.waddle.android.codex.CodexSession
import dev.waddle.android.terminal.TerminalWorkspace
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Behavioral coverage for the redesign: navigation, presentation switching, and live resize. */
class ProductUiIntegrationTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T { val v=AtomicReference<T>(); inst.runOnMainSync { v.set(block()) }; return v.get() }
    private fun nodes(): List<AccessibilityNodeInfo> {
        fun visit(n: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> { if(n==null) return emptyList(); n.refresh(); return listOf(n)+(0 until n.childCount).flatMap { visit(n.getChild(it)) } }
        return visit(inst.uiAutomation.rootInActiveWindow)
    }
    private fun visible(s: String) = nodes().any { it.text?.toString()==s || it.contentDescription?.toString()==s }
    private fun await(label: String, check: () -> Boolean) {
        val until=SystemClock.uptimeMillis()+20000
        while(SystemClock.uptimeMillis()<until) { if(check()) return; SystemClock.sleep(100) }
        fail(label + "; UI=" + nodes().mapNotNull { it.contentDescription ?: it.text?.take(100) }.take(65))
    }
    private fun click(s: String) {
        inst.waitForIdleSync(); inst.uiAutomation.waitForIdle(250,5000)
        await("Missing action $s") {
            var node=nodes().firstOrNull { it.text?.toString()==s || it.contentDescription?.toString()==s }
            var clicked=false
            while(node!=null && !clicked) {
                if(node.isClickable && node.isEnabled) clicked=node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node=node.parent
            }
            clicked
        }
        inst.waitForIdleSync(); inst.uiAutomation.waitForIdle(250,5000)
    }
    private fun workspace(s: ActivityScenario<MainActivity>): TerminalWorkspace {
        var w: TerminalWorkspace?=null
        await("Workspace missing") { s.onActivity { w=it.workspace }; w!=null }
        return w!!
    }
    private fun shell(command: String): String = inst.uiAutomation.executeShellCommand(command).use { fd ->
        java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText()
    }

    @Test fun filesStartAsListAndGridRetainsDirectoryAndTerminal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val w=workspace(scenario)
            val origin=main { w.selected!! }
            val directory=File(w.runtime.home,"layout-${System.nanoTime()}").apply { mkdirs() }
            File(directory,"中文 ' folder").mkdir()
            File(directory,"notes.txt").writeText("layout fixture")
            try {
                click("打开文件"); main { w.files.openLocal(directory) }
                await("Directory loaded") { main { !w.files.loading } && visible("切换网格") && visible("notes.txt") }
                click("切换网格"); await("Grid missing") { visible("切换列表") && visible("notes.txt") }
                scenario.recreate(); await("Grid preference lost") { visible("切换列表") }
                click("切换列表"); await("List missing") { visible("切换网格") }
                assertEquals(directory.canonicalFile,main { w.files.current!!.file })
                assertSame(origin,main { w.selected })
            } finally { main { w.files.openLocal(w.runtime.home) }; directory.deleteRecursively() }
        }
    }

    @Test fun accountsAreFullPageAndEachProviderHasConfiguration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            workspace(scenario)
            click("打开导航抽屉"); click("设置与运行环境"); click("打开账号页")
            await("Account page missing") { visible("返回账号") }
            for(provider in listOf("DeepSeek","通义千问","Kimi","智谱")) assertTrue(provider,visible(provider))
            val text=nodes().mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }.joinToString(" ")
            if(!BuildConfig.BUNDLED_AGENTS) for(agent in listOf("Codex","Claude","Antigravity")) assertFalse(text.contains(agent))
            assertFalse(text.contains("✦"))
            click("AI 模型 · 配置 Key 和模型"); await("Model page missing") { visible("返回AI 模型") }
            click("完成"); await("Returned to accounts") { visible("返回账号") }; click("完成")
            await("Returned to settings") { visible("打开账号页") }; click("完成")
        }
    }

    @Test fun foldResizeKeepsPtyMessagesAndUnsentDraft() {
        assumeTrue(android.os.Build.HARDWARE in listOf("ranchu","goldfish"))
        val oldSize=shell("wm size").lineSequence().firstOrNull { it.startsWith("Override size:") }?.substringAfter(": ") ?: "reset"
        val oldDensity=shell("wm density").lineSequence().firstOrNull { it.startsWith("Override density:") }?.substringAfter(": ") ?: "reset"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val w=workspace(scenario)
            val previous=main { w.selected }
            val directory=File(w.runtime.home,"fold-${System.nanoTime()}").apply { mkdirs() }
            val terminal=main { w.create(directory) }
            val handle=terminal.terminal.mHandle
            var panel: CodexSession?=null
            try {
                await("Shell ready") { main { terminal.promptReady } }
                main { terminal.terminal.write("printf '__FOLD_CONTENT__\\n'\r") }
                await("Shell output") { main { terminal.terminal.emulator.screen.transcriptText.contains("__FOLD_CONTENT__") } }
                w.agents?.let { agents ->
                    panel=main {
                        val client=CodexController(inst.targetContext,directory,"fold-visual-fixture")
                        client.messages+=CodexMessage("fold-message","Codex","折叠布局测试消息")
                        CodexSession("fold-panel",99,terminal,client).also { agents.sessions+=it; agents.selected=it; agents.openRequest=it }
                    }
                    await("Chat shown") { visible("折叠布局测试消息") }
                    val field=nodes().first { it.className?.toString()=="android.widget.EditText" }
                    assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"保留未发送草稿") }))
                }
                if(panel!=null) await("Draft entered") { main { panel!!.client.draft == "保留未发送草稿" } }
                for((size,density) in listOf("1968x2184" to 360,"1080x2520" to 420,"1968x2184" to 360)) {
                    shell("wm size $size"); shell("wm density $density")
                    val expanded=density==360
                    await("Responsive navigation") { visible("会话")==expanded && visible("打开导航抽屉")!=expanded }
                    if(panel!=null) await("Messages/draft lost after resize") { visible("折叠布局测试消息") && visible("保留未发送草稿") }
                    assertEquals(handle,main { terminal.terminal.mHandle })
                    assertTrue(main { terminal.running && terminal.terminal.emulator.screen.transcriptText.contains("__FOLD_CONTENT__") })
                    assertEquals(directory.canonicalPath,main { terminal.cwd })
                }
            } finally {
                shell("wm size $oldSize"); shell("wm density $oldDensity")
                main { panel?.let { w.agents?.close(it) }; w.close(terminal); previous?.let(w::select) }
                directory.deleteRecursively()
            }
        }
    }
}
