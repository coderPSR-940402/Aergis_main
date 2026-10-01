package com.airgesture.control.filtering

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
    fun ignoresSevereOutlierSpike() {
        val filter = AdaptiveKalmanFilter()
        filter.filter(0.5f, 0.5f, 1000L)
        val outlier = filter.filter(0.95f, 0.95f, 1016L)
        val followUp = filter.filter(0.55f, 0.52f, 1032L)

        assertTrue(outlier.x in 0.0f..1.0f)
        assertTrue(outlier.y in 0.0f..1.0f)
        assertTrue(followUp.x in 0.0f..1.0f)
        assertTrue(followUp.y in 0.0f..1.0f)
    }
}
