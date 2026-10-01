package com.airgesture.control.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveKalmanFilterTest {
    @Test
    fun keepsMotionWithinNormalizedBounds() {
        val filter = AdaptiveKalmanFilter()
        val initial = filter.filter(0.2f, 0.3f, 1000L)
        val updated = filter.filter(0.25f, 0.32f, 1016L)
        assertTrue(initial.x in 0.0f..1.0f)
        assertTrue(initial.y in 0.0f..1.0f)
        assertTrue(updated.x in 0.0f..1.0f)
        assertTrue(updated.y in 0.0f..1.0f)
    }

    @Test
    fun rejectsOneFrameSpikeWithoutFreezingOrTeleporting() {
        val filter = AdaptiveKalmanFilter()
        filter.filter(0.5f, 0.5f, 1000L)
        val spike = filter.filter(0.98f, 0.98f, 1033L)
        val recovered = filter.filter(0.54f, 0.52f, 1066L)
        assertTrue(spike.x < 0.82f && spike.y < 0.82f)
        assertEquals(0.54f, recovered.x, 0.08f)
        assertEquals(0.52f, recovered.y, 0.08f)
    }

    @Test
    fun followsSustainedFastMotionWithLowLag() {
        val filter = AdaptiveKalmanFilter()
        filter.filter(0.1f, 0.5f, 0L)
        var result = Point3D(0f, 0f, 0f)
        for (i in 1..6) result = filter.filter(0.1f + i * 0.08f, 0.5f, i * 33L)
        assertTrue("tracker lagged: ${result.x}", result.x > 0.48f)
    }
}
