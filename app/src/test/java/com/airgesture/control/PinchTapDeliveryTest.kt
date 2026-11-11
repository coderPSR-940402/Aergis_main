package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test

/** Click confirmation is a one-shot event; actual hotspot delivery may lag. */
class PinchTapDeliveryTest {
    @Test fun trustedConfirmationDeliversExactlyOnce() {
        val interpreter = approach()
        assertTrue(interpreter.processFrame(pose(true), 94L)!!.isClickEngaged)
        assertTrue(interpreter.takePendingTap(94L, true))
        assertFalse(interpreter.takePendingTap(94L, true))
        assertFalse(interpreter.hasPendingTap)
    }

    @Test fun untrustedOrUnappliedHotspotDefersButOnlyForTheSameHeldContact() {
        val interpreter = approach()
        assertTrue(interpreter.processFrame(pose(true), 94L)!!.isClickEngaged)
        assertFalse(interpreter.takePendingTap(94L, false))
        assertTrue(interpreter.hasPendingTap)
        assertFalse(interpreter.processFrame(pose(true), 127L)!!.isClickEngaged)
        assertTrue(interpreter.processFrame(pose(true), 160L)!!.isPinchPressed)
        assertTrue(interpreter.takePendingTap(160L, true))
        assertFalse(interpreter.takePendingTap(160L, true))
        for (t in 193L..600L step 33L) {
            interpreter.processFrame(pose(true), t)
            assertFalse(interpreter.takePendingTap(t, true))
        }
    }

    @Test fun contactReleaseImmediatelyDiscardsPendingWithoutPolling() {
        val interpreter = approach()
        interpreter.processFrame(pose(true), 94L)
        assertTrue(interpreter.hasPendingTap)
        interpreter.processFrame(pose(false), 127L)
        assertFalse("a release must invalidate without a dispatch attempt", interpreter.hasPendingTap)
        assertFalse(interpreter.takePendingTap(127L, true))
    }

    @Test fun untrustedContactExpiresEvenWithoutPollingDispatch() {
        val interpreter = approach()
        interpreter.processFrame(pose(true), 94L)
        for (t in 127L..358L step 33L) interpreter.processFrame(pose(true), t)
        assertFalse("stale pending taps must not survive", interpreter.hasPendingTap)
        assertFalse(interpreter.takePendingTap(358L, true))
    }

    @Test fun queuedTapDropsOnActionBlockAndReset() {
        val interpreter = approach()
        interpreter.processFrame(pose(true), 94L)
        interpreter.processFrame(pose(true), 127L, actionsAllowed = false)
        assertFalse(interpreter.hasPendingTap)
        assertFalse(interpreter.takePendingTap(127L, true))
        val resumed = interpreter.processFrame(pose(true), 160L)!!
        assertFalse(resumed.isClickEngaged)

        val reset = approach()
        reset.processFrame(pose(true), 94L)
        reset.resetActions()
        assertFalse(reset.hasPendingTap)
        assertFalse(reset.takePendingTap(94L, true))
    }

    @Test fun invalidGeometryInvalidTimestampAndMissingTipDropPending() {
        val badGeometry = approach()
        badGeometry.processFrame(pose(true), 94L)
        badGeometry.processFrame(pose(true).apply { this[4] = Point3D(Float.NaN, .5f) }, 127L)
        assertFalse(badGeometry.hasPendingTap)

        val staleFrame = approach()
        staleFrame.processFrame(pose(true), 94L)
        staleFrame.processFrame(pose(true), 94L)
        assertFalse(staleFrame.hasPendingTap)

        val missing = approach()
        missing.processFrame(pose(true), 94L)
        assertNull(missing.processFrame(emptyList(), 127L))
        assertFalse(missing.hasPendingTap)
    }

    @Test fun newContactMustObserveSeparationBeforeRearming() {
        val interpreter = approach()
        interpreter.processFrame(pose(true), 94L)
        assertTrue(interpreter.takePendingTap(94L, true))
        assertFalse(interpreter.processFrame(pose(true), 160L)!!.isClickEngaged)
        interpreter.processFrame(pose(false), 200L)
        interpreter.processFrame(pose(false), 233L)
        assertFalse(interpreter.processFrame(pose(true), 266L)!!.isClickEngaged)
        assertTrue(interpreter.processFrame(pose(true), 326L)!!.isClickEngaged)
        assertTrue(interpreter.takePendingTap(326L, true))
        assertFalse(interpreter.takePendingTap(326L, true))
    }

    @Test fun alreadyClosedAtAcquisitionNeverEnqueuesClick() {
        val interpreter = GestureInterpreter()
        for (t in listOf(1L, 34L, 100L, 180L)) {
            assertFalse(interpreter.processFrame(pose(true), t)!!.isClickEngaged)
            assertFalse(interpreter.hasPendingTap)
        }
    }

    private fun approach() = GestureInterpreter().also {
        it.processFrame(pose(false), 1L)
        it.processFrame(pose(true), 34L)
    }

    private fun pose(closed: Boolean) = MutableList(21) { Point3D(.5f, .5f) }.apply {
        this[4] = Point3D(if (closed) .55f else .2f, .5f)
        this[5] = Point3D(.4f, .5f)
        this[17] = Point3D(.6f, .5f)
        this[8] = Point3D(.60f, .5f)
        this[12] = Point3D(.56f, .5f)
    }
}
