package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class GestureTuningTest {
    @Test fun expandedCatalogWorksAlongsidePointerWithoutEnablingCompetingTouchCommands() {
        assertEquals(30, AirAction.entries.count { it != AirAction.NONE })
        for (action in AirAction.entries.filter { it != AirAction.NONE }) {
            assertTrue(GestureActionPolicy.isClassifierActionEnabled(true, action, false))
            assertEquals(!action.usesPointerTouch, GestureActionPolicy.isClassifierActionEnabled(true, action, true))
            assertFalse(GestureActionPolicy.isClassifierActionEnabled(false, action, true))
        }
    }
    @Test fun confidenceStillRejectsNoiseButLegacyTimingDoesNotDelayRecognition() {
        val gate = GestureTransactionStateMachine()
        val tuning = GestureTuning(holdMs = 400, repeatDelayMs = 1200, confidencePercent = 90)
        fun frame(t: Long, action: AirAction = AirAction.VOLUME_UP, confidence: Float = .95f) =
            gate.process(GestureTransactionStateMachine.Input(action, confidence, t, true, "owner",
                if (action == AirAction.NONE) null else "SHAKA", tuning))
        frame(1000, AirAction.NONE); frame(1100, AirAction.NONE)
        assertNull(frame(1150, confidence = .8f))
        frame(1200, AirAction.NONE); frame(1300, AirAction.NONE)
        assertEquals(AirAction.VOLUME_UP, frame(1350))
        frame(1400, AirAction.NONE); frame(1500, AirAction.NONE)
        assertEquals(AirAction.VOLUME_UP, frame(1550))
    }
    @Test fun changingTuningCannotReplayAConsumedOccurrence() {
        val gate = GestureTransactionStateMachine()
        gate.process(GestureTransactionStateMachine.Input(AirAction.NONE, .99f, 1000, true, "owner"))
        gate.process(GestureTransactionStateMachine.Input(AirAction.NONE, .99f, 1100, true, "owner"))
        fun frame(t: Long, hold: Long) = gate.process(GestureTransactionStateMachine.Input(
            AirAction.HOME, .99f, t, true, "owner", "OPEN_PALM", GestureTuning(holdMs = hold)))
        assertEquals(AirAction.HOME, frame(1150, 400))
        assertNull(frame(1200, 100)); assertNull(frame(1300, 100))
    }
    @Test fun storedSettingsAreBounded() {
        val t = GestureTuning(-1, -1, 1, 999, 999, 0).bounded()
        assertEquals(GestureTuning(100, 300, 65, 5, 85, 500), t)
    }
}
