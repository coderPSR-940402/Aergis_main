package com.airgesture.control

import android.util.Range
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackingFrameRateTest {
    @Test fun prefersFixed60OverVariable60AndHigherRates() {
        assertEquals(Range(60, 60), TrackingFrameRate.select(setOf(
            Range(30, 60), Range(120, 120), Range(30, 30), Range(60, 60))))
    }

    @Test fun usesAdvertisedVariable60WhenFixed60IsUnavailable() {
        assertEquals(Range(30, 60), TrackingFrameRate.select(setOf(
            Range(15, 60), Range(30, 30), Range(30, 60))))
    }

    @Test fun fallsBackToFastestSupportedRangeBelow60() {
        assertEquals(Range(30, 30), TrackingFrameRate.select(setOf(
            Range(15, 30), Range(15, 15), Range(30, 30))))
    }

    @Test fun neverInventsA60FpsRangeOrRequestsHighSpeedCapture() {
        assertNull(TrackingFrameRate.select(emptySet()))
        assertNull(TrackingFrameRate.select(setOf(Range(60, 120), Range(120, 120))))
        assertEquals(Range(24, 24), TrackingFrameRate.select(setOf(Range(24, 24))))
    }
}
