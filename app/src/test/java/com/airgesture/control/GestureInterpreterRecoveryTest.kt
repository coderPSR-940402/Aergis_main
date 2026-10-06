package com.airgesture.control

import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureInterpreterRecoveryTest {
    @Test
    fun nonFiniteTipCannotPoisonSubsequentValidPointerFrames() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (invalidX in listOf(true, false)) {
                val interpreter = GestureInterpreter()
                interpreter.processFrame(validPose(), 1L)
                val badPose = validPose().toMutableList().apply {
                    this[KinematicValidator.INDEX_TIP] = if (invalidX) {
                        Point3D(invalid, 0.5f)
                    } else {
                        Point3D(0.55f, invalid)
                    }
                }

                val rejected = interpreter.processFrame(badPose, 34L)
                if (rejected != null) {
                    assertFalse(rejected.poseEvidence.accepted)
                    assertFalse(rejected.isClickEngaged)
                }

                val recovered = interpreter.processFrame(validPose(), 67L)!!
                assertTrue("Pointer X must recover after $invalid", recovered.smoothedX.isFinite())
                assertTrue("Pointer Y must recover after $invalid", recovered.smoothedY.isFinite())
                assertTrue(recovered.smoothedX in 0f..1f)
                assertTrue(recovered.smoothedY in 0f..1f)
                assertTrue(recovered.poseEvidence.accepted)
                assertFalse("Invalid frame must cancel pending click", recovered.isClickEngaged)
            }
        }
    }

    private fun validPose(): List<Point3D> = MutableList(21) { Point3D(0.5f, 0.5f) }.apply {
        this[KinematicValidator.INDEX_MCP] = Point3D(0.4f, 0.5f)
        this[KinematicValidator.PINKY_MCP] = Point3D(0.6f, 0.5f)
        this[KinematicValidator.INDEX_TIP] = Point3D(0.55f, 0.5f)
        this[KinematicValidator.MIDDLE_TIP] = Point3D(0.56f, 0.5f)
    }
}
