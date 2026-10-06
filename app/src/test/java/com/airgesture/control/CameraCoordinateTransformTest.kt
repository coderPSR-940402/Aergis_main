package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCoordinateTransformTest {
    @Test
    fun rotatesImageCoordinatesClockwiseIntoUprightSpace() {
        val cases = listOf(
            Triple(0, 0.2f, 0.3f),
            Triple(90, 0.7f, 0.2f),
            Triple(180, 0.8f, 0.7f),
            Triple(270, 0.3f, 0.8f)
        )
        for ((rotation, expectedX, expectedY) in cases) {
            val result = CameraCoordinateTransform.toUpright(0.2f, 0.3f, -0.1f, rotation)
            assertEquals("X for rotation $rotation", expectedX, result.x, 0.0001f)
            assertEquals("Y for rotation $rotation", expectedY, result.y, 0.0001f)
            assertEquals(-0.1f, result.z, 0.0001f)
        }
    }

    @Test
    fun portraitSensorMovementMapsToTheCorrectScreenAxis() {
        // A 90-degree camera buffer: sensor Y controls upright X; sensor X controls upright Y.
        val center = CameraCoordinateTransform.toUpright(0.5f, 0.5f, 0f, 90)
        val screenCenter = PointerCoordinateMapper.map(center.x, center.y)
        val screenRight = CameraCoordinateTransform.toUpright(0.5f, 0.7f, 0f, 90).let {
            PointerCoordinateMapper.map(it.x, it.y)
        }
        val screenUp = CameraCoordinateTransform.toUpright(0.3f, 0.5f, 0f, 90).let {
            PointerCoordinateMapper.map(it.x, it.y)
        }
        assertTrue(screenRight.x > screenCenter.x)
        assertEquals(screenCenter.y, screenRight.y, 0.0001f)
        assertTrue(screenUp.y < screenCenter.y)
        assertEquals(screenCenter.x, screenUp.x, 0.0001f)
    }

    @Test
    fun calibrationReceivesUprightCoordinatesBeforeMirroring() {
        val upright = CameraCoordinateTransform.toUpright(0.5f, 0.8f, 0f, 90)
        val screen = PointerCoordinateMapper.map(upright.x, upright.y, PointerCalibrationProfile.DEFAULT)
        assertEquals(0.875f, screen.x, 0.0001f)
        assertEquals(0.5f, screen.y, 0.0001f)
    }

    @Test
    fun rotationsPreserveCornersAndInvalidEvidence() {
        val corner = CameraCoordinateTransform.toUpright(0f, 1f, 0f, 270)
        assertEquals(1f, corner.x, 0f)
        assertEquals(1f, corner.y, 0f)
        val invalid = CameraCoordinateTransform.toUpright(Float.NaN, 0.5f, 0f, 90)
        assertFalse(invalid.y.isFinite())
        val outside = CameraCoordinateTransform.toUpright(1.2f, 0.5f, 0f, 90)
        assertTrue(outside.y > 1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedRotationIsRejected() {
        CameraCoordinateTransform.toUpright(0.5f, 0.5f, 0f, 45)
    }
}
