package dev.waddle.android.ui

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.waddle.android.terminal.TerminalLog

class InputState {
    var control by mutableStateOf(false)
    var fontSize by mutableStateOf(14f)
}

class TerminalInput(private val view: TerminalView, private val input: InputState) :
    TerminalLog(), TerminalViewClient {
    override fun onScale(scale: Float): Float {
        if (scale > 1.1f || scale < 0.9f) {
            input.fontSize = (input.fontSize + if (scale > 1) 1 else -1).coerceIn(10f, 24f)
            return 1f
        }
        return scale
    }
    override fun onSingleTapUp(e: MotionEvent) {
        view.requestFocus()
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
    override fun readControlKey() = input.control
    override fun readAltKey() = false
    override fun readShiftKey() = false
    override fun readFnKey() = false
    override fun shouldBackButtonBeMappedToEscape() = false
    override fun shouldEnforceCharBasedInput() = false
    override fun shouldUseCtrlSpaceWorkaround() = false
    override fun isTerminalViewSelected() = true
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        input.control = false
        return false
    }
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession) = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        if (!KeyEvent.isModifierKey(keyCode)) input.control = false
        return false
    }
    override fun onLongPress(event: MotionEvent) = false
    override fun copyModeChanged(copyMode: Boolean) = Unit
    override fun onEmulatorSet() = Unit
}
