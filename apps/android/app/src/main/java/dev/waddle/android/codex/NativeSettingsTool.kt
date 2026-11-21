package dev.waddle.android.codex

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/** A closed, read-only API: no arbitrary setting names, URIs, files or writes. */
class NativeSettingsTool(private val context: Context) {
    companion object {
        const val NAME = "waddle_read_system_settings"
        val KEYS = listOf("brightness", "brightness_mode", "screen_timeout_ms", "airplane_mode", "accessibility_enabled")
        fun specification() = JSONObject().put("type", "function").put("name", NAME)
            .put("description", "Read current public Android settings through Waddle native APIs. Read only. brightness is the system's RAW brightness value, NOT the Settings slider percentage: do not convert it to a percentage by dividing by 255; the UI scale can be nonlinear. brightness_mode is 0 manual / 1 automatic. screen_timeout_ms is the stored timeout in milliseconds; custom values may appear as Not set in the system UI rather than a standard choice. airplane_mode and accessibility_enabled are 0 off / 1 on. Report stored values accurately without claiming to have inspected the Settings screen. Cannot change settings or inspect screens.")
            .put("inputSchema", JSONObject().put("type", "object").put("additionalProperties", false)
                .put("required", JSONArray().put("keys"))
                .put("properties", JSONObject().put("keys", JSONObject().put("type", "array")
                    .put("minItems", 1).put("maxItems", KEYS.size).put("uniqueItems", true)
                    .put("items", JSONObject().put("type", "string").put("enum", JSONArray(KEYS))))))
    }

    fun read(arguments: JSONObject): JSONObject {
        require(arguments.keys().asSequence().toSet() == setOf("keys")) { "Only keys is accepted" }
        val requested = arguments.optJSONArray("keys") ?: error("keys must be an array")
        require(requested.length() in 1..KEYS.size) { "Request one to five settings" }
        val names = (0 until requested.length()).map { index ->
            val value = requested.get(index)
            require(value is String && value in KEYS) { "Unsupported setting" }
            value as String
        }
        require(names.distinct().size == names.size) { "Duplicate settings" }
        val resolver = context.contentResolver
        val values = JSONObject()
        for (key in names) {
            val value = when (key) {
                "brightness" -> Settings.System.getString(resolver, Settings.System.SCREEN_BRIGHTNESS)
                "brightness_mode" -> Settings.System.getString(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
                "screen_timeout_ms" -> Settings.System.getString(resolver, Settings.System.SCREEN_OFF_TIMEOUT)
                "airplane_mode" -> Settings.Global.getString(resolver, Settings.Global.AIRPLANE_MODE_ON)
                "accessibility_enabled" -> Settings.Secure.getString(resolver, Settings.Secure.ACCESSIBILITY_ENABLED)
                else -> error("Unsupported setting")
            }
            values.put(key, value?.toLongOrNull() ?: JSONObject.NULL)
        }
        return JSONObject().put("source", "Waddle Android settings API").put("readOnly", true)
            .put("values", values).put("capturedAtMs", System.currentTimeMillis())
    }
}
