package dev.waddle.android

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.terminal.TerminalWorkspace
import dev.waddle.android.terminal.ShellSession
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class BundledAgentsIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>(); instrumentation.runOnMainSync { result.set(block()) }; return result.get()
    }
    private fun await(message: String, seconds: Int = 30, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + seconds * 1000
        while (SystemClock.uptimeMillis() < end) { if (main(check)) return; SystemClock.sleep(100) }
        fail(message)
    }
    private fun shell(session: ShellSession, text: String, seconds: Int = 30) {
        val epoch = main { session.promptEpoch }
        main { session.terminal.write(text + "\r") }
        await("Shell did not finish", seconds) { session.promptReady && session.promptEpoch > epoch }
    }
    @Test fun terminalLaunchChoiceCwdRememberOverrideAndIndependentConversations() {
        assumeTrue(BuildConfig.BUNDLED_AGENTS)
        val workspace = main { TerminalWorkspace(context) }
        val agents = workspace.agents!!
        val preferred = main { agents.preferred }
        val root = File(context.cacheDir, "agent-launch-${System.nanoTime()}").apply { mkdirs() }
        try {
            val first = main { agents.preference("ask"); workspace.create(root) }
            await("Prompt missing") { first.promptReady }
            main { first.terminal.write("codex\r") }
            await("Choice not requested") { agents.launches.size == 1 }
            assertEquals(root.canonicalFile, main { agents.launches.first().cwd.canonicalFile })
            main { agents.answer(agents.launches.first(), "chat", true) }
            await("Chat not launched") { first.promptReady && agents.sessions.size == 1 && agents.sessions.first().client.connected }
            val chat = main { agents.sessions.first() }
            assertEquals(root.canonicalFile, chat.client.workingDirectory)
            val child = File(root, "second space").apply { mkdirs() }
            val second = main { workspace.create(child) }
            await("Second prompt missing") { second.promptReady }
            shell(second, "codex")
            await("Remembered choice missing") { agents.sessions.size == 2 && agents.sessions.last().client.connected }
            assertTrue(main { agents.launches.isEmpty() })
            assertEquals(child.canonicalFile, main { agents.sessions.last().client.workingDirectory })
            assertNotSame(chat.client, main { agents.sessions.last().client })
            // Explicit terminal override bypasses remembered chat; ordinary flags keep CLI behavior.
            shell(second, "codex --waddle-terminal --version")
            assertTrue(main { second.terminal.emulator.screen.transcriptText.contains("codex-cli 0.170.0") })
            assertEquals(2, main { agents.sessions.size })
            main { agents.preference("terminal") }
            shell(first, "codex --waddle-chat")
            await("Chat override missing") { agents.sessions.size == 3 }
            main { agents.close(chat) }
            assertEquals(2, main { agents.sessions.size })
            assertTrue(main { agents.sessions.first().client.connected })
            main { agents.preference("ask"); first.terminal.write("codex\r") }
            await("Reset did not ask") { agents.launches.isNotEmpty() }
            main { agents.answer(agents.launches.first(), "cancel") }
            await("Cancel did not return") { first.promptReady }
            val tui = main { agents.preference("terminal"); workspace.create(root) }
            await("TUI shell missing") { tui.promptReady }
            main { tui.terminal.write("codex\r") }
            await("Original terminal interface did not render") {
                val screen = tui.terminal.emulator.screen.transcriptText
                !tui.promptReady && (screen.contains("Codex") || screen.contains("trust", true))
            }
            assertEquals(root.canonicalPath, main { tui.terminal.cwd })
            assertTrue(main { agents.launches.isEmpty() })
            main { tui.terminal.write("\u0003") }
            SystemClock.sleep(250)
            main { if (!tui.promptReady) tui.terminal.write("\u0003") }
            await("TUI did not return to Bash") { tui.promptReady }
            main { workspace.close(tui) }
        } finally { main { agents.preference(preferred); workspace.closeAll() }; root.deleteRecursively() }
    }

    @Test fun liveSavedLoginReadsWritesAndDraftRequiresConfirmation() {
        assumeTrue(BuildConfig.BUNDLED_AGENTS && InstrumentationRegistry.getArguments().getString("waddleLiveCodex") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var workspace: TerminalWorkspace? = null
            await("Service missing") { scenario.onActivity { workspace = it.workspace }; workspace != null }
            val w = workspace!!
            val agents = w.agents!!
            val oldMode = main { agents.preferred }
            val oldBackend = main { w.suggestions.useCodex }
            val previous = main { w.selected }
            val root = File(w.runtime.home, "waddle-stage1-${System.nanoTime()}").apply { mkdirs() }
            val input = File(root, "input.txt").apply { writeText("WADDLE_STAGE1_READ_WRITE") }
            val shell = main { w.create(root) }
            try {
                await("Prompt missing") { shell.promptReady }
                main { shell.terminal.write("codex --waddle-chat\r") }
                await("Login not restored", 60) { agents.selected?.client?.let { it.signedIn || it.error != null } == true }
                val client = main { agents.selected!!.client }
                assertNull(main { client.error }); assertTrue(main { client.signedIn })
                main { client.send("在当前目录读取 input.txt，把相同内容写入 result.txt，再读取 result.txt 验证。只操作当前测试目录，不读取认证文件。实际完成后简短报告。") }
                await("Real file task timed out", 180) {
                    client.approvals.toList().forEach { client.answerApproval(it, true) }
                    !client.busy
                }
                assertNull(main { client.error })
                assertEquals(input.readText(), File(root, "result.txt").readText().trim())
                main { client.send("请通过 shell 执行 bash -c 'printf SHOULD_NOT_RUN > rejected.txt'。如果用户拒绝审批，立即停止，不要重试或换工具。只操作当前目录。") }
                await("Expected command approval was not offered", 120) { client.approvals.isNotEmpty() || !client.busy }
                assertTrue("Write must await an approval", main { client.approvals.isNotEmpty() })
                assertFalse(File(root, "rejected.txt").exists())
                main { client.approvals.toList().forEach { client.answerApproval(it, false) } }
                await("Rejected task did not finish", 120) {
                    client.approvals.toList().forEach { client.answerApproval(it, false) }
                    !client.busy
                }
                assertFalse("Rejected command executed", File(root, "rejected.txt").exists())
                main { w.select(shell); w.suggestions.selectCodex(true) }
                await("Shell busy") { shell.promptReady }
                val snapshot = main { w.captureContext() }
                main { w.suggestions.generate(snapshot, "生成一条命令，把当前目录的 input.txt 复制为 draft-confirmed.txt。") }
                await("Real draft timed out", 150) { !w.suggestions.busy }
                assertNull(main { w.suggestions.error })
                assertTrue(main { w.suggestions.command.isNotBlank() })
                assertFalse(File(root, "draft-confirmed.txt").exists())
                main { assertTrue(w.runSuggestion(snapshot, w.suggestions.command).isSuccess) }
                await("Confirmed draft did not run") { shell.promptReady && File(root, "draft-confirmed.txt").exists() }
                assertEquals(input.readText(), File(root, "draft-confirmed.txt").readText().trim())
                // CLI uses the same on-device login; no credentials are copied or printed.
                shell(shell, "codex login status >/dev/null 2>&1; printf '__LOGIN_%s__\\n' \"\$?\"")
                assertTrue(main { shell.terminal.emulator.screen.transcriptText.contains("__LOGIN_0__") })
                shell(shell, "codex --ask-for-approval on-request exec --sandbox workspace-write --skip-git-repo-check 'Read input.txt and write its exact content to cli-result.txt in this directory. Do not access any other directory or credentials.' </dev/null >cli-test-output.txt 2>&1; printf '__CLI_%s__\\n' \"\$?\"", 180)
                assertTrue(main { shell.terminal.emulator.screen.transcriptText.contains("__CLI_0__") })
                assertEquals(input.readText(), File(root, "cli-result.txt").readText().trim())
                shell(shell, "cat missing-stage1-file")
                val failure = main { w.captureContext() }
                main { w.suggestions.generate(failure, "解释刚才命令为什么失败，给出建议；不要执行命令。") }
                await("Diagnosis timed out", 150) { !w.suggestions.busy }
                assertNull(main { w.suggestions.error }); assertTrue(main { w.suggestions.explanation.isNotBlank() })
                assertFalse(File(root, "missing-stage1-file").exists())
                main { agents.close(agents.sessions.first { it.client === client }) }
            } finally {
                main {
                    agents.sessions.filter { it.origin === shell }.toList().forEach(agents::close)
                    agents.preference(oldMode); w.suggestions.selectCodex(oldBackend); w.close(shell); previous?.let(w::select)
                }
                root.deleteRecursively()
            }
        }
    }
}
