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
            return from(runtime.running, runtime.cameraReady, runtime.visionReady, runtime.visionError,
                runtime.controlMode, runtime.foregroundSafety, accessibility, gestures || pointerClicking)
        }

        fun from(running: Boolean, cameraReady: Boolean, visionReady: Boolean, visionError: String?,
            mode: ControlMode, safety: ForegroundSafety, accessibility: Boolean, channelEnabled: Boolean): ProductSession {
            val armed = mode == ControlMode.ARMED
            val healthy = running && cameraReady && visionReady && visionError == null
            val safe = safety == ForegroundSafety.SAFE
            return ProductSession(when {
                !running -> ProductActionState.STOPPED
                !channelEnabled -> ProductActionState.DISABLED
                mode == ControlMode.PAUSED || (armed && (!safe || !healthy || !accessibility)) -> ProductActionState.PAUSED
                armed -> ProductActionState.ARMED
                else -> ProductActionState.DISARMED
            }, armed || (healthy && accessibility && safe && channelEnabled))
        }
    }
}
