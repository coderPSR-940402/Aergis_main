package com.airgesture.control.ui

import com.airgesture.control.*
import org.junit.Assert.*
import org.junit.Test

class HomeUiStateTest {
    private fun ready() = AirRuntime.uiStateSnapshot().copy(
        running = true, cameraReady = true, visionReady = true, visionError = null,
        handsDetected = 0, handedness = "Unknown", pointerTracking = false,
        pointerFeedback = PointerFeedback.NO_HAND, controlMode = ControlMode.READY,
        motionActive = false, foregroundSafety = ForegroundSafety.SAFE, safetyReason = "Allowed"
    )

    @Test fun cameraReadyWithoutHandDoesNotClaimTracking() {
        assertEquals(HomeStatus.SEARCHING, HomeUiState.from(ready()).status(true))
    }

    @Test fun detectedHandWithoutUsablePointerDoesNotClaimTracking() {
        assertEquals(HomeStatus.SEARCHING, HomeUiState.from(ready().copy(handsDetected = 1)).status(true))
    }

    @Test fun lostHandReplacesLiveTrackingStatus() {
        val live = ready().copy(pointerTracking = true, pointerFeedback = PointerFeedback.TRACKING)
        assertEquals(HomeStatus.TRACKING, HomeUiState.from(live).status(true))
        assertEquals(HomeStatus.SEARCHING, HomeUiState.from(live.copy(
            pointerTracking = false, pointerFeedback = PointerFeedback.COASTING
        )).status(true))
    }

    @Test fun armedSafetyPauseDoesNotLookReadyToExecute() {
        for (safety in listOf(ForegroundSafety.PROTECTED, ForegroundSafety.UNKNOWN)) {
            assertEquals(HomeStatus.PAUSED, HomeUiState.from(ready().copy(
                controlMode = ControlMode.ARMED, foregroundSafety = safety, pointerTracking = true
            )).status(true))
        }
        assertEquals(HomeStatus.PAUSED, HomeUiState.from(ready().copy(
            controlMode = ControlMode.ARMED, motionActive = true
        )).status(true))
    }

    @Test fun missingAccessibilityAndStoppedCameraHaveDistinctStates() {
        assertEquals(HomeStatus.SETUP_REQUIRED, HomeUiState.from(ready()).status(false))
        assertEquals(HomeStatus.IDLE, HomeUiState.from(ready().copy(running = false)).status(true))
        assertEquals(HomeStatus.CAMERA_STARTING, HomeUiState.from(ready().copy(cameraReady = false)).status(true))
        assertEquals(HomeStatus.VISION_STARTING, HomeUiState.from(ready().copy(visionReady = false)).status(true))
    }

    @Test fun actualVisionErrorIsVisibleAndPointerDisabledIsExplicit() {
        assertEquals(HomeStatus.ERROR, HomeUiState.from(ready().copy(visionError = "Model failed")).status(true))
        assertEquals(HomeStatus.POINTER_PAUSED, HomeUiState.from(ready().copy(
            pointerFeedback = PointerFeedback.DISABLED
        )).status(true))
        assertEquals(HomeStatus.POINTER_PAUSED, HomeUiState.from(ready().copy(
            pointerTracking = true, pointerFeedback = PointerFeedback.TRACKING
        )).status(accessibilityEnabled = true, pointerEnabled = false))
    }

    @Test fun telemetryOnlyChangesCannotInvalidateHome() {
        val initial = ready()
        val changed = initial.copy(
            visionTelemetry = initial.visionTelemetry.copy(totalResults = 120, acceptedResults = 118),
            actionDispatchTelemetry = initial.actionDispatchTelemetry.copy(totalOutcomes = 4),
            lastGesture = "Thumb_Up"
        )
        assertEquals(HomeUiState.from(initial), HomeUiState.from(changed))
    }
}
