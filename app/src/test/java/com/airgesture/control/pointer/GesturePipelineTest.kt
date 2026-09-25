package com.airgesture.control.pointer

import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturePipelineTest {
    @Test
    fun clickRequiresDwellAndDoesNotRepeatUntilRelease() {
        val machine = ClickHysteresisStateMachine(
            engageThreshold = 0.18f,
            releaseThreshold = 0.32f,
            dwellRequiredFrames = 2,
            refractoryPeriodMs = 150L
        )

        assertFalse(machine.processFrame(0.10f, 0L))
        assertTrue(machine.processFrame(0.10f, 33L))
        assertFalse(machine.processFrame(0.10f, 66L))
        assertEquals(ClickHysteresisStateMachine.State.CLICKED, machine.getCurrentState())

        assertFalse(machine.processFrame(0.40f, 99L))
        assertEquals(ClickHysteresisStateMachine.State.REFRACTORY, machine.getCurrentState())
        assertFalse(machine.processFrame(0.40f, 260L))
        assertEquals(ClickHysteresisStateMachine.State.IDLE, machine.getCurrentState())
    }

    @Test
    fun swipeDetectsRightwardMotion() {
        val engine = SwipeGestureEngine(
            windowSize = 5,
            minVelocityScreensPerSec = 0.8f,
            returnCooldownMs = 300L
        )

        var direction = SwipeDirection.NONE
        for (i in 0 until 5) {
            direction = engine.processFrame(
                Point3D(x = i * 0.05f, y = 0.5f),
                i * 33L
            )
        }

        assertEquals(SwipeDirection.RIGHT, direction)
    }

    @Test
    fun kinematicValidatorConstrainsImpossibleFingerLength() {
        val landmarks = MutableList(18) { Point3D(0.5f, 0.5f) }
        landmarks[KinematicValidator.INDEX_MCP] = Point3D(0.4f, 0.5f)
        landmarks[KinematicValidator.PINKY_MCP] = Point3D(0.6f, 0.5f)
        landmarks[KinematicValidator.INDEX_TIP] = Point3D(0.95f, 0.5f)

        val constrained = KinematicValidator().validateAndConstrainIndexTip(landmarks, null)
        val palmWidth = landmarks[KinematicValidator.INDEX_MCP]
            .distance2DTo(landmarks[KinematicValidator.PINKY_MCP])
        val boneLength = landmarks[KinematicValidator.INDEX_MCP].distance2DTo(constrained)

        assertTrue(boneLength <= palmWidth * 1.35f + 0.0001f)
    }
}
