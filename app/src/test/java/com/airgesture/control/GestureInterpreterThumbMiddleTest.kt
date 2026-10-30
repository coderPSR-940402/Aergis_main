package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import com.airgesture.control.pointer.SwipeDirection
import org.junit.Assert.*
import org.junit.Test

class GestureInterpreterThumbMiddleTest {
    @Test fun invalidIndexCannotPoisonThePointerFiltersOnRecovery() {
        for (badTip in listOf(Point3D(8f,.5f), Point3D(-8f,.5f), Point3D(.5f,8f),
            Point3D(.5f,-8f), Point3D(Float.NaN,.5f))) {
            val clean = GestureInterpreter()
            val interrupted = GestureInterpreter()
            clean.processFrame(pose(false),1000L)
            interrupted.processFrame(pose(false),1000L)
            interrupted.processFrame(pose(false).apply { this[8] = badTip },1033L)
            val expected = clean.processFrame(pose(false),1066L)!!
            val recovered = interrupted.processFrame(pose(false),1066L)!!
            assertEquals("Rejected X must not alter the next valid position",expected.smoothedX,recovered.smoothedX,.00001f)
            assertEquals("Rejected Y must not alter the next valid position",expected.smoothedY,recovered.smoothedY,.00001f)
        }
    }

    @Test fun offFrameTipCancelsContactUntilAnOpeningIsObserved() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(false),1000L)
        interpreter.processFrame(pose(true),1033L)
        assertNull(interpreter.processFrame(pose(true).apply { this[8] = Point3D(1.1f,.5f) },1066L))
        assertFalse(interpreter.processFrame(pose(true),1166L)!!.isClickEngaged)
        interpreter.processFrame(pose(false),1266L)
        assertTrue(interpreter.processFrame(pose(true),1366L)!!.isClickEngaged)
    }

    @Test fun authorizedFiniteRefitKeepsTheStablePointWithoutLearningTheBadCoordinate() {
        val interpreter = GestureInterpreter()
        val initial = interpreter.processFrame(pose(false),1000L)!!
        val refit = interpreter.processFrame(pose(true).apply { this[8] = Point3D(8f,-8f) },1100L,
            allowIndexRefit = true)!!
        assertTrue(refit.isClickEngaged)
        assertEquals(initial.smoothedX,refit.smoothedX,0f)
        assertEquals(initial.smoothedY,refit.smoothedY,0f)
        val recovered = interpreter.processFrame(pose(true),1200L)!!
        assertEquals(initial.smoothedX,recovered.smoothedX,.00001f)
        assertEquals(initial.smoothedY,recovered.smoothedY,.00001f)
        assertFalse(recovered.isClickEngaged)
    }

    @Test fun explicitlyAuthorizedIndexRefitCannotVetoVisibleThumbMiddleContact() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(closed = false), 1000L)
        val refit = pose(closed = true).apply { this[8] = Point3D(Float.NaN, Float.NaN, Float.NaN) }
        val result = interpreter.processFrame(refit, 1100L, allowIndexRefit = true)
        assertNotNull(result)
        assertTrue(result!!.isClickEngaged)
        assertTrue(result.smoothedX.isFinite())
        assertFalse(interpreter.processFrame(refit, 1200L, allowIndexRefit = true)!!.isClickEngaged)
    }

    @Test fun contactConfirmsOnceAndKeepsPressedStateUntilRelease() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(closed = false), 1L)
        val approaching = interpreter.processFrame(pose(closed = true), 34L)!!
        assertTrue(approaching.isPinchApproaching)
        assertFalse(approaching.isPinchPressed)
        assertFalse(approaching.isClickEngaged)
        val confirmed = interpreter.processFrame(pose(closed = true), 94L)!!
        assertTrue(confirmed.isClickEngaged)
        assertTrue(confirmed.isPinchPressed)
        assertEquals(SwipeDirection.NONE, confirmed.detectedSwipe)
        val held = interpreter.processFrame(pose(closed = true), 170L)!!
        assertFalse(held.isClickEngaged)
        assertTrue(held.isPinchPressed)
        val release = interpreter.processFrame(pose(closed = false), 200L)!!
        assertFalse(release.isPinchApproaching)
        assertFalse(release.isPinchPressed)
    }

    @Test fun indexMiddleContactAloneCannotClick() {
        val interpreter = GestureInterpreter()
        for (time in listOf(1L, 34L, 100L, 170L)) {
            val result = interpreter.processFrame(pose(closed = false).apply {
                this[8] = this[12]
            }, time)!!
            assertFalse(result.isClickEngaged)
            assertFalse(result.isPinchPressed)
            assertFalse(result.isPinchApproaching)
        }
    }

    @Test fun disabledActionsCancelContactButContinueIndexTracking() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(closed = false), 1L)
        interpreter.processFrame(pose(closed = true), 34L)
        val blocked = interpreter.processFrame(pose(closed = true), 100L, actionsAllowed = false)!!
        assertTrue(blocked.smoothedX.isFinite())
        assertFalse(blocked.isPinchApproaching)
        assertFalse(blocked.isPinchPressed)
        assertFalse(interpreter.processFrame(pose(closed = true), 170L)!!.isPinchPressed)
    }

    @Test fun invalidGeometryCancelsAConfirmedPress() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(closed = false), 1L)
        interpreter.processFrame(pose(closed = true), 34L)
        assertTrue(interpreter.processFrame(pose(closed = true), 94L)!!.isPinchPressed)
        val invalid = interpreter.processFrame(pose(closed = true).apply {
            this[4] = Point3D(Float.NaN, 0.5f)
        }, 130L)!!
        assertFalse(invalid.isPinchPressed)
        assertFalse(invalid.isPinchApproaching)
        assertTrue(invalid.smoothedX.isFinite())
        assertFalse(interpreter.processFrame(pose(closed = true), 200L)!!.isPinchPressed)
    }

    private fun pose(closed: Boolean) = MutableList(21) { Point3D(0.5f, 0.5f) }.apply {
        this[4] = Point3D(if (closed) 0.55f else 0.2f, 0.5f)
        this[5] = Point3D(0.4f, 0.5f)
        this[17] = Point3D(0.6f, 0.5f)
        this[8] = Point3D(0.60f, 0.5f)
        this[12] = Point3D(0.56f, 0.5f)
    }
}
