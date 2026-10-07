package com.airgesture.control

internal object PointerTrackingPolicy {
    // Device motion blocks actions, but must not hide valid pointer feedback.
    @Suppress("UNUSED_PARAMETER")
    fun isActive(
        pointerEnabled: Boolean,
        controlMode: ControlMode,
        motionActive: Boolean,
        foregroundSafety: ForegroundSafety
    ): Boolean = pointerEnabled && controlMode == ControlMode.ARMED &&
        foregroundSafety == ForegroundSafety.SAFE
}
