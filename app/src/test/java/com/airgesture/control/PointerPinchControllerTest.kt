package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class PointerPinchControllerTest {
    @Test fun ordinaryAimingPassesTheSelectedFilterPositionThroughExactly() {
        val controller = PointerPinchController()
        for (point in listOf(p(0.1f, 0.2f), p(0.9f, 0.95f), p(0.5f, 0.4f))) {
            assertEquals(point, controller.update(point, false, false, 1L).point)
        }
    }

    @Test fun approachHoldsThePreviouslyDisplayedTargetBeforeContact() {
        val controller = PointerPinchController()
        controller.update(p(0.4f, 0.5f), false, false, 1L)
        assertEquals(p(0.4f, 0.5f), controller.update(p(0.45f, 0.55f), true, false, 34L).point)
        val press = controller.update(p(0.50f, 0.60f), true, true, 100L)
        assertEquals(p(0.4f, 0.5f), press.point)
        assertEquals(PointerInteractionPhase.PRESSED, press.phase)
    }

    @Test fun stationaryHoldDoesNotMoveCursorAndBecomesHoldingWithoutAnotherClick() {
        val controller = PointerPinchController()
        controller.update(p(0.4f, 0.5f), false, false, 1L)
        controller.update(p(0.45f, 0.55f), true, true, 100L)
        assertEquals(p(0.4f, 0.5f), controller.update(p(0.46f, 0.56f), true, true, 300L).point)
        val held = controller.update(p(0.45f, 0.55f), true, true, 600L)
        assertEquals(p(0.4f, 0.5f), held.point)
        assertEquals(PointerInteractionPhase.HOLDING, held.phase)
    }

    @Test fun deliberateMovementWhilePressedDragsFromTheHeldTargetWithoutClosureDrift() {
        val controller = PointerPinchController()
        controller.update(p(0.4f, 0.5f), false, false, 1L)
        controller.update(p(0.5f, 0.6f), true, true, 100L)
        val drag = controller.update(p(0.6f, 0.8f), true, true, 180L)
        assertEquals(0.5f, drag.point.x, 0.0001f)
        assertEquals(0.7f, drag.point.y, 0.0001f)
        assertEquals(PointerInteractionPhase.DRAGGING, drag.phase)
        val edge = controller.update(p(1f, 1f), true, true, 250L).point
        assertEquals(0.9f, edge.x, 0.0001f)
        assertEquals(0.9f, edge.y, 0.0001f)
    }

    @Test fun releasingBlendsBackThenOrdinaryAimingIsUnchanged() {
        val controller = PointerPinchController()
        controller.update(p(0.4f, 0.5f), false, false, 1L)
        controller.update(p(0.5f, 0.6f), true, true, 100L)
        assertEquals(p(0.4f, 0.5f), controller.update(p(0.6f, 0.7f), false, false, 200L).point)
        val middle = controller.update(p(0.6f, 0.7f), false, false, 240L).point
        assertEquals(0.5f, middle.x, 0.0001f)
        assertEquals(0.6f, middle.y, 0.0001f)
        assertEquals(p(0.6f, 0.7f), controller.update(p(0.6f, 0.7f), false, false, 280L).point)
    }

    @Test fun cancellationCannotReuseAnOldHeldTarget() {
        val controller = PointerPinchController()
        controller.update(p(0.4f, 0.5f), false, false, 1L)
        controller.update(p(0.5f, 0.6f), true, true, 100L)
        controller.reset()
        assertEquals(p(0.8f, 0.9f), controller.update(p(0.8f, 0.9f), false, false, 200L).point)
    }

    private fun p(x: Float, y: Float) = PointerCoordinateMapper.Point(x, y)
}
