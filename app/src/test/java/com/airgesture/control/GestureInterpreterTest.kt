package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureInterpreterTest {
    @Test
    fun mapsKnownGestureNamesToActions() {
        val interpreter = GestureInterpreter()

        assertEquals(AirAction.TAP, interpreter.interpret(GestureSignal("thumb_up", 0.9f)).action)
        assertEquals(AirAction.TAP, interpreter.interpret(GestureSignal("thumbs_up", 0.9f)).action)
        assertEquals(AirAction.BACK, interpreter.interpret(GestureSignal("victory", 0.9f)).action)
        assertEquals(AirAction.BACK, interpreter.interpret(GestureSignal("peace", 0.9f)).action)
        assertEquals(AirAction.HOME, interpreter.interpret(GestureSignal("open_palm", 0.9f)).action)
        assertEquals(AirAction.HOME, interpreter.interpret(GestureSignal("open hand", 0.9f)).action)
        assertEquals(AirAction.RECENTS, interpreter.interpret(GestureSignal("closed_fist", 0.9f)).action)
        assertEquals(AirAction.RECENTS, interpreter.interpret(GestureSignal("fist", 0.9f)).action)
        assertEquals(AirAction.DOUBLE_TAP, interpreter.interpret(GestureSignal("pointing_up", 0.9f)).action)
        assertEquals(AirAction.DOUBLE_TAP, interpreter.interpret(GestureSignal("point", 0.9f)).action)
        assertEquals(AirAction.NONE, interpreter.interpret(GestureSignal("unknown_gesture", 0.9f)).action)
    }

    @Test
    fun retainsScoreInDecision() {
        val interpreter = GestureInterpreter()
        val decision = interpreter.interpret(GestureSignal("thumb_up", 0.85f))
        assertEquals(0.85f, decision.confidence, 0.0001f)
    }
}
