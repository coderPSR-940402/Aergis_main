package com.airgesture.control

import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureInterpreterSafetyTest {
    @Test
    fun invalidPoseCancelsPendingClickEvidence() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(closeFingerPose().toMutableList().apply {
            this[KinematicValidator.THUMB_TIP] = Point3D(0.2f, 0.5f)
        }, 0L)

        val first = interpreter.processFrame(closeFingerPose(), 1L)!!
        assertFalse(first.isClickEngaged)
        assertTrue(first.isPinchApproaching)

        val invalidPose = closeFingerPose().toMutableList().apply {
            this[KinematicValidator.WRIST] = Point3D(1.1f, 0.5f)
        }
        val rejected = interpreter.processFrame(invalidPose, 34L)!!

        assertFalse(rejected.poseEvidence.accepted)
        assertFalse(rejected.isClickEngaged)

        val restarted = interpreter.processFrame(closeFingerPose(), 67L)!!
        assertFalse(restarted.isClickEngaged)
    }

    private fun closeFingerPose(): List<Point3D> = MutableList(21) { Point3D(0.5f, 0.5f) }.apply {
        this[KinematicValidator.INDEX_MCP] = Point3D(0.4f, 0.5f)
        this[KinematicValidator.PINKY_MCP] = Point3D(0.6f, 0.5f)
        this[KinematicValidator.INDEX_TIP] = Point3D(0.55f, 0.5f)
        this[KinematicValidator.THUMB_TIP] = Point3D(0.55f, 0.5f)
        this[KinematicValidator.MIDDLE_TIP] = Point3D(0.56f, 0.5f)
    }
}
