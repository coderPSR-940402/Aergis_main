package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraClockTest {
    private fun ns(ms: Long) = ms * 1_000_000L

    @Test fun detectsElapsedRealtimeWhenDeviceHasSleptSinceBoot() {
        // elapsed = 10 h, uptime = 4 h; the frame was captured 40 ms ago on the elapsed clock.
        val elapsed = 36_000_000L; val uptime = 14_400_000L
        assertEquals(FrameTimeSource.CAMERA_ELAPSED_REALTIME,
            CameraClock.detect(ns(elapsed - 40), uptime, elapsed))
    }

    @Test fun detectsUptimeDomain() {
        val elapsed = 36_000_000L; val uptime = 14_400_000L
        assertEquals(FrameTimeSource.CAMERA_UPTIME, CameraClock.detect(ns(uptime - 40), uptime, elapsed))
    }

    @Test fun missingOrImplausibleTimestampFallsBackToAnalyzerClock() {
        assertEquals(FrameTimeSource.ANALYZER_UPTIME, CameraClock.detect(0L, 1_000L, 2_000L))
        assertEquals(FrameTimeSource.ANALYZER_UPTIME, CameraClock.detect(ns(5L), 1_000_000L, 2_000_000L))
    }
}
