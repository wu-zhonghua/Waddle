package dev.waddle.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Verify the matrix choice is the real OS permission, not just an instrumentation argument. */
class NotificationPermissionIntegrationTest {
    @Test fun requestedNotificationChoiceMatchesSystemPermission() {
        val inst=InstrumentationRegistry.getInstrumentation()
        val choice=InstrumentationRegistry.getArguments().getString("waddleNotifications")
        assumeTrue(Build.VERSION.SDK_INT >= 33 && Build.HARDWARE in listOf("ranchu","goldfish") && choice in listOf("allow","deny"))
        ActivityScenario.launch(MainActivity::class.java).use {
            val end=SystemClock.uptimeMillis()+15000
            while(SystemClock.uptimeMillis()<end) {
                val root=inst.uiAutomation.rootInActiveWindow
                val granted=inst.targetContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
                if(root?.packageName?.toString()==inst.targetContext.packageName && granted==(choice=="allow")) break
                SystemClock.sleep(100)
            }
            assertEquals("Actual notification permission must match the CI matrix",choice=="allow",
                inst.targetContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
        }
    }
}
