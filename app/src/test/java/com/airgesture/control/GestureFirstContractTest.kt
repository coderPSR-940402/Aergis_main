package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class GestureFirstContractTest {
    @Test fun navigationDoesNotRequirePointer() {
        for (action in listOf(AirAction.SCROLL_UP, AirAction.SCROLL_DOWN)) {
            assertFalse(action.usesPointerTouch)
            for (pose in RegisteredGesture.entries)
                assertTrue(GestureActionPolicy.isClassifierActionEnabled(true, action, false, pose))
        }
    }
    @Test fun originalGestureIdsAndDefaultsArePreservedAndCandidatesDisabled() {
        val original = listOf("THUMB_UP", "VICTORY", "OPEN_PALM", "CLOSED_FIST", "POINTING_UP",
            "THUMB_DOWN", "I_LOVE_YOU", "OK", "THREE_FINGERS", "FOUR_FINGERS", "SHAKA", "PINKY_ONLY")
        assertEquals(original, RegisteredGesture.entries.take(12).map { it.name })
        assertEquals(18, RegisteredGesture.entries.size)
        RegisteredGesture.entries.drop(12).forEach { assertEquals(AirAction.NONE, it.defaultAction) }
    }
}
