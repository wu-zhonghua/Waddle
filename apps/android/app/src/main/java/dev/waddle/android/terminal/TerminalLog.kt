package dev.waddle.android.terminal

import android.util.Log

open class TerminalLog {
    fun logError(tag: String, message: String) { Log.e(tag, message) }
    fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    fun logStackTrace(tag: String, e: Exception) { Log.e(tag, "Terminal error", e) }
}
