package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class BoundedGesturePolicyTest {
    @Test fun distinctAssignedPosesCanRequestBoundedTouchOnlyWithPointer() {
        for (gesture in RegisteredGesture.entries) for (action in AirAction.entries.filter { it.usesPointerTouch }) {
            assertEquals("$gesture / $action", gesture != RegisteredGesture.POINTING_UP,
                GestureActionPolicy.isClassifierActionEnabled(true, action, true, gesture))
            assertFalse(GestureActionPolicy.isClassifierActionEnabled(true, action, false, gesture))
            assertFalse(GestureActionPolicy.isClassifierActionEnabled(false, action, true, gesture))
        }
    }
    @Test fun globalMappingsRemainAvailableWithPointerOff() {
        for (gesture in RegisteredGesture.entries) {
            assertTrue(GestureActionPolicy.isClassifierActionEnabled(true, AirAction.BACK, false, gesture))
            assertFalse(GestureActionPolicy.isClassifierActionEnabled(true, AirAction.NONE, true, gesture))
        }
    }
}
