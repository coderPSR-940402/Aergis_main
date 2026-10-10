package com.airgesture.control

import com.airgesture.control.filtering.PoseGeometryEvidence
import com.airgesture.control.filtering.PoseGeometryRejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirRuntimeTest {
    @Test
    fun pointerToggleInvalidatesQueuedAndHeldActionsOnlyWhenChanged() {
        AirRuntime.pointerEnabled = true
        val before = AirRuntime.actionEpoch
        AirRuntime.pointerEnabled = false
        assertTrue(AirRuntime.actionEpoch > before)
        val disabled = AirRuntime.actionEpoch
        AirRuntime.pointerEnabled = false
        assertEquals(disabled, AirRuntime.actionEpoch)
        AirRuntime.pointerEnabled = true
    }

    @Test
    fun motionMagnitudeUpdatesWithoutInvalidatingActionsForUnchangedGate() {
        AirRuntime.setMotionState(MotionState(false, .1f, null))
        val epoch = AirRuntime.actionEpoch
        AirRuntime.setMotionState(MotionState(false, .7f, null))
        assertEquals(.7f, AirRuntime.state.value.motionMagnitude, 0f)
        assertEquals(epoch, AirRuntime.actionEpoch)
    }

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
    fun rawPointerStateIsClampedAndIndependentFromMappedPointer() {
        AirRuntime.setPointerState(0.8f, 0.9f, tracking = true)
        AirRuntime.setRawPointerState(-1f, 2f, tracking = true)

        assertEquals(0.8f, AirRuntime.pointerSnapshot().x, 0f)
        assertEquals(0.9f, AirRuntime.pointerSnapshot().y, 0f)
        assertEquals(0f, AirRuntime.rawPointerSnapshot().x, 0f)
        assertEquals(1f, AirRuntime.rawPointerSnapshot().y, 0f)

        AirRuntime.setPointerState(0f, 0f, tracking = false)
        AirRuntime.setRawPointerState(0f, 0f, tracking = false)
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
    fun actionDispatchTelemetryCountsEachPlatformOutcome() {
        AirRuntime.resetActionDispatchTelemetry()

        AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.POLICY_DENIED)
        AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.PLATFORM_REJECTED)
        AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.COMPLETED)
        AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.CANCELLED)

        val telemetry = AirRuntime.state.value.actionDispatchTelemetry
        assertEquals(4L, telemetry.totalOutcomes)
        assertEquals(1L, telemetry.policyDenied)
        assertEquals(1L, telemetry.platformRejected)
        assertEquals(1L, telemetry.completed)
        assertEquals(1L, telemetry.cancelled)
        assertEquals(ActionDispatchOutcome.CANCELLED, telemetry.lastOutcome)

        AirRuntime.resetActionDispatchTelemetry()
    }

    @Test
    fun poseEvidenceIsPublishedToUiStateAsAggregateData() {
        val evidence = PoseGeometryEvidence(
            landmarkCount = 21,
            finiteLandmarkCount = 21,
            normalizedPalmWidth = 0.2f,
            indexBoneToPalmRatio = 0.8f,
            fingertipToPalmRatio = 0.1f,
            accepted = true,
            rejectionReason = PoseGeometryRejectionReason.NONE
        )

        AirRuntime.recordPoseEvidence(evidence)

        assertEquals(evidence, AirRuntime.uiStateSnapshot().poseEvidence)
        assertEquals(21, AirRuntime.uiStateSnapshot().poseEvidence?.finiteLandmarkCount)
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

