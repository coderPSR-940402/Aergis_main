package com.airgesture.control

/** Separates pointer-originated actions from classifier-mapped system actions. */
object GestureActionPolicy {
    /** The same mapped click selects the highlighted item when coordinate aiming is off. */
    fun resolve(action: AirAction, pointerControlActive: Boolean): AirAction =
        if (action == AirAction.TAP && !pointerControlActive) AirAction.SELECT_ITEM else action
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

    fun isClassifierActionEnabled(gesturesEnabled: Boolean, action: AirAction,
        pointerControlActive: Boolean = false, gesture: RegisteredGesture? = null): Boolean {
        if (!gesturesEnabled || action == AirAction.NONE) return false
        // Pointer aiming commonly resembles Pointing_Up. Its legacy double-tap
        // mapping must not compete with a deliberate pinch touch session.
        if (!action.usesPointerTouch) return true
        // Unidentified legacy callers retain their conservative policy. Production
        // callers supply the stable identity; coordinate commands need a visible pointer.
        if (gesture == null) return !pointerControlActive
        return pointerControlActive && gesture != RegisteredGesture.POINTING_UP
    }
}
