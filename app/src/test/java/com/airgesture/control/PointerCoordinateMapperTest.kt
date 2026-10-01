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
}
