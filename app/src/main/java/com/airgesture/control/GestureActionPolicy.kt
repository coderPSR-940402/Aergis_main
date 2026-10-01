package com.airgesture.control

/** Defines which pointer-originated actions are controlled by the gesture-actions setting. */
object GestureActionPolicy {
    fun isEnabled(gesturesEnabled: Boolean, action: AirAction): Boolean {
        if (!gesturesEnabled) return false
        return when (action) {
            AirAction.TAP,
            AirAction.SCROLL_UP,
            AirAction.SCROLL_DOWN -> true
            else -> false
        }
    }
}
