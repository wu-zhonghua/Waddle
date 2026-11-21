package dev.waddle.android.codex

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

data class DisplayState(val brightness: Int, val mode: Int) {
    fun json() = JSONObject().put("brightness", brightness).put("brightness_mode", mode)
}

data class DisplayRequest(val setting: String, val value: Int) {
    init {
        require((setting == "brightness" && value in 1..255) ||
            (setting == "brightness_mode" && value in 0..1)) { "只允许亮度 1–255 或亮度模式 0/1。" }
    }
}

data class DisplayChange(val request: DisplayRequest, val before: DisplayState) {
    val description: String get() = if (request.setting == "brightness")
        "系统屏幕亮度：${before.brightness} → ${request.value}（原始值 1–255）\n将影响整个设备的屏幕亮度。"
    else "亮度模式：${if (before.mode == 0) "手动" else "自动"} → ${if (request.value == 0) "手动" else "自动"}" +
        if (request.value == 1) "\n开启后，实际亮度由系统根据环境调节。" else "\n关闭自动调节，保留当前亮度。"
}

/** Two allowlisted Settings.System writes. Planning never writes; the controller owns consent. */
class NativeDisplaySettingsTool(private val context: Context) {
    companion object {
        const val NAME = "waddle_set_display_setting"
        fun specification() = JSONObject().put("type", "function").put("name", NAME)
            .put("description", "Change one public Android display setting through Waddle. Requires Android write-settings permission, the Waddle display-write toggle, and explicit confirmation for EACH call. setting=brightness uses an integer raw value 1..255 and requires manual brightness mode first; setting=brightness_mode uses 0=manual or 1=automatic. Do not silently change modes. For a brightness request while automatic, request brightness_mode=0 first only if the user wants manual brightness. No other settings, shell commands or arbitrary URIs. Results include before/after and read-back verification. A denied call must not be retried or bypassed with shell.")
            .put("inputSchema", JSONObject().put("type", "object").put("additionalProperties", false)
                .put("required", JSONArray().put("setting").put("value"))
                .put("properties", JSONObject()
                    .put("setting", JSONObject().put("type", "string").put("enum", JSONArray().put("brightness").put("brightness_mode")))
                    .put("value", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 255))))

        fun parse(arguments: JSONObject): DisplayRequest {
            require(arguments.keys().asSequence().toSet() == setOf("setting", "value")) { "只接受 setting 和 value。" }
            val setting = arguments.get("setting")
            val value = arguments.get("value")
            require(setting is String && (value is Int || value is Long)) { "setting 必须为字符串，value 必须为整数。" }
            val number = (value as Number).toLong()
            require(number in 0..255) { "设置值超出范围。" }
            return DisplayRequest(setting as String, number.toInt())
        }
    }

    fun canWrite() = Settings.System.canWrite(context)
    fun state(): DisplayState = DisplayState(
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS),
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE),
    )

    fun plan(arguments: JSONObject): DisplayChange {
        val request = parse(arguments)
        check(canWrite()) { "请点击 Codex 页“允许修改亮度”旁的“系统授权”，在 Android 设置中允许 Waddle 修改系统设置，再返回打开修改开关。" }
        val before = state()
        check(request.setting != "brightness" || before.mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
            "当前为自动亮度；设置固定亮度前需由用户确认切换为手动模式。"
        }
        return DisplayChange(request, before)
    }

    fun apply(change: DisplayChange): JSONObject {
        check(canWrite()) { "Android 修改系统设置权限已撤销。" }
        val request = change.request
        val current = state()
        // Automatic brightness can vary while confirming a MODE change. Only the
        // approved mode must still match; fixed brightness also requires a stable value.
        check(current.mode == change.before.mode && (request.setting != "brightness" || current.brightness == change.before.brightness)) {
            "设置在确认期间发生变化，本次未修改。请重新请求并确认。"
        }
        check(request.setting != "brightness" || current.mode == 0) { "当前不是手动亮度模式。" }
        val name = if (request.setting == "brightness") Settings.System.SCREEN_BRIGHTNESS else Settings.System.SCREEN_BRIGHTNESS_MODE
        val previous = if (request.setting == "brightness") current.brightness else current.mode
        if (previous != request.value) check(Settings.System.putInt(context.contentResolver, name, request.value)) { "系统拒绝写入设置。" }
        val after = state()
        val verified = (if (request.setting == "brightness") after.brightness else after.mode) == request.value
        return JSONObject().put("source", "Waddle Android settings API").put("readOnly", false)
            .put("setting", request.setting).put("requestedValue", request.value)
            .put("before", current.json()).put("values", after.json()).put("changed", previous != request.value)
            .put("verified", verified).put("capturedAtMs", System.currentTimeMillis())
    }
}
