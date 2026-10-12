package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class GestureTransactionStateMachineTest {
    @Test fun confirmationHasTheSameDurationAt15_30_60Fps() {
        for (fps in listOf(15, 30, 60)) {
            val gate = GestureTransactionStateMachine()
            val fired = (0..fps).mapNotNull { frame ->
                val time = frame * 1000L / fps
                gate.process(input(AirAction.BACK, time))?.let { time }
            }
            assertEquals("one command at $fps FPS", 1, fired.size)
            assertTrue("must confirm for 100 ms at $fps FPS: $fired", fired.single() >= 100L)
            assertTrue("no extra frame stages at $fps FPS: $fired", fired.single() <= 100L + 1000L / fps + 1L)
        }
    }

    @Test fun briefGestureCannotFireAtHighFrameRate() {
        val gate = GestureTransactionStateMachine()
        for (time in 0L..90L step 10L) assertNull(gate.process(input(AirAction.HOME, time)))
        assertNull(gate.process(input(AirAction.NONE, 100L)))
    }

    @Test fun releaseAndCooldownAreBothRequired() {
        val gate = GestureTransactionStateMachine()
        fire(gate)
        for (time in 150L..1000L step 50L) assertNull(gate.process(input(AirAction.BACK, time)))
        assertNull(gate.process(input(AirAction.NONE, 1050L)))
        assertNull(gate.process(input(AirAction.NONE, 1100L)))
        // A short neutral excursion is not a release.
        assertNull(gate.process(input(AirAction.BACK, 1150L)))
        assertNull(gate.process(input(AirAction.NONE, 1200L)))
        assertNull(gate.process(input(AirAction.NONE, 1300L)))
        assertNull(gate.process(input(AirAction.BACK, 1350L)))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 1450L)))
    }

    @Test fun neutralWindowCanCompleteDuringCooldownButCannotBypassIt() {
        val gate = GestureTransactionStateMachine()
        fire(gate)
        for (time in 150L..750L step 50L) assertNull(gate.process(input(AirAction.NONE, time)))
        assertNull(gate.process(input(AirAction.NONE, 800L)))
        assertNull(gate.process(input(AirAction.HOME, 850L)))
        assertEquals(AirAction.HOME, gate.process(input(AirAction.HOME, 950L)))
    }

    @Test fun missingTrackingAndWeakEvidenceDoNotRearmHeldCommand() {
        for (invalid in listOf(
            input(AirAction.NONE, 0L).copy(tracking = false),
            input(AirAction.BACK, 0L).copy(confidence = 0.1f),
            input(AirAction.NONE, 0L).copy(ownershipId = null)
        )) {
            val gate = GestureTransactionStateMachine()
            fire(gate)
            for (time in 150L..1000L step 50L) assertNull(gate.process(invalid.copy(timestampMs = time)))
            for (time in 1050L..1300L step 50L) assertNull(gate.process(input(AirAction.BACK, time)))
        }
    }

    @Test fun ownershipChangeRestartsConfirmation() {
        val gate = GestureTransactionStateMachine()
        assertNull(gate.process(input(AirAction.BACK, 0L, "left")))
        assertNull(gate.process(input(AirAction.BACK, 80L, "left")))
        assertNull(gate.process(input(AirAction.BACK, 100L, "right")))
        assertNull(gate.process(input(AirAction.BACK, 150L, "right")))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 200L, "right")))
    }

    @Test fun staleGapRestartsCandidateButDoesNotRearmFiredCommand() {
        val gate = GestureTransactionStateMachine()
        assertNull(gate.process(input(AirAction.BACK, 0L)))
        assertNull(gate.process(input(AirAction.BACK, 1000L)))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 1100L)))
        for (time in 2000L..2300L step 50L) assertNull(gate.process(input(AirAction.BACK, time)))
    }

    @Test fun engineResetPreservesReleaseRequirementAfterDispatch() {
        val gate = GestureTransactionStateMachine()
        fire(gate)
        gate.reset()
        for (time in 1000L..1300L step 50L) assertNull(gate.process(input(AirAction.BACK, time)))
        assertNull(gate.process(input(AirAction.NONE, 1350L)))
        assertNull(gate.process(input(AirAction.NONE, 1450L)))
        assertNull(gate.process(input(AirAction.BACK, 1500L)))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 1600L)))
    }

    @Test fun invalidConfidenceOrUnknownOwnerCannotConfirm() {
        for (invalid in listOf(
            input(AirAction.BACK, 0L).copy(confidence = Float.NaN),
            input(AirAction.BACK, 0L).copy(confidence = Float.POSITIVE_INFINITY),
            input(AirAction.BACK, 0L).copy(ownershipId = null),
            input(AirAction.BACK, 0L).copy(ownershipId = "")
        )) {
            val gate = GestureTransactionStateMachine()
            for (time in 0L..300L step 50L) assertNull(gate.process(invalid.copy(timestampMs = time)))
        }
    }

    @Test fun duplicateAndOutOfOrderSamplesDoNotAdvanceEvidence() {
        val gate = GestureTransactionStateMachine()
        assertNull(gate.process(input(AirAction.BACK, 100L)))
        repeat(10) { assertNull(gate.process(input(AirAction.BACK, 100L))) }
        assertNull(gate.process(input(AirAction.BACK, 99L)))
        assertNull(gate.process(input(AirAction.BACK, 150L)))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 200L)))
    }

    @Test fun actionChangeRestartsConfirmationAndNeutralOwnerCannotSwitchMidRelease() {
        val gate = GestureTransactionStateMachine()
        assertNull(gate.process(input(AirAction.BACK, 0L)))
        assertNull(gate.process(input(AirAction.HOME, 90L)))
        assertNull(gate.process(input(AirAction.HOME, 150L)))
        assertEquals(AirAction.HOME, gate.process(input(AirAction.HOME, 190L)))
        for (time in 240L..940L step 50L) assertNull(gate.process(input(AirAction.HOME, time)))
        assertNull(gate.process(input(AirAction.NONE, 1000L, "left")))
        assertNull(gate.process(input(AirAction.NONE, 1050L, "right")))
        assertNull(gate.process(input(AirAction.NONE, 1100L, "right")))
        for (time in 1150L..1400L step 50L) assertNull(gate.process(input(AirAction.HOME, time)))
    }

    private fun fire(gate: GestureTransactionStateMachine) {
        assertNull(gate.process(input(AirAction.BACK, 0L)))
        assertNull(gate.process(input(AirAction.BACK, 33L)))
        assertNull(gate.process(input(AirAction.BACK, 66L)))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 100L)))
    }

    private fun input(action: AirAction, timestamp: Long, owner: String? = "right") =
        GestureTransactionStateMachine.Input(action, 0.9f, timestamp, true, owner)
}
