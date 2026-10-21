package com.airgesture.control

import com.airgesture.control.filtering.PoseGeometryEvidence
import com.airgesture.control.filtering.PoseGeometryRejectionReason
import org.junit.Assert.*
import org.junit.Test

class ClassifierGeometryInterruptionTest {
    // recording(7), CI #274, elapsed 70.500–70.939 s. Actual camera timestamps and
    // scores; no private image/landmark data. BACK is an explicit test assignment,
    // since the recording does not include the device's saved mapping.
    @Test fun recordedNeutralGeometryTransitionThenVictoryDispatchesOnce() {
        val gate = GestureTransactionStateMachine()
        assertNull(sample(gate, 23676555L, score = .8814619f))
        assertNull(sample(gate, 23676655L, score = .8743201f))
        assertEquals(GestureTransactionStateMachine.State.NEUTRAL, gate.currentState())
        assertNull(sample(gate, 23676788L, rejected = true, score = .89583886f))
        assertEquals(AirAction.BACK, sample(gate, 23676888L, AirAction.BACK, score = .7461522f))
        assertNull(sample(gate, 23676988L, AirAction.BACK, score = .7870305f))
    }

    @Test fun rejectedGeometryCannotDispatchEvenWhenReady() {
        val gate = ready()
        assertNull(sample(gate, 150L, AirAction.BACK, rejected = true))
        assertEquals(AirAction.BACK, sample(gate, 200L, AirAction.BACK))
    }

    @Test fun rejectedGeometryCannotRearmConsumedPoseOrFinishPartialNeutral() {
        val gate = ready()
        assertEquals(AirAction.BACK, sample(gate, 150L, AirAction.BACK))
        sample(gate, 200L)
        sample(gate, 250L, rejected = true)
        sample(gate, 300L)
        assertNull(sample(gate, 350L, AirAction.BACK))
        sample(gate, 400L); sample(gate, 500L)
        assertEquals(AirAction.BACK, sample(gate, 550L, AirAction.BACK))
    }

    @Test fun continuousRejectedFramesCannotDispatchOrConsumeFirstOccurrence() {
        val gate = ready()
        for (t in 150L..450L step 50L) assertNull(sample(gate, t, rejected = true))
        assertEquals(AirAction.BACK, sample(gate, 500L, AirAction.BACK))
        assertNull(sample(gate, 525L, AirAction.BACK))
        sample(gate, 550L); sample(gate, 650L)
        assertEquals(AirAction.BACK, sample(gate, 700L, AirAction.BACK))
    }

    @Test fun firstOccurrenceRemainsEligibleAfterEvidenceGap() {
        for (delay in listOf(250L, 251L)) {
            val gate = ready()
            sample(gate, 250L, rejected = true)
            val result = sample(gate, 100L + delay, AirAction.BACK)
            assertEquals("total evidence gap $delay", AirAction.BACK, result)
        }
    }

    @Test fun geometryRejectionCannotConsumeFirstOccurrenceOrRearmConsumedOne() {
        for (consumed in listOf(false, true)) {
            val gate = if (consumed) ready() else GestureTransactionStateMachine()
            if (consumed) assertEquals(AirAction.BACK, sample(gate, 150L, AirAction.BACK))
            sample(gate, 200L, rejected = true)
            assertEquals(if (consumed) null else AirAction.BACK, sample(gate, 250L, AirAction.BACK))
            assertNull(sample(gate, 300L, AirAction.BACK))
        }
    }

    @Test fun ownerEpochAndTrackingLossCannotReplayConsumedOccurrence() {
        for (boundary in listOf("owner", "epoch", "tracking")) {
            val gate = ready()
            assertEquals(AirAction.BACK, sample(gate, 150L, AirAction.BACK))
            val frame = input(200L, rejected = true)
            gate.process(when (boundary) {
                "owner" -> frame.copy(ownershipId = "new")
                "epoch" -> frame.copy(actionEpoch = 1L)
                else -> frame.copy(tracking = false)
            })
            assertNull(sample(gate, 250L, AirAction.BACK))
        }
    }

    @Test fun onlyVisibleSameHandRatioRejectionCanRetainReadiness() {
        for (reason in PoseGeometryRejectionReason.entries) {
            val pose = evidence(reason)
            assertFalse("Invalid evidence cannot qualify for command dispatch", CommandTrackingEligibility.isEligible(
                true, true, true, true, pose))
            assertEquals(reason.name, reason == PoseGeometryRejectionReason.EXCESSIVE_INDEX_BONE,
                CommandTrackingEligibility.canRetainReadiness(true, true, true, true, pose))
        }
        val ratio = evidence(PoseGeometryRejectionReason.EXCESSIVE_INDEX_BONE)
        assertFalse(CommandTrackingEligibility.canRetainReadiness(false, true, true, true, ratio))
        assertFalse(CommandTrackingEligibility.canRetainReadiness(true, false, true, true, ratio))
        assertFalse(CommandTrackingEligibility.canRetainReadiness(true, true, true, false, ratio))
        assertFalse(CommandTrackingEligibility.canRetainReadiness(true, true, true, true, null))
    }

    private fun ready() = GestureTransactionStateMachine().also {
        sample(it, 0L); sample(it, 100L)
    }
    private fun sample(gate: GestureTransactionStateMachine, t: Long, action: AirAction = AirAction.NONE,
        rejected: Boolean = false, score: Float = .99f) = gate.process(input(t, action, rejected, score))

    private fun input(t: Long, action: AirAction = AirAction.NONE, rejected: Boolean = false,
        score: Float = .99f): GestureTransactionStateMachine.Input {
        val pose = if (rejected) evidence(PoseGeometryRejectionReason.EXCESSIVE_INDEX_BONE)
            else evidence(PoseGeometryRejectionReason.NONE).copy(accepted = true)
        val valid = CommandTrackingEligibility.isEligible(true, true, true, true, pose)
        return GestureTransactionStateMachine.Input(action, score, t,
            valid || CommandTrackingEligibility.canRetainReadiness(true, true, true, true, pose),
            "Left:6", if (action == AirAction.NONE) null else "VICTORY", evidenceValid = valid)
    }
    private fun evidence(reason: PoseGeometryRejectionReason) = PoseGeometryEvidence(
        21, 21, .13f, 1.5f, .9f, false, reason)
}
