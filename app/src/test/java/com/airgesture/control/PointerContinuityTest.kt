package com.airgesture.control

import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import com.airgesture.control.pointer.SwipeDirection
import org.junit.Assert.*
import org.junit.Test

class PointerContinuityTest {
    @Test
    fun phoneMotionKeepsPointerActiveButBlocksActions() {
        assertTrue(PointerTrackingPolicy.isActive(true, ControlMode.ARMED, true, ForegroundSafety.SAFE))
        assertFalse(ActionSafetyPolicy.evaluate(AirAction.TAP, true, ControlMode.ARMED, true, ForegroundSafety.SAFE).allowed)
    }

    @Test
    fun pointerStillRespectsDisablePauseAndForegroundGates() {
        assertFalse(PointerTrackingPolicy.isActive(false, ControlMode.ARMED, true, ForegroundSafety.SAFE))
        for (mode in listOf(ControlMode.OFF, ControlMode.READY, ControlMode.PAUSED)) {
            assertFalse(PointerTrackingPolicy.isActive(true, mode, false, ForegroundSafety.SAFE))
        }
        for (safety in listOf(ForegroundSafety.UNKNOWN, ForegroundSafety.PROTECTED)) {
            assertFalse(PointerTrackingPolicy.isActive(true, ControlMode.ARMED, false, safety))
        }
    }

    @Test
    fun validTipSurvivesNonFinitePalmLandmarks() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            for (joint in listOf(KinematicValidator.INDEX_MCP, KinematicValidator.PINKY_MCP)) {
                val pose = closePose().toMutableList().apply { this[joint] = Point3D(bad, 0.5f) }
                val result = GestureInterpreter().processFrame(pose, 1L)
                assertNotNull("Valid tip must survive unusable palm", result)
                assertEquals(0.55f, result!!.smoothedX, 0.0001f)
                assertFalse(result.poseEvidence.accepted)
                assertFalse(result.isClickEngaged)
                assertEquals(SwipeDirection.NONE, result.detectedSwipe)
            }
        }
    }

    @Test
    fun edgeTipIsNotPulledIntoDistortedPalmGeometry() {
        for (tip in listOf(Point3D(0.02f, 0.5f), Point3D(0.98f, 0.5f), Point3D(0.5f, 0.98f))) {
            val pose = closePose().toMutableList().apply {
                this[KinematicValidator.INDEX_TIP] = tip
                this[KinematicValidator.PINKY_MCP] = Point3D(0.401f, 0.5f)
            }
            val result = GestureInterpreter().processFrame(pose, 1L)!!
            assertEquals(tip.x, result.smoothedX, 0.0001f)
            assertEquals(tip.y, result.smoothedY, 0.0001f)
            assertFalse(result.poseEvidence.accepted)
            assertFalse(result.isClickEngaged)
        }
    }

    @Test
    fun motionFramesClearClickDwellWithoutResettingPointerFilters() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(closePose().toMutableList().apply {
            this[KinematicValidator.THUMB_TIP] = Point3D(0.2f, 0.5f)
        }, 0L)
        interpreter.processFrame(closePose(), 1L)
        val moving = interpreter.processFrame(closePose(), 34L, actionsAllowed = false)!!
        assertTrue(moving.smoothedX.isFinite())
        assertTrue(moving.poseEvidence.accepted)
        assertFalse(moving.isClickEngaged)
        assertEquals(SwipeDirection.NONE, moving.detectedSwipe)
        val resumed = interpreter.processFrame(closePose(), 67L)!!
        assertFalse("Motion must cancel prior click dwell", resumed.isClickEngaged)
    }

    @Test
    fun fingertipFallbackSelectsOneEligibleHandWithoutPalmEvidence() {
        assertEquals(3, PointerHandFallback.selectIndex(
            listOf(PointerHandCandidate(3, "Right", Point3D(0.02f, 0.98f))),
            ControlHandPreference.EITHER
        ))
    }

    @Test
    fun fingertipFallbackNeverChoosesBetweenTwoEligibleHands() {
        val candidates = listOf(
            PointerHandCandidate(0, "Left", Point3D(0.2f, 0.5f)),
            PointerHandCandidate(1, "Right", Point3D(0.8f, 0.5f))
        )
        assertNull(PointerHandFallback.selectIndex(candidates, ControlHandPreference.EITHER))
        assertEquals(1, PointerHandFallback.selectIndex(candidates, ControlHandPreference.RIGHT))
    }

    @Test
    fun fingertipFallbackRejectsMissingTipNonFiniteTipAndWrongHand() {
        assertNull(PointerHandFallback.selectIndex(listOf(PointerHandCandidate(0, "Left", null)), ControlHandPreference.EITHER))
        assertNull(PointerHandFallback.selectIndex(listOf(PointerHandCandidate(0, "Left", Point3D(Float.NaN, 0.5f))), ControlHandPreference.EITHER))
        assertNull(PointerHandFallback.selectIndex(listOf(PointerHandCandidate(0, "Left", Point3D(0.5f, Float.POSITIVE_INFINITY))), ControlHandPreference.EITHER))
        assertNull(PointerHandFallback.selectIndex(listOf(PointerHandCandidate(0, "Left", Point3D(0.5f, 0.5f))), ControlHandPreference.RIGHT))
    }

    private fun closePose(): List<Point3D> = MutableList(21) { Point3D(0.5f, 0.5f) }.apply {
        this[KinematicValidator.INDEX_MCP] = Point3D(0.4f, 0.5f)
        this[KinematicValidator.PINKY_MCP] = Point3D(0.6f, 0.5f)
        this[KinematicValidator.INDEX_TIP] = Point3D(0.55f, 0.5f)
        this[KinematicValidator.THUMB_TIP] = Point3D(0.55f, 0.5f)
        this[KinematicValidator.MIDDLE_TIP] = Point3D(0.56f, 0.5f)
    }
}
