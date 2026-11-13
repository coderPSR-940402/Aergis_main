package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class DragActionArbitrationTest {
    @Test fun primaryNavigationEscapesActiveDrag() {
        for (action in listOf(AirAction.BACK, AirAction.HOME, AirAction.RECENTS,
            AirAction.NOTIFICATIONS, AirAction.QUICK_SETTINGS))
            assertEquals(DragActionArbitration.Decision.CANCEL_THEN_ALLOW,
                DragActionArbitration.decide(true, action))
    }

    @Test fun conflictingCoordinateInputIsRejectedDuringDrag() {
        for (action in listOf(AirAction.TAP, AirAction.DOUBLE_TAP, AirAction.SCROLL_UP,
            AirAction.SCROLL_DOWN, AirAction.SWIPE_LEFT, AirAction.SWIPE_RIGHT))
            assertEquals(DragActionArbitration.Decision.REJECT,
                DragActionArbitration.decide(true, action))
    }

    @Test fun dragToggleAndEmergencyPointerControlsStillWork() {
        for (action in listOf(AirAction.POINTER_DRAG, AirAction.TOGGLE_POINTER,
            AirAction.CALIBRATE_POINTER))
            assertEquals(DragActionArbitration.Decision.ALLOW,
                DragActionArbitration.decide(true, action))
    }

    @Test fun inactiveDragNeverBlocksNormalActions() {
        for (action in AirAction.entries)
            assertEquals(DragActionArbitration.Decision.ALLOW,
                DragActionArbitration.decide(false, action))
    }
}
