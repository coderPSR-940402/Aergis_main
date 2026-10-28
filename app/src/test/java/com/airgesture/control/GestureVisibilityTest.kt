package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import com.airgesture.control.filtering.PoseGeometryEvidenceEvaluator
import org.junit.Assert.*
import org.junit.Test

class GestureVisibilityTest {
    private val evaluator = PoseGeometryEvidenceEvaluator()

    @Test fun customCommandAndNeutralIgnoreUnusedDistalJoints() {
        val points = LandmarkGestureRecognizerTest.pose(RegisteredGesture.FOUR_FINGERS).toMutableList()
        points[7] = Point3D(Float.NaN, Float.NaN, Float.NaN)
        points[19] = Point3D(1.1f, .5f)
        assertFalse(evaluator.evaluate(points).accepted)
        assertTrue(evaluator.evaluateGesture(points, RegisteredGesture.FOUR_FINGERS).accepted)
        assertTrue(evaluator.evaluateGesture(points, null).accepted)
    }

    @Test fun thumbOrientationRequiresDifferentJointsFromVictory() {
        val points = LandmarkGestureRecognizerTest.pose(RegisteredGesture.FOUR_FINGERS).toMutableList()
        points[3] = Point3D(Float.NaN, .5f)
        assertTrue(evaluator.evaluateGesture(points, RegisteredGesture.VICTORY).accepted)
        assertFalse(evaluator.evaluateGesture(points, RegisteredGesture.THUMB_UP).accepted)
        assertFalse(evaluator.evaluateGesture(points, RegisteredGesture.THUMB_DOWN).accepted)
        assertFalse(evaluator.evaluateGesture(points, null).accepted)
    }

    @Test fun relevantTipsCannotDisappearFromAnyRegisteredCommand() {
        for (gesture in RegisteredGesture.entries) for (joint in listOf(4, 8, 12, 16, 20)) {
            val points = LandmarkGestureRecognizerTest.pose(RegisteredGesture.FOUR_FINGERS).toMutableList()
            points[joint] = Point3D(Float.NaN, .5f)
            assertFalse("$gesture requires tip $joint", evaluator.evaluateGesture(points, gesture).accepted)
        }
    }
}
