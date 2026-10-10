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

    @Test fun briefSmallExcursionCannotTurnAClickIntoADrag() {
        val controller = PointerPinchController()
        controller.update(p(.4f, .5f), false, false, 1L)
        controller.update(p(.4f, .5f), true, true, 100L)
        val noise = controller.update(p(.431f, .5f), true, true, 133L)
        assertEquals(PointerInteractionPhase.PRESSED, noise.phase)
        assertEquals(p(.4f, .5f), noise.point)
        assertEquals(PointerInteractionPhase.PRESSED, controller.update(p(.405f, .5f), true, true, 166L).phase)
    }

    @Test fun sustainedDragUsesEqualPhysicalDistanceOnBothScreenAxes() {
        for (delta in listOf(p(.04f, 0f), p(0f, .02f))) {
            val controller = PointerPinchController()
            controller.update(p(.4f, .5f), false, false, 1L, .5f)
            controller.update(p(.4f, .5f), true, true, 100L, .5f)
            val point = p(.4f + delta.x, .5f + delta.y)
            assertEquals(PointerInteractionPhase.PRESSED, controller.update(point, true, true, 133L, .5f).phase)
            assertEquals(PointerInteractionPhase.DRAGGING, controller.update(point, true, true, 200L, .5f).phase)
        }
    }

    @Test fun releasingContactWhileFingersRemainCloseEndsThePreviousDrag() {
        val controller = PointerPinchController()
        controller.update(p(.4f, .5f), false, false, 1L)
        controller.update(p(.4f, .5f), true, true, 100L)
        val drag = controller.update(p(.6f, .5f), true, true, 200L)
        assertEquals(PointerInteractionPhase.DRAGGING, drag.phase)
        val release = controller.update(p(.6f, .5f), true, false, 233L)
        assertEquals(drag.point, release.point)
        val nextPress = controller.update(p(.6f, .5f), true, true, 800L)
        assertEquals(PointerInteractionPhase.PRESSED, nextPress.phase)
        assertEquals(drag.point, nextPress.point)
    }

    private fun p(x: Float, y: Float) = PointerCoordinateMapper.Point(x, y)
}
