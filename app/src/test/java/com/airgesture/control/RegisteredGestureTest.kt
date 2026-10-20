package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class RegisteredGestureTest {
    @Test fun eighteenStableIndependentGestureIdsCoverEveryRegisteredSignal() {
        assertEquals(18, RegisteredGesture.entries.size)
        assertEquals(18, RegisteredGesture.entries.map { it.signalName }.toSet().size)
        RegisteredGesture.entries.forEach { assertEquals(it, RegisteredGesture.fromSignal(it.signalName)) }
        assertNull(RegisteredGesture.fromSignal("None"))
        assertNull(RegisteredGesture.fromSignal("unknown"))
        assertEquals(RegisteredGesture.THUMB_UP, RegisteredGesture.fromSignal("thumbs_up"))
    }

    @Test fun switchingGesturesWithSameActionCannotBypassNeutralRelease() {
        val gate = GestureTransactionStateMachine()
        fun frame(time: Long, id: String) = gate.process(GestureTransactionStateMachine.Input(
            AirAction.BACK, .9f, time, true, "owner", id))
        gate.process(GestureTransactionStateMachine.Input(AirAction.NONE, .9f, 800L, true, "owner"))
        gate.process(GestureTransactionStateMachine.Input(AirAction.NONE, .9f, 900L, true, "owner"))
        assertEquals(AirAction.BACK, frame(1000, "VICTORY"))
        assertNull(frame(1100, "SHAKA"))
        assertNull(frame(1200, "SHAKA"))
        assertNull(frame(1400, "SHAKA"))
    }
}
