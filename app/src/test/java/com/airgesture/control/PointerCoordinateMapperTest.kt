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
    fun verticalMovementStaysVertical() {
        val top = PointerCoordinateMapper.map(0.5f, 0.1f)
        val bottom = PointerCoordinateMapper.map(0.5f, 0.9f)
        assertEquals(top.x, bottom.x, 0.0001f)
        assertEquals((0.1f - 0.02f) / 0.96f, top.y, 0.0001f)
        assertEquals((0.9f - 0.02f) / 0.96f, bottom.y, 0.0001f)
    }

    @Test
    fun horizontalMovementStaysHorizontalAndMirrored() {
        val left = PointerCoordinateMapper.map(0.1f, 0.5f)
        val right = PointerCoordinateMapper.map(0.9f, 0.5f)
        assertEquals((0.9f - 0.02f) / 0.96f, left.x, 0.0001f)
        assertEquals((0.1f - 0.02f) / 0.96f, right.x, 0.0001f)
        assertEquals(left.y, right.y, 0.0001f)
    }

    @Test
    fun mapsNearEdgeWithoutEightPercentDeadZone() {
        val point = PointerCoordinateMapper.map(0.5f, 0.08f)
        assertEquals(0.5f, point.x, 0.0001f)
        assertEquals(0.0625f, point.y, 0.0001f)
    }
}
