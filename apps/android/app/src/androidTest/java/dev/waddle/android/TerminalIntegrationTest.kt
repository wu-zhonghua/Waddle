package dev.waddle.android

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.termux.view.TerminalView
import dev.waddle.android.terminal.ShellSession
import dev.waddle.android.terminal.TerminalWorkspace
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class TerminalIntegrationTest {
    @get:Rule
    val notificationPermission: GrantPermissionRule = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun await(message: String, predicate: () -> Boolean) {
        // CI runs on a software-accelerated emulator where shell startup can take several seconds.
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (main(predicate)) return
            SystemClock.sleep(40)
        }
        fail(message)
    }

    private fun output(session: ShellSession) = session.terminal.emulator.screen.transcriptText

    private fun withWorkspace(block: (TerminalWorkspace, ShellSession) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workspace = main { TerminalWorkspace(context) }
        val session = main { workspace.create() }
        try {
            await("Shell did not display its prompt") { output(session).contains("waddle") }
            main { session.terminal.write("stty -echo\r") }
            block(workspace, session)
        } finally {
            main { workspace.closeAll() }
            await("Shell was not reaped after close") { !session.terminal.isRunning }
        }
    }

    @Test fun shellHasPtyUnicodeAndPrivateWorkingDirectory() = withWorkspace { _, session ->
        main { session.terminal.write("test -t 0 && printf '__PTY_%s__\\n' ok; printf '__HOME_%s__\\n' \"\$PWD\"; printf '\\033[32m你好 Waddle\\033[0m\\n'\r") }
        await("PTY/UTF-8 output missing") {
            val text = output(session)
            text.contains("__PTY_ok__") && text.contains("你好 Waddle") && text.contains("/dev.waddle.android/files/home")
        }
        assertFalse(main { output(session).contains("\u001b[32m") })
    }

    @Test fun resizeReachesTheKernelPty() = withWorkspace { _, session ->
        main {
            session.terminal.updateSize(101, 37, 8, 16)
            session.terminal.write("printf '__SIZE_'; stty size\r")
        }
        await("PTY resize was not applied") { output(session).contains("__SIZE_37 101") }
    }

    @Test fun ctrlCInterruptsForegroundJobAndShellStillWorks() = withWorkspace { _, session ->
        main { session.terminal.write("printf '__JOB_%s__\\n' started; sleep 30; printf '__JOB_%s__\\n' completed\r") }
        await("Job did not start") { output(session).contains("__JOB_started__") }
        main { session.terminal.write("\u0003") }
        main { session.terminal.write("printf '__AFTER_%s__\\n' interrupt\r") }
        await("Shell did not recover after Ctrl-C") { output(session).contains("__AFTER_interrupt__") }
        assertTrue(main { session.terminal.isRunning })
    }

    @Test fun sessionsHaveIndependentStateAndExitCodes() = withWorkspace { workspace, first ->
        val second = main { workspace.create() }
        try {
            main {
                first.terminal.write("WADDLE_TEST=first; printf '__FIRST_%s__\\n' \"\$WADDLE_TEST\"\r")
                second.terminal.write("printf '__SECOND_%s__\\n' \"\${WADDLE_TEST:-empty}\"; exit 7\r")
            }
            await("First session lost state") { output(first).contains("__FIRST_first__") }
            await("Second session did not exit") { !second.terminal.isRunning }
            assertTrue(main { output(second).contains("__SECOND_empty__") })
            assertEquals(7, main { second.terminal.exitStatus })
            assertTrue(main { first.terminal.isRunning })
            main { workspace.select(first); workspace.close(second) }
            assertSame(first, main { workspace.selected })
        } finally { main { workspace.close(second) } }
    }

    @Test fun imeCompositionAndActivityRecreationPreserveTheSession() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            await("Activity did not connect to its service and render its terminal") {
                activity.workspace?.selected != null && findTerminal(activity.window.decorView) != null
            }
            lateinit var session: ShellSession
            scenario.onActivity {
                session = it.workspace!!.selected!!
                val view = findTerminal(it.window.decorView)!!
                val connection = view.onCreateInputConnection(EditorInfo())
                connection.setComposingText("printf '输入法", 1)
                connection.commitText("printf '输入法_%s\\n' 成功\n", 1)
            }
            await("IME composition was not committed correctly") { output(session).contains("输入法_成功") }
            val originalPid = main { session.terminal.pid }
            scenario.recreate()
            scenario.onActivity { activity = it }
            await("Recreated Activity failed to rebind") { activity.workspace?.selected != null }
            scenario.onActivity {
                assertSame(session, it.workspace!!.selected)
                assertEquals(originalPid, it.workspace!!.selected!!.terminal.pid)
                assertTrue(output(session).contains("输入法_成功"))
                it.workspace!!.closeAll()
            }
        }
    }

    private fun findTerminal(view: View): TerminalView? {
        if (view is TerminalView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findTerminal(view.getChildAt(i))?.let { return it }
        return null
    }
}
