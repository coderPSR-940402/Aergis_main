package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class RegisteredGestureTest {
    @Test fun twelveStableIndependentGestureIdsCoverEveryRegisteredSignal() {
        assertEquals(12, RegisteredGesture.entries.size)
        assertEquals(12, RegisteredGesture.entries.map { it.signalName }.toSet().size)
        RegisteredGesture.entries.forEach { assertEquals(it, RegisteredGesture.fromSignal(it.signalName)) }
        assertNull(RegisteredGesture.fromSignal("None"))
        assertNull(RegisteredGesture.fromSignal("unknown"))
        assertEquals(RegisteredGesture.THUMB_UP, RegisteredGesture.fromSignal("thumbs_up"))
    }

    @Test fun switchingGesturesWithSameActionCannotCombineConfirmationTime() {
        val gate = GestureTransactionStateMachine()
        fun frame(time: Long, id: String) = gate.process(GestureTransactionStateMachine.Input(
            AirAction.BACK, .9f, time, true, "owner", id))
        assertNull(frame(1000, "VICTORY"))
        assertNull(frame(1100, "SHAKA"))
        assertEquals(AirAction.BACK, frame(1200, "SHAKA"))
        assertNull(frame(1400, "SHAKA"))
    }
}
