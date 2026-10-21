package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

/** Occurrences, not action NONE or elapsed hold time, control command rearming. */
class ClassifierOccurrenceRegressionTest {
    @Test fun weakNoneCannotReleaseAConsumedCommand() {
        val gate = GestureTransactionStateMachine()
        primeAndFire(gate)
        for (t in 300L..1300L step 100L) gate.process(frame(AirAction.NONE, t, confidence = 0f))
        assertNoCommands(gate, 1400L)
    }

    @Test fun disabledGestureIsNotNeutralRelease() {
        val gate = GestureTransactionStateMachine()
        primeAndFire(gate)
        for (t in 300L..1300L step 100L) gate.process(frame(AirAction.NONE, t, gesture = "THUMB_DOWN"))
        assertNoCommands(gate, 1400L)
    }

    @Test fun engineCreationAcceptsFirstCommandWithoutNeutralAndCannotRepeat() {
        val gate = GestureTransactionStateMachine()
        assertEquals(AirAction.BACK, gate.process(frame(AirAction.BACK, 0L)))
        assertNoCommands(gate, 50L)
    }

    @Test fun resetAfterDispatchRequiresNewNeutral() {
        val gate = GestureTransactionStateMachine()
        primeAndFire(gate)
        gate.reset()
        assertNoCommands(gate, 350L)
    }

    @Test fun trackingLossAfterDispatchRequiresNewNeutral() {
        val gate = GestureTransactionStateMachine()
        primeAndFire(gate)
        gate.process(frame(AirAction.NONE, 300L).copy(tracking = false))
        assertNoCommands(gate, 350L)
    }

    @Test fun staleGapAfterDispatchRequiresNewNeutral() {
        val gate = GestureTransactionStateMachine()
        primeAndFire(gate)
        assertNoCommands(gate, 1000L)
    }

    @Test fun changedOwnerCannotReplayConsumedOccurrence() {
        val gate = GestureTransactionStateMachine()
        primeAndFire(gate)
        for (t in 300L..800L step 50L) assertNull(gate.process(frame(AirAction.BACK, t).copy(ownershipId = "new")))
    }

    @Test fun acceptedRecognitionFiresWithoutSavedHoldDelay() {
        val gate = GestureTransactionStateMachine()
        neutral(gate, 0L)
        assertEquals(AirAction.BACK, gate.process(frame(AirAction.BACK, 150L).copy(
            tuning = GestureTuning(holdMs = 1500L, repeatDelayMs = 3000L))))
        assertNoCommands(gate, 200L)
    }

    @Test fun neutralRearmsWithoutSavedRepeatDelay() {
        val gate = GestureTransactionStateMachine()
        primeAndFire(gate)
        neutral(gate, 300L)
        assertEquals(AirAction.BACK, gate.process(frame(AirAction.BACK, 450L)))
    }

    @Test fun everyCadenceProducesOneCommandPerOccurrence() {
        for (interval in listOf(16L, 33L, 66L, 134L, 200L)) {
            val gate = GestureTransactionStateMachine()
            val times = (0L..2000L step interval).toList()
            val outputs = times.mapNotNull { t ->
                gate.process(frame(if (t < 250L) AirAction.NONE else AirAction.BACK, t))
            }
            assertEquals("cadence $interval", listOf(AirAction.BACK), outputs)
        }
    }

    private fun primeAndFire(gate: GestureTransactionStateMachine) {
        neutral(gate, 0L)
        val actions = listOf(150L, 200L, 250L).mapNotNull { gate.process(frame(AirAction.BACK, it)) }
        assertEquals(listOf(AirAction.BACK), actions)
    }
    private fun assertNoCommands(gate: GestureTransactionStateMachine, start: Long) {
        for (t in start..start + 500L step 50L) assertNull("No replay at $t", gate.process(frame(AirAction.BACK, t)))
    }
    private fun neutral(gate: GestureTransactionStateMachine, start: Long) {
        gate.process(frame(AirAction.NONE, start))
        gate.process(frame(AirAction.NONE, start + 100L))
    }
    private fun frame(action: AirAction, t: Long, confidence: Float = .99f,
        gesture: String? = if (action == AirAction.NONE) null else "VICTORY") =
        GestureTransactionStateMachine.Input(action, confidence, t, true, "owner", gesture)
}
