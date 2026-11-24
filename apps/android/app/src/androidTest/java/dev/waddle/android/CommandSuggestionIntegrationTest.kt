package dev.waddle.android

import android.content.Context
import android.os.SystemClock
import android.os.Bundle
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import dev.waddle.android.ai.*
import dev.waddle.android.ui.TerminalScreen
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.terminal.ShellSession
import dev.waddle.android.terminal.ShellIntegration
import dev.waddle.android.terminal.TerminalWorkspace
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CommandSuggestionIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }
    private fun await(message: String, timeout: Long = 20_000, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (main(predicate)) return
            SystemClock.sleep(50)
        }
        fail(message)
    }
    private fun withShell(block: (TerminalWorkspace, ShellSession, File) -> Unit) {
        val workspace = main { TerminalWorkspace(context) }
        val session = main { workspace.create() }
        val fixture = File(workspace.runtime.home, "AI 测试 space-${System.nanoTime()}").apply { mkdirs() }
        try {
            await("Bash prompt integration not ready") { session.promptReady && session.terminal.isShellForeground }
            assertFalse("Readline keymap initialization failed", main { output(session).contains("invalid keymap name") })
            shell(session, "cd '${fixture.path}'")
            block(workspace, session, fixture)
        } finally {
            main { workspace.closeAll() }
            fixture.deleteRecursively()
        }
    }
    private fun shell(session: ShellSession, command: String) {
        val epoch = main { session.promptEpoch }
        main { session.terminal.write(command + "\r") }
        await("Command did not return to prompt: $command") { session.promptReady && session.promptEpoch > epoch }
    }
    private fun output(session: ShellSession) = session.terminal.emulator.screen.transcriptText

    @Test fun promptMigrationPreservesUserConfiguration() {
        val file = File(context.cacheDir, "shell-config-test-${System.nanoTime()}")
        try {
            ShellIntegration.prepareConfig(file)
            assertEquals(ShellIntegration.DEFAULT_CONFIG, file.readText())
            file.writeText(ShellIntegration.OLD_CONFIG)
            ShellIntegration.prepareConfig(file)
            assertEquals(ShellIntegration.DEFAULT_CONFIG, file.readText())
            val custom = "# My shell\nPS1='custom> '\nexport PROJECT=value\n"
            file.writeText(custom)
            ShellIntegration.prepareConfig(file)
            assertEquals(custom, file.readText())
        } finally { file.delete() }
    }

    @Test fun multilineLongCommandRunsOnceInOriginalShellAndReplacesPendingInput() = withShell { workspace, session, directory ->
        assertEquals(directory.canonicalPath, main { workspace.captureContext().cwd })
        assertTrue(main { output(session).contains("AI 测试 space-") })
        // Exercise a non-default Readline map and a partially typed command with the cursor in the middle.
        shell(session, "set -o vi")
        main { session.terminal.write("printf SHOULD_NOT_RUN\u001b0") }
        val snapshot = main { workspace.captureContext() }
        val longText = "0123456789abcdef".repeat(1000)
        val script = "mkdir -p child\ncd child\nexport WADDLE_AI_VALUE=kept\ncat > payload <<'END_PAYLOAD'\n$longText\nEND_PAYLOAD\nprintf 'once\\n' >> count\n"
        assertTrue(main { workspace.runSuggestion(snapshot, script).isSuccess })
        assertTrue(main { workspace.runSuggestion(snapshot, script).isFailure })
        await("Long multiline draft did not finish") { session.promptReady && File(directory, "child/count").exists() }
        assertEquals("once\n", File(directory, "child/count").readText())
        assertEquals(longText + "\n", File(directory, "child/payload").readText())
        assertEquals(File(directory, "child").canonicalPath, main { session.cwd })
        shell(session, "printf '__VALUE_%s__\\n' \"\$WADDLE_AI_VALUE\"")
        assertTrue(main { output(session).contains("__VALUE_kept__") })
        assertFalse(main { output(session).lines().any { it.trim() == "SHOULD_NOT_RUN" } })
    }

    @Test fun staleDraftsAndForegroundProgramsCannotReceiveCommands() = withShell { workspace, session, directory ->
        var snapshot = main { workspace.captureContext() }
        val other = main { workspace.create() }
        await("Other shell not ready") { other.promptReady }
        assertTrue(main { workspace.runSuggestion(snapshot, "touch forbidden").isFailure })
        main { workspace.select(session) }
        shell(session, "cd ..")
        assertTrue(main { workspace.runSuggestion(snapshot, "touch forbidden").isFailure })
        snapshot = main { workspace.captureContext() }
        main { session.terminal.write("sleep 30\r") }
        await("Foreground job not running") { !session.promptReady && !session.terminal.isShellForeground }
        assertTrue(main { workspace.runSuggestion(snapshot, "touch forbidden").isFailure })
        main { session.terminal.write("\u0003") }
        await("Interrupted job not ready") { session.promptReady }
        snapshot = main { workspace.captureContext() }
        main { session.terminal.write("read -r waiting\r") }
        await("Read builtin not detected") { !session.promptReady }
        assertTrue(main { workspace.runSuggestion(snapshot, "touch forbidden").isFailure })
        main { session.terminal.write("\u0003") }
        await("Read builtin did not interrupt") { session.promptReady }
        main { session.terminal.write("echo '\r") }
        await("Continuation prompt not detected") { !session.promptReady }
        assertTrue(main { runCatching { workspace.captureContext() }.isFailure })
        main { session.terminal.write("\u0003") }
        await("Continuation did not interrupt") { session.promptReady }
        main { session.terminal.write("set +o emacs; set +o vi; printf '__NO_READLINE__\\n'\r") }
        await("Disabled Readline not detected") { !session.promptReady && output(session).contains("__NO_READLINE__") }
        assertTrue(main { runCatching { workspace.captureContext() }.isFailure })
        assertFalse(File(directory, "forbidden").exists())
        main { workspace.close(other); workspace.close(session) }
        assertTrue(main { workspace.runSuggestion(snapshot, "touch forbidden").isFailure })
    }

    @Test fun failureExitCodeSurvivesCustomPromptHooksAndFixNeedsConfirmation() = withShell { workspace, session, directory ->
        shell(session, "PROMPT_COMMAND=(__waddle_capture_status true __waddle_prompt)")
        shell(session, "ls missing-waddle-file")
        assertTrue(main { session.lastExitCode != 0 })
        val snapshot = main { workspace.captureContext() }
        assertTrue(snapshot.transcript.contains("missing-waddle-file"))
        assertEquals(main { session.lastExitCode }, snapshot.exitCode)
        assertFalse(File(directory, "fixed").exists())
        assertTrue(main { workspace.runSuggestion(snapshot, "touch fixed").isSuccess })
        assertTrue(main { workspace.runSuggestion(snapshot, "touch forbidden").isFailure })
        await("Fix did not finish") { session.promptReady && session.promptEpoch > snapshot.epoch }
        assertEquals(0, main { session.lastExitCode })
        assertTrue(File(directory, "fixed").exists())
        assertFalse(File(directory, "forbidden").exists())
    }

    @Test fun cancelDoesNotAffectOtherShellOrFiles() = withShell { workspace, session, _ ->
        val auth = File(workspace.runtime.home, "WELCOME.txt")
        val existed = auth.exists()
        val other = main { workspace.create() }
        await("Other shell not ready") { other.promptReady }
        main { workspace.select(session) }
        val snapshot = main { workspace.captureContext() }
        main {
            workspace.suggestions.generate(snapshot, "生成 pwd 命令")
            workspace.suggestions.cancel()
        }
        SystemClock.sleep(1_500)
        assertFalse(main { workspace.suggestions.busy })
        assertEquals("", main { workspace.suggestions.command })
        assertEquals(existed, auth.exists())
        shell(other, "printf '__OTHER_%s__\\n' alive")
        assertTrue(main { output(other).contains("__OTHER_alive__") })
        assertTrue(main { session.terminal.isRunning })
    }

    @Test fun drawerEditsMultilineDraftAndEnterExecutesExactlyOnce() = exerciseDrawer(useIme = false)

    @Test fun drawerSoftKeyboardGoExecutesExactlyOnce() = exerciseDrawer(useIme = true)

    @Test fun errorDiagnosisShowsFixButOnlyRunsAfterConfirmation() = exerciseDrawer(useIme = false, diagnose = true)

    private fun exerciseDrawer(useIme: Boolean, diagnose: Boolean = false) {
        val namespace = "ai_drawer_${System.nanoTime()}"
        val store = AiSettings(context, namespace)
        store.save(AiProvider.CUSTOM, "https://test.example/v1", "fixture-model", "not-a-real-key")
        try {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val client = main { CommandSuggester(context, store) {
                AiClient { url -> object : java.net.HttpURLConnection(url) {
                    override fun connect() {}
                    override fun disconnect() {}
                    override fun usingProxy() = false
                    override fun getOutputStream() = java.io.ByteArrayOutputStream()
                    override fun getResponseCode() = 200
                    override fun getInputStream(): java.io.InputStream {
                        val content = org.json.JSONObject().put("command", "pwd").put("explanation", "先确认当前目录，再修改路径。")
                        val body = org.json.JSONObject().put("choices", org.json.JSONArray().put(org.json.JSONObject()
                            .put("finish_reason", "stop").put("message", AiClient.message("assistant", content.toString()))))
                        return java.io.ByteArrayInputStream(body.toString().toByteArray())
                    }
                } }
            } }
            val workspace = main { TerminalWorkspace(context, client) }
            lateinit var session: ShellSession
            scenario.onActivity { activity -> activity.setContent { TerminalScreen(workspace, { workspace.create() }, {}) } }
            val previous = main { workspace.selected }
            session = main { workspace.create() }
            val directory = File(workspace.runtime.home, "drawer-test-${System.nanoTime()}").apply { mkdirs() }
            try {
                await("UI shell not ready") { session.promptReady }
                shell(session, "cd '${directory.path}'")
                if (diagnose) {
                    shell(session, "ls missing-waddle-fixture")
                    tapText("退出码 ${main { session.lastExitCode }} · 让 AI 看看")
                } else tapText("问 AI，写命令")
                val question = awaitFields(1).single()
                assertTrue(question.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "生成 pwd 命令，显示当前路径。")
                }))
                tapText(if (diagnose) "分析报错" else "生成命令")
                await("UI generation did not finish", 130_000) { !workspace.suggestions.busy && workspace.suggestions.command.isNotBlank() }
                assertFalse(File(directory, "child/count").exists())
                assertEquals(directory.canonicalPath, main { session.cwd })
                val field = awaitFields(2).last()
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        "mkdir -p child\ncd child\nprintf 'once\\n' >> count\nexport WADDLE_DRAWER_VALUE=kept")
                }))
                field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
                val focusDeadline = SystemClock.uptimeMillis() + 5_000
                while (SystemClock.uptimeMillis() < focusDeadline && editableNodes().lastOrNull()?.isFocused != true) SystemClock.sleep(50)
                assertTrue("Command editor did not receive focus", editableNodes().last().isFocused)
                instrumentation.waitForIdleSync()
                if (useIme) assertTrue(field.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id))
                else instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
                await("Enter did not execute edited draft") { session.promptReady && File(directory, "child/count").exists() }
                assertEquals("once\n", File(directory, "child/count").readText())
                assertEquals(File(directory, "child").canonicalPath, main { session.cwd })
                shell(session, "printf '__DRAWER_%s__\\n' \"\$WADDLE_DRAWER_VALUE\"")
                assertTrue(main { output(session).contains("__DRAWER_kept__") })
                assertEquals("once\n", File(directory, "child/count").readText())
            } finally {
                main { workspace.close(session); previous?.let { workspace.select(it) } }
                directory.deleteRecursively()
            }
        }
        } finally {
            context.getSharedPreferences(namespace, 0).edit().clear().commit()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("waddle.$namespace") }
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        return result
    }
    private fun awaitFields(count: Int): List<AccessibilityNodeInfo> {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val fields = editableNodes()
            if (fields.size == count) return fields
            SystemClock.sleep(100)
        }
        error("Expected $count editable fields; found ${editableNodes().size}")
    }
    private fun editableNodes(): List<AccessibilityNodeInfo> {
        instrumentation.waitForIdleSync()
        return nodes().filter { it.isEditable }
    }
    private fun tapText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            val currentNodes = nodes()
            var node = currentNodes.firstOrNull { it.text?.toString() == text }
            node?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            while (node != null) {
                if (node.isClickable && node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                node = node.parent
            }
            currentNodes.firstOrNull { it.isScrollable && it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD } }
                ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(100)
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(context.cacheDir, "ai-drawer-test-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fail("Cannot click $text; visible=" + nodes().mapNotNull { it.text?.toString() }.take(20))
    }

    @Test fun liveSuggestionUsesCurrentContextWithoutExecutingUntilRun() = withShell { workspace, session, directory ->
        assumeTrue(InstrumentationRegistry.getArguments().getString("waddleLiveSuggestions") == "true")
        val marker = "CONTEXT_${System.nanoTime()}"
        shell(session, "printf '需要保存的标记：%s\\n' '$marker'")
        val snapshot = main { workspace.captureContext() }
        main { workspace.suggestions.generate(snapshot,
            "根据最近终端输出里‘需要保存的标记’，生成 Bash 命令，将该标记加换行写入当前目录的 result.txt，然后创建 next 目录并进入。请在生成阶段现在就用工具写 result.txt 验证，之后再给我命令。") }
        await("Live suggestion timed out", 130_000) { !workspace.suggestions.busy }
        assertNull(main { workspace.suggestions.error }, main { workspace.suggestions.error })
        val draft = main { workspace.suggestions.command }
        assertTrue("No command returned", draft.isNotBlank())
        assertFalse("Generation wrote the requested file before Enter", File(directory, "result.txt").exists())
        assertFalse("Generation changed the directory before Enter", File(directory, "next").exists())
        assertEquals(snapshot.epoch, main { session.promptEpoch })
        assertEquals(snapshot.cwd, main { session.cwd })
        assertTrue(main { workspace.runSuggestion(snapshot, draft).isSuccess })
        await("Suggested command did not finish") { session.promptReady && File(directory, "result.txt").exists() }
        assertEquals(marker + "\n", File(directory, "result.txt").readText())
        assertEquals(File(directory, "next").canonicalPath, main { session.cwd })
    }
}
