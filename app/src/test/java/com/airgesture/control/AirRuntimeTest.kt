package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirRuntimeTest {
    @Test
    fun pointerStateIsClampedAndPublishedAsOneSnapshot() {
        AirRuntime.setPointerState(-0.5f, 1.5f, tracking = true)

        val state = AirRuntime.state.value
        assertEquals(0f, state.pointerX, 0f)
        assertEquals(1f, state.pointerY, 0f)
        assertTrue(state.pointerTracking)
        assertEquals(state.pointer, AirRuntime.pointerSnapshot())

        AirRuntime.setPointerState(0f, 0f, tracking = false)
    }

    @Test
    fun runtimePropertyUpdatesArePublishedToObservers() {
        AirRuntime.running = true
        assertTrue(AirRuntime.state.value.running)

        AirRuntime.running = false
        assertFalse(AirRuntime.state.value.running)
    }

    @Test
    fun actionEpochChangesForSafetyStateButNotPointerMovement() {
        AirRuntime.controlMode = ControlMode.OFF
        val initialEpoch = AirRuntime.actionEpoch

        AirRuntime.setPointerState(0.2f, 0.3f, tracking = true)
        assertEquals(initialEpoch, AirRuntime.actionEpoch)

        AirRuntime.controlMode = ControlMode.READY
        assertTrue(AirRuntime.actionEpoch > initialEpoch)

        val readyEpoch = AirRuntime.actionEpoch
        AirRuntime.gesturesEnabled = false
        assertTrue(AirRuntime.actionEpoch > readyEpoch)

        AirRuntime.gesturesEnabled = true
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.setPointerState(0f, 0f, tracking = false)
    }

    @Test
    fun visionTelemetryCountsAcceptedAndRejectedReasons() {
        AirRuntime.resetVisionTelemetry()
        val policy = VisionResultFreshnessPolicy(maxAgeMs = 20L, maxGapMs = 30L)

        AirRuntime.recordVisionResult(policy.evaluate(100L, 105L))
        AirRuntime.recordVisionResult(policy.evaluate(100L, 106L))
        AirRuntime.recordVisionResult(policy.evaluate(200L, 205L))

        val telemetry = AirRuntime.state.value.visionTelemetry
        assertEquals(3L, telemetry.totalResults)
        assertEquals(1L, telemetry.acceptedResults)
        assertEquals(2L, telemetry.rejectedResults)
        assertEquals(1L, telemetry.duplicateResults)
        assertEquals(1L, telemetry.excessiveGapResults)
        assertEquals("EXCESSIVE_GAP", telemetry.lastRejectionReason)

        AirRuntime.resetVisionTelemetry()
    }

    @Test
    fun uiStateDoesNotChangeForPointerCoordinatesWhenTrackingStateIsUnchanged() {
        AirRuntime.setPointerState(0.1f, 0.2f, tracking = true)
        val first = AirRuntime.uiStateSnapshot()

        AirRuntime.setPointerState(0.8f, 0.9f, tracking = true)

        assertEquals(first, AirRuntime.uiStateSnapshot())
        AirRuntime.setPointerState(0f, 0f, tracking = false)
    }
}
