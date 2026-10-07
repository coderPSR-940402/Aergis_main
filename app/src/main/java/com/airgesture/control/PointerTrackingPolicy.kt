package com.airgesture.control

internal object PointerTrackingPolicy {
    fun isActive(
        pointerEnabled: Boolean,
        controlMode: ControlMode,
        motionActive: Boolean,
        foregroundSafety: ForegroundSafety
    ): Boolean = pointerEnabled && controlMode == ControlMode.ARMED &&
        !motionActive && foregroundSafety == ForegroundSafety.SAFE
}
