package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureTransactionStateMachineTest {
    private val owner = "right"

    @Test
    fun oneShotRequiresStableFramesBeforeFiring() {
        val gate = GestureTransactionStateMachine(
            stableFramesRequired = 2,
            neutralFramesRequired = 2,
            cooldownMs = 100L
        )

        assertNull(gate.process(input(AirAction.BACK, 0L)))
        assertNull(gate.process(input(AirAction.BACK, 33L)))
        assertNull(gate.process(input(AirAction.BACK, 66L)))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 99L)))
        assertNull(gate.process(input(AirAction.BACK, 132L)))
    }

    @Test
    fun releaseIsRequiredBeforeTheSameGestureCanFireAgain() {
        val gate = GestureTransactionStateMachine(
            stableFramesRequired = 1,
            neutralFramesRequired = 2,
            cooldownMs = 100L
        )

        assertNull(gate.process(input(AirAction.HOME, 0L)))
        assertNull(gate.process(input(AirAction.HOME, 33L)))
        assertNull(gate.process(input(AirAction.HOME, 66L)))
        assertEquals(AirAction.HOME, gate.process(input(AirAction.HOME, 99L)))
        assertNull(gate.process(input(AirAction.NONE, 200L)))
        assertNull(gate.process(input(AirAction.NONE, 233L)))
        assertNull(gate.process(input(AirAction.HOME, 266L)))
        assertNull(gate.process(input(AirAction.HOME, 299L)))
        assertNull(gate.process(input(AirAction.HOME, 332L)))
        assertEquals(AirAction.HOME, gate.process(input(AirAction.HOME, 365L)))
    }

    @Test
    fun ownershipChangeCancelsCandidateInsteadOfSwitchingCommand() {
        val gate = GestureTransactionStateMachine(stableFramesRequired = 1)

        assertNull(gate.process(input(AirAction.BACK, 0L, owner = "left")))
        assertNull(gate.process(input(AirAction.BACK, 33L, owner = owner)))
        assertNull(gate.process(input(AirAction.BACK, 66L, owner = owner)))
        assertNull(gate.process(input(AirAction.BACK, 99L, owner = owner)))
        assertEquals(AirAction.BACK, gate.process(input(AirAction.BACK, 132L, owner = owner)))
    }

    @Test
    fun staleFrameGapCancelsThePreviousTransaction() {
        val gate = GestureTransactionStateMachine(stableFramesRequired = 1, maxFrameGapMs = 100L)

        assertNull(gate.process(input(AirAction.RECENTS, 0L)))
        assertNull(gate.process(input(AirAction.RECENTS, 33L)))
        assertNull(gate.process(input(AirAction.RECENTS, 1000L)))
        assertNull(gate.process(input(AirAction.RECENTS, 1033L)))
        assertNull(gate.process(input(AirAction.RECENTS, 1066L)))
        assertEquals(AirAction.RECENTS, gate.process(input(AirAction.RECENTS, 1099L)))
    }

    private fun input(action: AirAction, timestampMs: Long, owner: String? = this.owner) =
        GestureTransactionStateMachine.Input(
            action = action,
            confidence = 0.9f,
            timestampMs = timestampMs,
            tracking = true,
            ownershipId = owner
        )
}
