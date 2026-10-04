package com.airgesture.control

/** Separates pointer-originated actions from classifier-mapped system actions. */
object GestureActionPolicy {
    /** Compatibility alias for callers that authorize pointer-originated actions. */
    fun isEnabled(gesturesEnabled: Boolean, action: AirAction): Boolean {
        return isPointerActionEnabled(gesturesEnabled, action)
    }

    fun isPointerActionEnabled(gesturesEnabled: Boolean, action: AirAction): Boolean {
        if (!gesturesEnabled) return false
        return when (action) {
            AirAction.TAP,
            AirAction.SCROLL_UP,
            AirAction.SCROLL_DOWN -> true
            else -> false
        }
    }

    fun isClassifierActionEnabled(gesturesEnabled: Boolean, action: AirAction): Boolean {
        return gesturesEnabled && action != AirAction.NONE
    }
}
