package dev.waddle.android

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DisplaySettingsIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun args(setting: String, value: Int) = JSONObject().put("setting", setting).put("value", value)
    private fun await(message: String, seconds: Int = 90, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + seconds * 1000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(100)
        }
        fail(message)
    }

    private fun restore(original: DisplayState) {
        // Test cleanup executes as Waddle's ordinary UID, using its granted permission.
        assertTrue(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0))
        assertTrue(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, original.brightness))
        assertTrue(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, original.mode))
        SystemClock.sleep(300)
        val actual = NativeDisplaySettingsTool(context).state()
        assertEquals(original.mode, actual.mode)
        if (original.mode == 0) assertEquals(original.brightness, actual.brightness)
    }

    @Test fun displaySchemaRejectsOtherSettingsAndInvalidValues() {
        for (text in listOf("{}", "{\"setting\":\"brightness\",\"value\":0}",
            "{\"setting\":\"brightness\",\"value\":256}", "{\"setting\":\"brightness\",\"value\":-1}",
            "{\"setting\":\"brightness\",\"value\":1.5}", "{\"setting\":\"brightness\",\"value\":\"96\"}",
            "{\"setting\":\"brightness_mode\",\"value\":2}", "{\"setting\":\"brightness_mode\",\"value\":true}",
            "{\"setting\":\"airplane_mode\",\"value\":1}", "{\"setting\":\"brightness\",\"value\":96,\"uri\":\"content://settings\"}")) {
            assertTrue("Accepted $text", runCatching { NativeDisplaySettingsTool.parse(JSONObject(text)) }.isFailure)
        }
        assertEquals(DisplayRequest("brightness", 96), NativeDisplaySettingsTool.parse(args("brightness", 96)))
        assertEquals(DisplayRequest("brightness_mode", 1), NativeDisplaySettingsTool.parse(args("brightness_mode", 1)))
    }

    @Test fun missingSystemPermissionCannotPlanOrApply() {
        val tool = NativeDisplaySettingsTool(context)
        assumeFalse("Run before granting Android write-settings access", tool.canWrite())
        val before = tool.state()
        val denied = runCatching { tool.plan(args("brightness", 96)) }
        assertTrue(denied.isFailure)
        assertTrue(denied.exceptionOrNull()!!.message!!.contains("系统授权"))
        assertTrue(runCatching { tool.apply(DisplayChange(DisplayRequest("brightness", 96), before)) }.isFailure)
        assertEquals(before, tool.state())
    }

    /** Opt-in because it changes real device settings. Always restores the initial state. */
    @Test fun nativeWritesReadBackAndRejectStaleConfirmation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("waddleDisplayWrites") == "true")
        val tool = NativeDisplaySettingsTool(context)
        assertTrue("Grant write-settings access in Android Settings first", tool.canWrite())
        val original = tool.state()
        try {
            fun write(setting: String, value: Int) {
                assertTrue(tool.apply(tool.plan(args(setting, value))).getBoolean("verified"))
                SystemClock.sleep(200)
                assertEquals(value, if (setting == "brightness") tool.state().brightness else tool.state().mode)
            }
            write("brightness_mode", 0)
            write("brightness", 96)
            val stale = tool.plan(args("brightness", 128))
            write("brightness", 160)
            assertTrue(runCatching { tool.apply(stale) }.isFailure)
            assertEquals(160, tool.state().brightness)
            write("brightness_mode", 1)
            assertTrue("Fixed brightness must not silently disable automatic mode", runCatching { tool.plan(args("brightness", 96)) }.isFailure)
            write("brightness_mode", 0)
        } finally { restore(original) }
    }

    /** Uses a real signed-in Waddle Codex and the same confirmation handler as the UI. */
    @Test fun liveCodexWritesOnlyAfterConfirmationAndRestoresSettings() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("waddleLiveDisplay") == "true")
        val tool = NativeDisplaySettingsTool(context)
        assertTrue(tool.canWrite())
        val original = tool.state()
        val evidence = JSONObject().put("uid", Process.myUid()).put("original", original.json()).put("completed", false)
        val changes = JSONArray()
        evidence.put("confirmedChanges", changes)
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it }
                await("Service did not bind") { activity.codex != null }
                lateinit var client: CodexController
                main { client = activity.codex!!; client.connect() }
                try {
                    await("Waddle login unavailable") { client.signedIn || client.error != null }
                    main { assertNull(client.error); assertTrue(client.signedIn); client.newConversation(); client.updateDisplayWritesEnabled(true) }
                    val initialCalls = client.nativeWriteCalls
                    val baseline = tool.state()
                    val target = if (baseline.brightness == 96) 128 else 96
                    fun request(setting: String, value: Int): CodexApproval {
                        main { client.newConversation(); client.send("请只调用 waddle_set_display_setting，setting=$setting，value=$value；等待 Waddle 确认后报告结果。不要用 shell，不要修改其他设置。如果被拒绝或关闭开关，立即结束，不重试。") }
                        await("Model did not request a display change: ${client.error}", 120) { client.approvals.any { it.displayWrite != null } || !client.busy }
                        lateinit var approval: CodexApproval
                        main {
                            assertNull(client.error)
                            approval = client.approvals.firstOrNull { it.displayWrite != null } ?: error("No native write request: ${client.messages.lastOrNull()?.text}")
                            assertEquals(DisplayRequest(setting, value), approval.displayWrite!!.change.request)
                        }
                        return approval
                    }
                    fun done() {
                        await("Codex did not finish after confirmation", 120) { !client.busy }
                        main { assertNull(client.error); assertTrue(client.approvals.isEmpty()) }
                    }

                    val rejected = request("brightness", target)
                    assertEquals(baseline, tool.state())
                    main { client.answerApproval(rejected, false) }
                    done()
                    assertEquals(baseline, tool.state()); assertEquals(initialCalls, client.nativeWriteCalls)
                    evidence.put("rejectedRequestUnchanged", true)

                    val disabled = request("brightness", target)
                    main { client.updateDisplayWritesEnabled(false); client.answerApproval(disabled, true) }
                    done()
                    assertEquals(baseline, tool.state()); assertEquals(initialCalls, client.nativeWriteCalls)
                    evidence.put("disabledPendingRequestUnchanged", true)
                    main { client.updateDisplayWritesEnabled(true) }

                    fun accept(setting: String, value: Int) {
                        val before = tool.state()
                        val approval = request(setting, value)
                        assertEquals("Mode changed before confirmation", before.mode, tool.state().mode)
                        if (setting == "brightness") assertEquals("Brightness changed before confirmation", before.brightness, tool.state().brightness)
                        main { client.answerApproval(approval, true) }
                        done()
                        SystemClock.sleep(250)
                        val actual = tool.state()
                        assertEquals(value, if (setting == "brightness") actual.brightness else actual.mode)
                        changes.put(JSONObject().put("setting", setting).put("value", value).put("before", before.json()).put("actual", actual.json()))
                    }
                    accept("brightness", target)
                    accept("brightness_mode", 1)
                    accept("brightness_mode", 0)
                    assertEquals(initialCalls + 3, client.nativeWriteCalls)
                    evidence.put("completed", true)
                } finally { main { client.disconnect() } }
            }
        } finally {
            restore(original)
            evidence.put("restored", tool.state().json()).put("capturedAtMs", System.currentTimeMillis())
            File(context.filesDir, "display-write-validation.json").writeText(evidence.toString(2))
        }
    }
}
