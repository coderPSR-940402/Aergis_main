package com.airgesture.control

import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class PointerPinchControllerTest {
    @Test fun refitUsesPreviousTargetForAtMost350msAndCannotRenewItsOwnTrust() {
        val c = PointerPinchController()
        c.update(p(.4f, .5f), false, false, 1000L)
        assertEquals(p(.4f, .5f), c.refitAnchor(1033L))
        c.update(p(.9f, .9f), true, false, 1033L, positionTrusted = false)
        assertEquals(p(.4f, .5f), c.refitAnchor(1350L))
        assertNull(c.refitAnchor(1351L))
        assertNull(c.refitAnchor(999L))
        c.reset()
        assertNull(c.refitAnchor(1100L))
    }

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

    @Test fun stationaryHoldNeverChangesTheCommandOrTarget() {
        val controller = PointerPinchController()
        controller.update(p(0.4f, 0.5f), false, false, 1L)
        controller.update(p(0.45f, 0.55f), true, true, 100L)
        assertEquals(p(0.4f, 0.5f), controller.update(p(0.46f, 0.56f), true, true, 300L).point)
        val held = controller.update(p(0.45f, 0.55f), true, true, 600L)
        assertEquals(p(0.4f, 0.5f), held.point)
        assertEquals(PointerInteractionPhase.PRESSED, held.phase)
    }

    @Test fun movementWhilePressedCannotTurnTheClickIntoADrag() {
        val controller = PointerPinchController()
        controller.update(p(0.4f, 0.5f), false, false, 1L)
        controller.update(p(0.5f, 0.6f), true, true, 100L)
        val drag = controller.update(p(0.6f, 0.8f), true, true, 180L)
        assertEquals(0.4f, drag.point.x, 0.0001f)
        assertEquals(0.5f, drag.point.y, 0.0001f)
        assertEquals(PointerInteractionPhase.PRESSED, drag.phase)
        val edge = controller.update(p(1f, 1f), true, true, 250L).point
        assertEquals(0.4f, edge.x, 0.0001f)
        assertEquals(0.5f, edge.y, 0.0001f)
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

    @Test fun largeReleaseRefitCannotJumpAwayFromTheClickAnchor() {
        val controller = PointerPinchController()
        val anchor = p(.4f, .5f)
        controller.update(anchor, false, false, 1L)
        controller.update(p(.5f, .6f), true, true, 100L)
        assertEquals(anchor, controller.update(p(.9f, .1f), false, false, 200L).point)
        assertEquals("A false-open burst must keep the exact visible target", anchor,
            controller.update(p(.9f, .1f), false, false, 300L).point)
        assertEquals("Re-contact during the guard must reacquire the same target", anchor,
            controller.update(p(.9f, .1f), true, true, 310L).point)
    }

    @Test fun sustainedLargeReleaseRecoversInBoundedSteps() {
        val controller = PointerPinchController()
        val anchor = p(.4f, .5f)
        val candidate = p(.9f, .1f)
        controller.update(anchor, false, false, 1L)
        controller.update(p(.5f, .6f), true, true, 100L)
        controller.update(candidate, false, false, 200L)
        controller.update(candidate, false, false, 300L)
        var previous = anchor
        var timestamp = 340L
        repeat(20) {
            val next = controller.update(candidate, false, false, timestamp).point
            assertTrue("Release recovery step must stay bounded", distance(previous, next) <= .0401f)
            previous = next
            timestamp += 40L
        }
        assertEquals(candidate.x, previous.x, .0001f)
        assertEquals(candidate.y, previous.y, .0001f)
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

    @Test fun movementOnEitherAxisRetainsTheClickAnchor() {
        for (delta in listOf(p(.04f, 0f), p(0f, .02f))) {
            val controller = PointerPinchController()
            controller.update(p(.4f, .5f), false, false, 1L)
            controller.update(p(.4f, .5f), true, true, 100L)
            val point = p(.4f + delta.x, .5f + delta.y)
            assertEquals(PointerInteractionPhase.PRESSED, controller.update(point, true, true, 133L).phase)
            assertEquals(PointerInteractionPhase.PRESSED, controller.update(point, true, true, 200L).phase)
        }
    }

    @Test fun nearContactCannotMoveTheAnchorOrCreateAHoldVariant() {
        val controller = PointerPinchController()
        controller.update(p(.4f, .5f), false, false, 1L)
        controller.update(p(.4f, .5f), true, true, 100L)
        val drag = controller.update(p(.6f, .5f), true, true, 200L)
        assertEquals(PointerInteractionPhase.PRESSED, drag.phase)
        val release = controller.update(p(.6f, .5f), true, false, 233L)
        assertEquals(drag.point, release.point)
        val nextPress = controller.update(p(.6f, .5f), true, true, 800L)
        assertEquals(PointerInteractionPhase.PRESSED, nextPress.phase)
        assertEquals(drag.point, nextPress.point)
    }

    private fun p(x: Float, y: Float) = PointerCoordinateMapper.Point(x, y)
    private fun distance(a: PointerCoordinateMapper.Point, b: PointerCoordinateMapper.Point): Float =
        hypot(a.x - b.x, a.y - b.y)
}
