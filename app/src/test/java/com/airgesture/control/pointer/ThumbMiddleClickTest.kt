package com.airgesture.control.pointer

import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test

class ThumbMiddleClickTest {
    @Test fun pinchDistanceUsesThumbAndMiddleWhileIndexKeepsAiming() {
        val pose = pose(thumbX = 0.46f, middleX = 0.47f, indexX = 0.60f)
        assertEquals(0.05f, KinematicValidator().calculateNormalizedFingerDistance(pose), 0.0001f)
        pose[8] = Point3D(0.35f, 0.4f)
        assertEquals(0.05f, KinematicValidator().calculateNormalizedFingerDistance(pose), 0.0001f)
    }

    @Test fun indexMiddleContactDoesNotCountAsThumbMiddleContact() {
        val pose = pose(thumbX = 0.20f, middleX = 0.55f, indexX = 0.55f)
        assertEquals(1.75f, KinematicValidator().calculateNormalizedFingerDistance(pose), 0.0001f)
    }

    @Test fun acquiringAnAlreadyClosedPinchCannotClick() {
        val machine = ClickHysteresisStateMachine()
        for (time in listOf(1L, 34L, 100L, 180L)) {
            assertFalse(machine.processFrame(0.10f, time))
            assertEquals(ClickHysteresisStateMachine.State.IDLE, machine.getCurrentState())
        }
    }

    @Test fun approachStartsBeforeContactAndConfirmationUsesElapsedTime() {
        val machine = ClickHysteresisStateMachine()
        assertFalse(machine.processFrame(0.8f, 1L))
        assertFalse(machine.processFrame(0.30f, 34L))
        assertEquals(ClickHysteresisStateMachine.State.ENGAGING, machine.getCurrentState())
        assertFalse(machine.processFrame(0.10f, 67L))
        assertFalse(machine.processFrame(0.10f, 80L))
        assertFalse(machine.processFrame(0.10f, 100L))
        assertTrue(machine.processFrame(0.10f, 127L))
        assertFalse(machine.processFrame(0.10f, 200L))
        assertFalse(machine.processFrame(0.10f, 300L))
    }

    @Test fun aBriefContactCannotClickAndSeparationAllowsAnotherClick() {
        val machine = ClickHysteresisStateMachine()
        machine.processFrame(0.8f, 1L)
        assertFalse(machine.processFrame(0.10f, 34L))
        assertFalse(machine.processFrame(0.8f, 60L))
        assertFalse(machine.processFrame(0.10f, 100L))
        assertTrue(machine.processFrame(0.10f, 160L))
        machine.processFrame(0.8f, 200L)
        assertFalse(machine.processFrame(0.10f, 220L))
        machine.processFrame(0.8f, 340L)
        assertFalse(machine.processFrame(0.10f, 380L))
        assertTrue(machine.processFrame(0.10f, 440L))
    }

    @Test fun invalidOrStaleEvidenceRequiresOpeningAgain() {
        for ((distance, time) in listOf(Float.NaN to 70L, Float.POSITIVE_INFINITY to 70L,
            -0.1f to 70L, 0.1f to 34L, 0.1f to 400L)) {
            val machine = ClickHysteresisStateMachine()
            machine.processFrame(0.8f, 1L)
            machine.processFrame(0.1f, 34L)
            assertFalse(machine.processFrame(distance, time))
            assertFalse(machine.processFrame(0.1f, time + 70L))
            machine.processFrame(0.8f, time + 100L)
            assertFalse(machine.processFrame(0.1f, time + 130L))
            assertTrue(machine.processFrame(0.1f, time + 190L))
        }
    }

    private fun pose(thumbX: Float, middleX: Float, indexX: Float) =
        MutableList(21) { Point3D(0.5f, 0.5f) }.apply {
            this[4] = Point3D(thumbX, 0.5f)
            this[5] = Point3D(0.4f, 0.5f)
            this[17] = Point3D(0.6f, 0.5f)
            this[8] = Point3D(indexX, 0.5f)
            this[12] = Point3D(middleX, 0.5f)
        }
}
