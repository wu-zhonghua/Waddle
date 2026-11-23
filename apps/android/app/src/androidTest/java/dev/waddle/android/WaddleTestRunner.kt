package dev.waddle.android

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.runner.AndroidJUnitRunner

/** Only isolated emulators: exercise the real first-run dialog, including denial. */
class WaddleTestRunner : AndroidJUnitRunner() {
    @Volatile private var watching = true
    private var deny = false
    @Volatile private var chosePermission = false
    override fun onCreate(arguments: Bundle) {
        deny = arguments.getString("waddleNotifications") == "deny"
        super.onCreate(arguments)
    }
    override fun onStart() {
        if (Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Android SDK") || Build.HARDWARE in listOf("ranchu", "goldfish")) Thread {
            while (watching && !chosePermission) {
                runCatching {
                    fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
                        if (root == null) emptyList() else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
                    val visible = nodes(uiAutomation.rootInActiveWindow)
                    if (visible.any { it.text?.toString()?.let { text -> text.contains("notifications", true) || text.contains("通知") } == true &&
                            it.packageName?.toString()?.endsWith("permissioncontroller") == true }) {
                        val id = if (deny) "/permission_deny_button" else "/permission_allow_button"
                        val labels = if (deny) setOf("don't allow", "don’t allow", "不允许") else setOf("allow", "允许")
                        var button = visible.firstOrNull { it.viewIdResourceName?.endsWith(id) == true || it.text?.toString()?.lowercase() in labels }
                        while (button != null) {
                            if (button.isClickable && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { chosePermission = true; break }
                            button = button.parent
                        }
                    }
                }
                SystemClock.sleep(200)
            }
        }.apply { isDaemon = true; start() }
        super.onStart()
    }
    override fun finish(resultCode: Int, results: Bundle?) { watching = false; results?.putString("waddleNotificationChoice", if (chosePermission) (if (deny) "deny" else "allow") else "no-dialog"); super.finish(resultCode, results) }
}
