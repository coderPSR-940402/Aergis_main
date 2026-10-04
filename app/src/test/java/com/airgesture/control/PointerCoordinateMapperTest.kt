package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerCoordinateMapperTest {
    @Test
    fun mirrorsHorizontalAxisAndPreservesVerticalAxis() {
        val left = PointerCoordinateMapper.map(0f, 0.25f)
        val right = PointerCoordinateMapper.map(1f, 0.75f)
        assertEquals(1f, left.x, 0.0001f)
        assertEquals(0f, right.x, 0.0001f)
        assertEquals(0.24f, left.y, 0.01f)
        assertEquals(0.76f, right.y, 0.01f)
    }

    @Test
    fun clampsCoordinatesToScreenBounds() {
        val point = PointerCoordinateMapper.map(-1f, 2f)
        assertTrue(point.x in 0f..1f)
        assertTrue(point.y in 0f..1f)
    }

    @Test
    fun calibratedMappingUsesVersionedBoundsAndOptionalMirror() {
        val mirrored = PointerCoordinateMapper.map(
            x = 0.2f,
            y = 0.5f,
            profile = PointerCalibrationProfile.DEFAULT
        )
        val unmirrored = PointerCoordinateMapper.map(
            x = 0.2f,
            y = 0.5f,
            profile = PointerCalibrationProfile.DEFAULT.copy(mirrorX = false)
        )

        assertEquals(0.875f, mirrored.x, 0.0001f)
        assertEquals(0.125f, unmirrored.x, 0.0001f)
        assertEquals(0.5f, mirrored.y, 0.0001f)
    }

    @Test
    fun invalidCalibrationFallsBackToSafeDefaultProfile() {
        val invalid = PointerCalibrationProfile(
            schemaVersion = 1,
            left = 0.8f,
            right = 0.2f,
            top = 0.4f,
            bottom = 0.5f
        )

        assertTrue(!invalid.isValid())
        val fallback = PointerCoordinateMapper.map(0.2f, 0.5f, invalid)
        val expected = PointerCoordinateMapper.map(0.2f, 0.5f, PointerCalibrationProfile.DEFAULT)
        assertEquals(expected, fallback)
    }
}
