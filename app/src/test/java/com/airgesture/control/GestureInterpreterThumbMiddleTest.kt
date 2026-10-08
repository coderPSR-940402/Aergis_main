package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import com.airgesture.control.pointer.SwipeDirection
import org.junit.Assert.*
import org.junit.Test

class GestureInterpreterThumbMiddleTest {
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
