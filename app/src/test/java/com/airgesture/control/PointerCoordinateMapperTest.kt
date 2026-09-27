package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class PointerCoordinateMapperTest {
    @Test
    fun mapsCameraTopLeftToScreenTopRight() {
        val point = PointerCoordinateMapper.map(0.02f, 0.02f)
        assertEquals(1f, point.x, 0.0001f)
        assertEquals(0f, point.y, 0.0001f)
    }

    @Test
    fun mapsCameraTopRightToScreenTopLeft() {
        val point = PointerCoordinateMapper.map(0.98f, 0.02f)
        assertEquals(0f, point.x, 0.0001f)
        assertEquals(0f, point.y, 0.0001f)
    }

    @Test
    fun mapsCameraBottomLeftToScreenBottomRight() {
        val point = PointerCoordinateMapper.map(0.02f, 0.98f)
        assertEquals(1f, point.x, 0.0001f)
        assertEquals(1f, point.y, 0.0001f)
    }

    @Test
    fun mapsCameraBottomRightToScreenBottomLeft() {
        val point = PointerCoordinateMapper.map(0.98f, 0.98f)
        assertEquals(0f, point.x, 0.0001f)
        assertEquals(1f, point.y, 0.0001f)
    }

    @Test
    fun mapsCenterToCenter() {
        val point = PointerCoordinateMapper.map(0.5f, 0.5f)
        assertEquals(0.5f, point.x, 0.0001f)
        assertEquals(0.5f, point.y, 0.0001f)
    }

    @Test
    fun mapsNearEdgeWithoutEightPercentDeadZone() {
        val point = PointerCoordinateMapper.map(0.08f, 0.5f)
        assertEquals(0.9375f, point.x, 0.0001f)
        assertEquals(0.5f, point.y, 0.0001f)
    }
}
