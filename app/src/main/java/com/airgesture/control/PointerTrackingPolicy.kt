package com.airgesture.control

internal object PointerTrackingPolicy {
    fun isActive(
        pointerEnabled: Boolean,
        controlMode: ControlMode,
        foregroundSafety: ForegroundSafety
    ): Boolean = pointerEnabled && controlMode == ControlMode.ARMED &&
        foregroundSafety == ForegroundSafety.SAFE
}

