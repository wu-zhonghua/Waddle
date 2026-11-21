package dev.waddle.android

import android.app.Activity
import android.app.Application
import android.app.UiAutomation
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexController
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Real model and visible UI coverage; explicitly opt in on the selected signed-in emulator. */
@RunWith(AndroidJUnit4::class)
class CodexLifecycleIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val ui get() = instrumentation.uiAutomation
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(message: String, seconds: Int = 45, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + seconds * 1000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = predicate() }
            if (ready) return
            SystemClock.sleep(100)
        }
        fail(message)
    }

    private fun click(text: String) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            val nodes = nodes(ui.rootInActiveWindow).filter { it.text?.toString() == text || it.contentDescription?.toString() == text }
            for (node in nodes) {
                var target: AccessibilityNodeInfo? = node
                while (target != null) {
                    if (target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                    target = target.parent
                }
            }
            SystemClock.sleep(100)
        }
        fail("Visible control missing: $text; window=${ui.rootInActiveWindow?.packageName}; tabs=" +
            nodes(ui.rootInActiveWindow).mapNotNull { it.text?.toString() }.filter { it.contains("Codex") || it.contains("终端") }.take(8))
    }

    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        return listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
    }

    @Test fun stopDuringStartupAndCommandThenContinueAfterBackgroundAndRotation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("waddleLiveLifecycle") == "true")
        val app = ApplicationProvider.getApplicationContext<Application>()
        val resumed = AtomicReference<MainActivity?>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) resumed.set(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        val evidence = JSONObject().put("completed", false)
        val initialRotation = app.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY).rotation
        val initialAutoRotation = Settings.System.getInt(app.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                await("Service not bound") { resumed.get()?.codex != null }
                lateinit var client: CodexController
                main { client = resumed.get()!!.codex!!; client.connect() }
                val fixture = File(client.runtime.home, "lifecycle-probe-${System.nanoTime()}").apply { mkdirs() }
                try {
                    await("Signed-in embedded Codex unavailable") { client.signedIn || client.error != null }
                    main { assertNull(client.error); assertTrue(client.signedIn); client.newConversation() }
                    click("打开导航抽屉"); click("Codex")
                    // Same main-thread callback ensures stop arrives before thread/start's response.
                    main {
                        client.send("只回复 WADDLE_CANCELLED_BEFORE_START，不要调用工具。")
                        client.interrupt()
                    }
                    await("Stop while creating a thread was lost") { !client.busy }
                    main {
                        assertEquals("已停止", client.status); assertNull(client.error)
                        assertFalse(client.messages.any { it.role == "Codex" })
                    }
                    evidence.put("stopBeforeThreadReady", true)

                    main {
                        client.send("只回复 WADDLE_CANCELLED_DURING_START，不要调用工具。")
                        client.interrupt()
                    }
                    await("Stop before turn ID was lost") { !client.busy }
                    main { assertNull(client.error); assertEquals("已停止", client.status) }
                    evidence.put("stopBeforeTurnReady", true)

                    val script = File(fixture, "wait.sh")
                    script.writeText("printf started > '${fixture.path}/started'\nsleep 20\nprintf UNEXPECTED > '${fixture.path}/finished'\n")
                    main { client.send("请实际执行 bash ${script.path} 并等待命令结束。不要改脚本、不要加后台符号、不要改设置、不访问其他目录。") }
                    await("Model did not launch the foreground command", 120) { File(fixture, "started").isFile }
                    main { assertTrue(client.busy) }
                    click("停止")
                    await("Stop did not end the active turn", 15) { !client.busy }
                    main { assertNull(client.error); assertEquals("已停止", client.status); assertTrue(client.approvals.isEmpty()) }
                    // Wait beyond the original sleep so a surviving process cannot falsely pass.
                    SystemClock.sleep(21_000)
                    assertFalse("Interrupted command kept running", File(fixture, "finished").exists())
                    evidence.put("stopRunningCommandWithoutLateWrite", true)

                    val marker = "WADDLE_CONTEXT_${System.nanoTime()}"
                    main { client.send("请记住本次验收口令 $marker，只回复该口令，不要用工具。") }
                    await("Cannot continue after stop", 120) { !client.busy }
                    val thread = client.threadId
                    main { assertNull(client.error); assertTrue(client.messages.any { it.role == "Codex" && it.text.contains(marker) }) }
                    val before = resumed.get()!!
                    main { assertTrue(before.moveTaskToBack(true)) }
                    await("Activity did not enter background") { !before.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
                    SystemClock.sleep(1_000)
                    // Match returning through the launcher, rather than creating a new Activity with a different intent.
                    app.startActivity(app.packageManager.getLaunchIntentForPackage(app.packageName)!!)
                    await("Background return lost the conversation") {
                        resumed.get()?.codex === client && resumed.get()!!.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                    }
                    main { assertEquals(thread, client.threadId); assertTrue(client.messages.any { it.text.contains(marker) }) }
                    click("打开导航抽屉"); click("Codex")
                    evidence.put("backgroundReturn", true)

                    val oldActivity = resumed.get()!!
                    val nextRotation = if (oldActivity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 1 else 0
                    assertTrue(ui.setRotation(nextRotation))
                    await("Real rotation did not recreate and rebind Activity") {
                        val current = resumed.get()
                        current != null && current !== oldActivity && current.codex === client &&
                            current.resources.configuration.orientation == (if (nextRotation == 1) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT)
                    }
                    main { assertEquals(thread, client.threadId); assertTrue(client.messages.any { it.text.contains(marker) }) }
                    click("打开导航抽屉"); click("Codex")
                    main { client.send("刚才让你记住的验收口令是什么？只回复原口令，不调用工具。") }
                    await("Cannot continue after rotation", 120) { !client.busy }
                    main {
                        assertNull(client.error); assertEquals(thread, client.threadId)
                        assertTrue(client.messages.last { it.role == "Codex" }.text.contains(marker))
                    }
                    evidence.put("rotationAndConversationMemory", true).put("completed", true)
                } finally {
                    main { client.disconnect() }
                    fixture.deleteRecursively()
                }
            }
        } finally {
            ui.setRotation(initialRotation)
            if (initialAutoRotation != 0) ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
            app.unregisterActivityLifecycleCallbacks(callbacks)
            File(app.filesDir, "stage1-lifecycle-validation.json").writeText(evidence.toString(2))
        }
    }
}
