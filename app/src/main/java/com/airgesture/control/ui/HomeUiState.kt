package com.airgesture.control.ui

import com.airgesture.control.AirRuntimeUiState
import com.airgesture.control.ControlMode
import com.airgesture.control.ForegroundSafety
import com.airgesture.control.PointerFeedback

/** Only user-visible state changes invalidate Control; frame counters stay in Tracking. */
internal data class HomeUiState(
    val running: Boolean,
    val cameraReady: Boolean,
    val visionReady: Boolean,
    val visionError: String?,
    val handsDetected: Int,
    val handedness: String,
    val pointerTracking: Boolean,
    val pointerFeedback: PointerFeedback,
    val controlMode: ControlMode,
    val motionActive: Boolean,
    val foregroundSafety: ForegroundSafety,
    val safetyReason: String
) {
    fun status(accessibilityEnabled: Boolean, pointerEnabled: Boolean = true): HomeStatus = when {
        !accessibilityEnabled -> HomeStatus.SETUP_REQUIRED
        !running -> HomeStatus.IDLE
        visionError != null -> HomeStatus.ERROR
        !cameraReady -> HomeStatus.CAMERA_STARTING
        !visionReady -> HomeStatus.VISION_STARTING
        controlMode == ControlMode.PAUSED || motionActive ||
            (controlMode == ControlMode.ARMED && foregroundSafety != ForegroundSafety.SAFE) -> HomeStatus.PAUSED
        !pointerEnabled || pointerFeedback == PointerFeedback.DISABLED -> HomeStatus.POINTER_PAUSED
        pointerTracking -> HomeStatus.TRACKING
        else -> HomeStatus.SEARCHING
    }

    companion object {
        fun from(runtime: AirRuntimeUiState) = HomeUiState(
            runtime.running, runtime.cameraReady, runtime.visionReady, runtime.visionError,
            runtime.handsDetected, runtime.handedness, runtime.pointerTracking, runtime.pointerFeedback,
            runtime.controlMode, runtime.motionActive, runtime.foregroundSafety, runtime.safetyReason
        )
    }
}

internal enum class HomeStatus {
    SETUP_REQUIRED, IDLE, ERROR, CAMERA_STARTING, VISION_STARTING, PAUSED, POINTER_PAUSED, TRACKING, SEARCHING
}
