package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test

class PointerAuthorityRegressionTest {
    @Test
    fun rejectedPoseCannotPullIndexTipTowardPalm() {
        val hand = MutableList(21) { Point3D(0.5f, 0.5f) }.apply {
            this[5] = Point3D(0.4f, 0.5f)
            this[17] = Point3D(0.5f, 0.5f)
            this[8] = Point3D(0.4f, 0.68f)
            this[12] = Point3D(0.5f, 0.65f)
        }
        val result = GestureInterpreter().processFrame(hand, 1000L, actionsAllowed = false)!!
        assertFalse(result.poseEvidence.accepted)
        assertEquals("Landmark 8 alone owns X", hand[8].x, result.smoothedX, 0.0001f)
        assertEquals("Landmark 8 alone owns Y", hand[8].y, result.smoothedY, 0.0001f)
        assertFalse(result.isClickEngaged)
    }

    @Test
    fun changingPalmWidthCannotChangeStationaryTipPosition() {
        fun hand(width: Float) = MutableList(21) { Point3D(0.5f, 0.5f) }.apply {
            this[5] = Point3D(0.4f, 0.5f)
            this[17] = Point3D(0.4f + width, 0.5f)
            this[8] = Point3D(0.4f, 0.68f)
            this[12] = Point3D(0.5f, 0.65f)
        }
        val narrow = GestureInterpreter().processFrame(hand(0.1f), 1000L, false)!!
        val wide = GestureInterpreter().processFrame(hand(0.2f), 1000L, false)!!
        assertEquals("Palm/depth must not warp pointer Y", narrow.smoothedY, wide.smoothedY, 0.0001f)
    }
}
