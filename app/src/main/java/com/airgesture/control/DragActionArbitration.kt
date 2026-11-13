package com.airgesture.control

/** Preserve native-touch exclusivity without trapping primary gesture navigation. */
internal object DragActionArbitration {
    enum class Decision { ALLOW, CANCEL_THEN_ALLOW, REJECT }

    fun decide(dragBusy: Boolean, action: AirAction): Decision {
        if (!dragBusy || action == AirAction.POINTER_DRAG ||
            action == AirAction.TOGGLE_POINTER || action == AirAction.CALIBRATE_POINTER)
            return Decision.ALLOW

        // An active drag can own the native touch stream for many seconds.
        // Back/Home/Recents and system panels are non-coordinate global actions.
        // Cancel the drag, then execute the one requested navigation command.
        if (action == AirAction.BACK || action == AirAction.HOME ||
            action == AirAction.RECENTS || action == AirAction.NOTIFICATIONS ||
            action == AirAction.QUICK_SETTINGS)
            return Decision.CANCEL_THEN_ALLOW

        return Decision.REJECT
    }
}
