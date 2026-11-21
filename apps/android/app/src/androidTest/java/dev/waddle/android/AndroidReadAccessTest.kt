package dev.waddle.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read only public settings; never adopt shell identity or query user secrets. */
@RunWith(AndroidJUnit4::class)
class AndroidReadAccessTest {
    @Test fun publicSettingsAreReadableThroughTheAppApi() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(context.applicationInfo.uid, Process.myUid())
        for (permission in listOf(
            "android.permission.DUMP",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.INTERACT_ACROSS_USERS",
        )) {
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))
        }
        val resolver = context.contentResolver
        val values = linkedMapOf(
            "brightness" to Settings.System.getString(resolver, Settings.System.SCREEN_BRIGHTNESS),
            "brightness_mode" to Settings.System.getString(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE),
            "screen_timeout_ms" to Settings.System.getString(resolver, Settings.System.SCREEN_OFF_TIMEOUT),
            "airplane_mode" to Settings.Global.getString(resolver, Settings.Global.AIRPLANE_MODE_ON),
            "accessibility_enabled" to Settings.Secure.getString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED),
        )
        for ((key, value) in values) assertNotNull("Missing public setting: $key", value)
        Log.i("WaddleAccessProbe", JSONObject(values).put("uid", Process.myUid()).toString())
        // A public fixture for a separate Termux UID access test, never a secret.
        File(context.filesDir, "access-probe-public.txt").writeText("WADDLE_OTHER_APP_PUBLIC_FIXTURE\n")
    }
}
