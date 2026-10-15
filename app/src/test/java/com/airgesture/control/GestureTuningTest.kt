package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class GestureTuningTest {
    @Test fun expandedCatalogWorksAlongsidePointerWithoutEnablingCompetingTouchCommands() {
        assertEquals(18, AirAction.entries.count { it != AirAction.NONE })
        for (action in AirAction.entries.filter { it != AirAction.NONE }) {
            assertTrue(GestureActionPolicy.isClassifierActionEnabled(true, action, false))
            assertEquals(!action.usesPointerTouch, GestureActionPolicy.isClassifierActionEnabled(true, action, true))
            assertFalse(GestureActionPolicy.isClassifierActionEnabled(false, action, true))
        }
    }
    @Test fun tuningChangesConfirmationConfidenceAndRearming() {
        val gate = GestureTransactionStateMachine()
        val tuning = GestureTuning(holdMs = 400, repeatDelayMs = 1200, confidencePercent = 90)
        fun frame(t: Long, action: AirAction = AirAction.VOLUME_UP, confidence: Float = .95f) =
            gate.process(GestureTransactionStateMachine.Input(action, confidence, t, true, "owner", "SHAKA", tuning))
        assertNull(frame(1000, confidence = .8f))
        assertNull(frame(1100)); assertNull(frame(1300))
        assertEquals(AirAction.VOLUME_UP, frame(1500))
        assertNull(frame(1700, AirAction.NONE)); assertNull(frame(1900, AirAction.NONE))
        assertEquals(GestureTransactionStateMachine.State.COOLDOWN, gate.currentState())
        for (t in listOf(2100L, 2300L, 2500L, 2700L)) frame(t, AirAction.NONE)
        assertEquals(GestureTransactionStateMachine.State.NEUTRAL, gate.currentState())
    }
    @Test fun settingsChangeCannotInheritPartialConfirmation() {
        val gate = GestureTransactionStateMachine()
        fun frame(t: Long, hold: Long) = gate.process(GestureTransactionStateMachine.Input(
            AirAction.HOME, .99f, t, true, "owner", "OPEN_PALM", GestureTuning(holdMs = hold)))
        assertNull(frame(1000, 400)); assertNull(frame(1200, 100))
        assertEquals(AirAction.HOME, frame(1300, 100))
    }
    @Test fun storedSettingsAreBounded() {
        val t = GestureTuning(-1, -1, 1, 999, 999, 0).bounded()
        assertEquals(GestureTuning(100, 300, 65, 5, 85, 500), t)
    }
}
