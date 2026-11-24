package dev.waddle.android

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexSession
import dev.waddle.android.terminal.TerminalWorkspace
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class CodexHistoryIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>(); instrumentation.runOnMainSync { value.set(block()) }; return value.get()
    }
    private fun await(label: String, seconds: Int = 30, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + seconds * 1000
        while (SystemClock.uptimeMillis() < end) { if (check()) return; SystemClock.sleep(100) }
        fail(label) // Never dump the user's history into the test log.
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        fun walk(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
            if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { walk(node.getChild(it)) }
        return walk(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun click(text: String) = await("Cannot click fixture UI") {
        var node = nodes().firstOrNull { it.text?.toString() == text || it.contentDescription?.toString() == text }
        var clicked = false
        while (node != null && !clicked) {
            if (node.isClickable && node.isEnabled) clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node = node.parent
        }
        clicked
    }
    private fun draft(text: String) = await("Cannot enter draft") {
        nodes().firstOrNull { it.className?.toString() == "android.widget.EditText" }?.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }) == true
    }

    @Test fun liveHistoryPickerRestoresMessagesCwdAndContinuesSameSession() {
        assumeTrue(BuildConfig.BUNDLED_AGENTS && InstrumentationRegistry.getArguments().getString("waddleLiveCodex") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var workspace: TerminalWorkspace? = null
            await("Workspace missing") { scenario.onActivity { workspace = it.workspace }; workspace != null }
            val w = workspace!!; val agents = w.agents!!
            val previous = main { w.selected }
            val previousAgent = main { agents.selected }
            val root = File(w.runtime.home, "waddle-history-${System.nanoTime()}").apply { mkdirs() }
            val a = File(root, "original 中文 space").apply { mkdir() }
            val b = File(root, "different terminal").apply { mkdir() }
            val marker = "HISTORY_CWD_${System.nanoTime()}"
            File(a, "marker.txt").writeText(marker)
            val shells = mutableListOf<dev.waddle.android.terminal.ShellSession>()
            fun launch(folder: File): CodexSession {
                val shell = main { w.create(folder) }; shells += shell
                await("Shell missing") { main { shell.promptReady } }
                main { shell.terminal.write("codex --waddle-chat\r") }
                await("Chat missing", 60) { main { agents.selected?.let { it.origin === shell && (it.client.signedIn || it.client.error != null) } == true } }
                return main { agents.selected!! }.also { assertNull(main { it.client.error }); assertTrue(main { it.client.signedIn }) }
            }
            try {
                val old = launch(a)
                val prompt = "Waddle 历史恢复测试 ${System.nanoTime()}。请只回复 HISTORY_READY，不调用工具。"
                main { old.client.send(prompt) }
                await("First real turn timed out", 150) { main { !old.client.busy } }
                assertNull(main { old.client.error })
                val thread = main { old.client.threadId!! }
                assertTrue(main { old.client.messages.any { it.role == "Codex" && it.text.contains("HISTORY_READY") } })
                main { agents.close(old) }
                val source = launch(b)
                await("Chat UI missing") { nodes().any { it.text?.toString() == "历史对话" || it.contentDescription?.toString() == "历史对话" } }
                draft("UNSENT_SOURCE_DRAFT")
                click("历史对话")
                await("History list missing", 45) { main { !source.client.historyLoading } }
                assertNull(main { source.client.historyError })
                while (main { source.client.history.none { it.id == thread } && source.client.historyCursor != null }) {
                    main { source.client.loadHistory(more = true) }
                    await("More history missing", 45) { main { !source.client.historyLoading } }
                    assertNull(main { source.client.historyError })
                }
                val entry = main { source.client.history.single { it.id == thread } }
                assertEquals(a.canonicalPath, entry.cwd)
                assertTrue(entry.updatedAt > 0)
                assertTrue("Existing local history was omitted", main { source.client.history.any { it.id != thread } })
                click(entry.title)
                await("Original session not restored", 60) { main { agents.selected?.client?.let { it.threadId == thread && !it.busy } == true } }
                val restored = main { agents.selected!! }
                assertNotSame(source, restored)
                assertEquals(a.canonicalFile, main { restored.client.workingDirectory })
                assertTrue(main { restored.client.messages.any { it.text == prompt } })
                await("History not displayed") { nodes().any { it.text?.toString()?.contains("HISTORY_READY") == true } }
                main { restored.client.send("读取当前工作目录的 marker.txt，回复文件的完整内容及当前目录绝对路径。只读取这个文件，不访问其他目录，不修改文件。") }
                await("Resumed real turn timed out", 180) {
                    main {
                        restored.client.approvals.toList().forEach { restored.client.answerApproval(it, true) }
                        !restored.client.busy
                    }
                }
                assertNull(main { restored.client.error })
                assertEquals(thread, main { restored.client.threadId })
                assertEquals(a.canonicalFile, main { restored.client.workingDirectory })
                assertTrue("Continuation did not read the original cwd", main { restored.client.messages.any { it.role == "Codex" && it.text.contains(marker) && it.text.contains(a.name) } })
                draft("UNSENT_RESTORED_DRAFT")
                val count = main { agents.sessions.size }
                click("历史对话")
                await("Second history list missing", 45) { main { !restored.client.historyLoading && restored.client.history.any { it.id == thread } } }
                click(main { restored.client.history.first { it.id == thread }.title })
                assertSame(restored, main { agents.selected }); assertEquals(count, main { agents.sessions.size })
                await("Restored draft lost") { nodes().any { it.text?.toString() == "UNSENT_RESTORED_DRAFT" } }
                main { agents.selected = source; agents.openRequest = source }
                await("Source draft lost") { nodes().any { it.text?.toString() == "UNSENT_SOURCE_DRAFT" } }
                main { agents.restoreHistory(source, thread) }
                assertSame(restored, main { agents.selected }); assertEquals(count, main { agents.sessions.size })
                await("Restored draft lost after switching") { nodes().any { it.text?.toString() == "UNSENT_RESTORED_DRAFT" } }
                scenario.recreate()
                await("History lost after Activity recreation") { nodes().any { it.text?.toString() == "UNSENT_RESTORED_DRAFT" } }
                assertEquals(thread, main { agents.selected!!.client.threadId })
            } finally {
                main {
                    agents.sessions.filter { it.origin in shells }.toList().forEach(agents::close)
                    shells.forEach(w::close); previous?.let(w::select)
                    if (previousAgent in agents.sessions) agents.selected = previousAgent
                }
                root.deleteRecursively()
            }
        }
    }
}
