package com.airgesture.control.filtering

import com.airgesture.control.GestureInterpreter
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PartialPinchEvidenceTest {
    private fun pose() = MutableList(21) { Point3D(.5f,.5f) }.apply {
        this[5]=Point3D(.4f,.5f); this[17]=Point3D(.6f,.5f)
        this[4]=Point3D(.5f,.4f); this[12]=Point3D(.51f,.4f); this[8]=Point3D(.5f,.3f)
    }

    @Test fun irrelevantMissingLandmarksDoNotVetoPointerContact() {
        for(i in listOf(0,1,2,3,6,7,9,10,11,13,14,15,16,18,19,20)) {
            val p=pose(); p[i]=Point3D(Float.NaN,Float.NaN,Float.NaN)
            assertTrue("landmark $i",PoseGeometryEvidenceEvaluator().evaluatePointer(p).accepted)
            assertFalse(PoseGeometryEvidenceEvaluator().evaluate(p).accepted)
        }
    }

    @Test fun rigidPalmRollPreservesThumbMiddleContactRatio() {
        val frontal = MutableList(21) { Point3D(.5f,.5f) }.apply {
            this[5]=Point3D(.4f,.5f,0f); this[17]=Point3D(.6f,.5f,0f)
            this[4]=Point3D(.485f,.4f,0f); this[12]=Point3D(.515f,.4f,0f)
            this[8]=Point3D(.5f,.3f,0f)
        }
        val rolled = frontal.toMutableList().apply {
            this[5]=rotateAroundY(frontal[5], .5f, 60f)
            this[17]=rotateAroundY(frontal[17], .5f, 60f)
            this[4]=rotateAroundY(frontal[4], .5f, 60f)
            this[12]=rotateAroundY(frontal[12], .5f, 60f)
        }
        val validator=KinematicValidator()
        val expected=validator.calculateNormalizedFingerDistance(frontal)
        assertEquals(.15f, expected, .0001f)
        assertEquals("Rigid hand roll must not change physical thumb-middle contact evidence",
            expected, validator.calculateNormalizedFingerDistance(rolled), .01f)
    }

    @Test fun naturalPalmRollCannotTurnAnOpenHandIntoAClick() {
        val interpreter=GestureInterpreter()
        val open=MutableList(21){Point3D(.5f,.5f)}.apply {
            this[5]=Point3D(.4f,.5f,0f); this[17]=Point3D(.6f,.5f,0f)
            this[4]=Point3D(.46f,.4f,0f); this[12]=Point3D(.54f,.4f,0f)
            this[8]=Point3D(.5f,.3f,0f)
        }
        val rolled=open.toMutableList().apply {
            this[5]=rotateAroundY(open[5], .5f, 80f)
            this[17]=rotateAroundY(open[17], .5f, 80f)
            this[4]=rotateAroundY(open[4], .5f, 80f)
            this[12]=rotateAroundY(open[12], .5f, 80f)
        }
        assertFalse(interpreter.processFrame(open, 1000L)!!.isClickEngaged)
        val firstRolled=interpreter.processFrame(rolled, 1033L)!!
        val secondRolled=interpreter.processFrame(rolled, 1083L)!!
        assertTrue(firstRolled.smoothedX.isFinite() && firstRolled.smoothedY.isFinite())
        assertTrue(secondRolled.smoothedX.isFinite() && secondRolled.smoothedY.isFinite())
        assertFalse("Palm roll during ordinary pointing must never synthesize thumb-middle contact", firstRolled.isClickEngaged)
        assertFalse("Sustained rolled pointing must remain an open non-clicking state", secondRolled.isClickEngaged)
    }

    @Test fun fingertipPointerSurvivesUnrelatedOffCameraLandmarks() {
        val interpreter=GestureInterpreter()
        val p=pose()
        p[8]=Point3D(.73f,.82f,0f)
        for(i in listOf(0,1,2,3,6,7,9,10,11,13,14,15,16,18,19,20)) p[i]=Point3D(1.2f,-.2f,0f)
        val result=interpreter.processFrame(p, 100L)
        assertNotNull(result)
        assertEquals(.73f,result!!.smoothedX,.0001f)
        assertEquals(.82f,result.smoothedY,.0001f)
        assertFalse(result.isClickEngaged)
    }

    private fun rotateAroundY(point: Point3D, centerX: Float, degrees: Float): Point3D {
        val radians=Math.toRadians(degrees.toDouble())
        val dx=point.x-centerX
        return Point3D(
            centerX+(dx*cos(radians)).toFloat(),
            point.y,
            (dx*sin(radians)).toFloat()
        )
    }
}
