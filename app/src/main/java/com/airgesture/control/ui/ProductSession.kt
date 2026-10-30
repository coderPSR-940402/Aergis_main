package com.airgesture.control.ui

import com.airgesture.control.AirRuntimeUiState
import com.airgesture.control.ControlMode
import com.airgesture.control.ForegroundSafety

internal enum class ProductActionState { STOPPED, DISABLED, DISARMED, ARMED, PAUSED }

/** Presentation only. The engine's dispatch/epoch policies remain authoritative. */
internal data class ProductSession(val actions: ProductActionState, val canToggleArm: Boolean) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun from(runtime: AirRuntimeUiState, accessibility: Boolean, gestures: Boolean,
            pointer: Boolean, pointerClicking: Boolean): ProductSession {
            val channelEnabled = gestures || pointerClicking
            val armed = runtime.controlMode == ControlMode.ARMED
            val healthy = runtime.running && runtime.cameraReady && runtime.visionReady && runtime.visionError == null
            val safe = runtime.foregroundSafety == ForegroundSafety.SAFE
            return ProductSession(when {
                !runtime.running -> ProductActionState.STOPPED
                !channelEnabled -> ProductActionState.DISABLED
                runtime.controlMode == ControlMode.PAUSED || (armed && (!safe || !healthy || !accessibility)) -> ProductActionState.PAUSED
                armed -> ProductActionState.ARMED
                else -> ProductActionState.DISARMED
            }, armed || (healthy && accessibility && safe && channelEnabled))
        }
    }
}
