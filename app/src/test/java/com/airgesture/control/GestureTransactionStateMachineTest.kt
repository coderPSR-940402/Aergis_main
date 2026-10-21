package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class GestureTransactionStateMachineTest {
    @Test fun firstValidOccurrenceDoesNotRequirePrimingNeutral() {
        val gate = GestureTransactionStateMachine()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 0L)))
        assertEquals(GestureTransactionStateMachine.State.COOLDOWN, gate.currentState())
    }

    @Test fun resetBeforeFirstDispatchKeepsFirstOccurrenceEligible() {
        val gate = GestureTransactionStateMachine()
        gate.reset()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
    }

    @Test fun recognitionHasNoHoldDelayAt15_30_60Fps() {
        for (fps in listOf(15, 30, 60)) {
            val gate = ready()
            val fired = (0..fps).mapNotNull { frame ->
                val time = 150L + frame * 1000L / fps
                gate.process(input(AirAction.BACK, time))?.let { time }
            }
            assertEquals("one command immediately at $fps FPS", listOf(150L), fired)
        }
    }

    @Test fun weakBriefGestureCannotFireAtHighFrameRate() {
        val gate = ready()
        for (time in 150L..240L step 10L)
            assertNull(gate.process(input(AirAction.HOME, time).copy(confidence = .1f)))
    }

    @Test fun persistentPoseAndBriefNeutralExcursionCannotRepeat() {
        val gate = ready()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
        for (time in 200L..1000L step 50L) assertNull(gate.process(input(AirAction.BACK, time)))
        assertNull(gate.process(input(AirAction.NONE, 1050L)))
        assertNull(gate.process(input(AirAction.NONE, 1100L)))
        assertNull(gate.process(input(AirAction.BACK, 1150L)))
        assertNull(gate.process(input(AirAction.NONE, 1200L)))
        assertNull(gate.process(input(AirAction.NONE, 1300L)))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 1350L)))
    }

    @Test fun neutralRearmDoesNotRequireAnAdditionalCooldown() {
        val gate = ready()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
        assertNull(gate.process(input(AirAction.NONE, 200L)))
        assertNull(gate.process(input(AirAction.NONE, 300L)))
        assertEquals(AirAction.HOME, gate.process(input(AirAction.HOME, 350L)))
    }

    @Test fun missingTrackingAndWeakEvidenceDoNotRearmHeldCommand() {
        for (invalid in listOf(
            input(AirAction.NONE, 0L).copy(tracking = false),
            input(AirAction.BACK, 0L).copy(confidence = 0.1f),
            input(AirAction.NONE, 0L).copy(confidence = 0.1f),
            input(AirAction.NONE, 0L).copy(ownershipId = null)
        )) {
            val gate = ready()
            assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
            for (time in 200L..1000L step 50L) assertNull(gate.process(invalid.copy(timestampMs = time)))
            for (time in 1050L..1300L step 50L) assertNull(gate.process(input(AirAction.BACK, time)))
        }
    }

    @Test fun ownershipChangeWhileReadyDoesNotConsumeFirstOccurrence() {
        val gate = ready()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L, "left")))
        for (time in 200L..350L step 50L) assertNull(gate.process(input(AirAction.BACK, time, "left")))
    }

    @Test fun staleGapCannotCompleteNeutralOrReplayCommand() {
        val gate = ready()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
        gate.process(input(AirAction.NONE, 200L))
        gate.process(input(AirAction.NONE, 1000L))
        assertNull(gate.process(input(AirAction.BACK, 1050L)))
        for (time in 2000L..2300L step 50L) assertNull(gate.process(input(AirAction.BACK, time)))
    }

    @Test fun engineResetPreservesReleaseRequirementAfterDispatch() {
        val gate = ready()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
        gate.reset()
        for (time in 1000L..1300L step 50L) assertNull(gate.process(input(AirAction.BACK, time)))
        gate.process(input(AirAction.NONE, 1350L))
        gate.process(input(AirAction.NONE, 1450L))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 1500L)))
    }

    @Test fun invalidConfidenceDoesNotConsumeReadinessAndUnknownOwnerCannotFire() {
        for (confidence in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f)) {
            val gate = GestureTransactionStateMachine()
            for (time in 0L..300L step 50L)
                assertNull(gate.process(input(AirAction.NONE, time).copy(confidence = confidence)))
            assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 350L)))
        }
        for (owner in listOf(null, "")) {
            val gate = ready()
            for (time in 150L..350L step 50L)
                assertNull(gate.process(input(AirAction.BACK, time, owner)))
        }
    }

    @Test fun duplicateAndOutOfOrderSamplesCannotAdvanceStateOrBypassCooldown() {
        val gate = GestureTransactionStateMachine()
        gate.process(input(AirAction.NONE, 100L))
        repeat(10) { assertNull(gate.process(input(AirAction.NONE, 100L))) }
        gate.process(input(AirAction.NONE, 99L))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
        gate.reset()
        gate.process(input(AirAction.NONE, 0L))
        gate.process(input(AirAction.NONE, 100L))
        assertNull(gate.process(input(AirAction.BACK, 200L)))
    }

    @Test fun changedCommandCannotReplayAndNeutralOwnerCannotSwitchMidRelease() {
        val gate = ready()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
        assertNull(gate.process(input(AirAction.HOME, 200L)))
        gate.process(input(AirAction.NONE, 250L, "left"))
        gate.process(input(AirAction.NONE, 300L, "right"))
        gate.process(input(AirAction.NONE, 350L, "right"))
        assertNull(gate.process(input(AirAction.HOME, 400L)))
    }

    @Test fun runtimeEpochChangeWhileReadyDoesNotConsumeFirstOccurrence() {
        val gate = ready()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L).copy(actionEpoch = 2L)))
        for (time in 200L..350L step 50L)
            assertNull(gate.process(input(AirAction.BACK, time).copy(actionEpoch = 2L)))
    }

    @Test fun irregularCadenceAndDropoutCannotReplayOrCompleteRelease() {
        val gate = ready()
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 150L)))
        for (t in listOf(166L, 201L, 335L, 520L)) assertNull(gate.process(input(AirAction.HOME, t)))
        gate.process(input(AirAction.NONE, 600L))
        gate.process(input(AirAction.NONE, 650L).copy(tracking = false))
        gate.process(input(AirAction.NONE, 700L))
        assertNull(gate.process(input(AirAction.BACK, 750L)))
    }

    @Test fun disabledOccurrenceCannotBecomeActiveWhenItsMappingChanges() {
        val gate = ready()
        assertNull(gate.process(input(AirAction.NONE, 150L).copy(gestureId = "THUMB_DOWN")))
        for (t in 200L..400L step 50L)
            assertNull(gate.process(input(AirAction.HOME, t).copy(gestureId = "THUMB_DOWN")))
    }

    private fun ready() = GestureTransactionStateMachine().also {
        it.process(input(AirAction.NONE, 0L)); it.process(input(AirAction.NONE, 100L))
    }
    private fun input(action: AirAction, timestamp: Long, owner: String? = "right") =
        GestureTransactionStateMachine.Input(action, 0.9f, timestamp, true, owner,
            gestureId = if (action == AirAction.NONE) null else "VICTORY")
}
