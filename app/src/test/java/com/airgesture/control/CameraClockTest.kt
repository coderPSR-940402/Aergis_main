package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class CameraClockTest {
    @Test fun nearbyUnknownClockCannotBeUsedToRejectAFrameAsStale() {
        assertEquals(FrameTimeSource.ANALYZER_UPTIME,
            CameraClock.detect(ns(9_700), 10_000, 10_020))
    }
    private fun ns(ms: Long) = ms * 1_000_000L

    @Test fun detectsElapsedRealtimeWhenDeviceHasSleptSinceBoot() {
        // elapsed = 10 h, uptime = 4 h; the frame was captured 40 ms ago on the elapsed clock.
        val elapsed = 36_000_000L; val uptime = 14_400_000L
        assertEquals(FrameTimeSource.CAMERA_ELAPSED_REALTIME,
            CameraClock.detect(ns(elapsed - 40), uptime, elapsed, realtimeSource = true))
    }

    @Test fun unknownSourceUsesCaptureArrivalForAgeEvenWhenCloseToUptime() {
        val elapsed = 36_000_000L; val uptime = 14_400_000L
        assertEquals(FrameTimeSource.ANALYZER_UPTIME, CameraClock.detect(ns(uptime - 40), uptime, elapsed))
    }

    @Test fun clockOffsetDoesNotInventLatencyOrChangeSensorIntervals() {
        for (offset in listOf(-500_000L, -330L, 0L, 600L)) {
            val cameraMs = 1_000_000L + offset
            val source = CameraClock.detect(ns(cameraMs), 1_000_000L, 2_000_000L)
            assertEquals(170L, CameraClock.ageMs(source, ns(cameraMs), 1_000_000L, 1_000_170L, 2_000_170L))
            val first = CameraClock.sampleTimestampMs(ns(cameraMs), 1_000_000L)
            val next = CameraClock.sampleTimestampMs(ns(cameraMs + 33), 1_000_060L)
            assertEquals("Sensor cadence must not inherit callback jitter", 33L, next - first)
        }
    }

    @Test fun synchronizedCameraStillIncludesDeliveryDelayAndRejectsImpossibleClockOrder() {
        val source = FrameTimeSource.CAMERA_ELAPSED_REALTIME
        assertEquals(700L, CameraClock.ageMs(source, ns(2_000_000), 1_000_500, 1_000_700, 2_000_700))
        assertEquals(-1L, CameraClock.ageMs(source, ns(2_000_001), 1_000_000, 1_000_000, 2_000_000))
    }

    @Test fun exposureAuthorityUsesNanosecondsOnlyForComparableClocks() {
        val boundary = ns(2_000_000L) + 10L
        val realtime = FrameTimeSource.CAMERA_ELAPSED_REALTIME
        assertTrue(CameraClock.exposedBefore(realtime, boundary - 1, boundary))
        assertFalse(CameraClock.exposedBefore(realtime, boundary, boundary))
        assertFalse(CameraClock.exposedBefore(realtime, boundary + 1, boundary))
        assertFalse(CameraClock.exposedBefore(FrameTimeSource.ANALYZER_UPTIME, boundary - 1, boundary))
    }

    @Test fun deliveryLatencyIsAbsentForUnknownOrInvalidSensorTime() {
        assertEquals(181L, CameraClock.deliveryMs(FrameTimeSource.CAMERA_ELAPSED_REALTIME,
            ns(2_000_000), 2_000_181))
        assertNull(CameraClock.deliveryMs(FrameTimeSource.ANALYZER_UPTIME, ns(2_000_000), 2_000_181))
        assertNull(CameraClock.deliveryMs(FrameTimeSource.CAMERA_ELAPSED_REALTIME, ns(2_000_001), 2_000_000))
    }

    @Test fun missingOrImplausibleTimestampFallsBackToAnalyzerClock() {
        assertEquals(FrameTimeSource.ANALYZER_UPTIME, CameraClock.detect(0L, 1_000L, 2_000L))
        assertEquals(FrameTimeSource.ANALYZER_UPTIME, CameraClock.detect(ns(5L), 1_000_000L, 2_000_000L))
    }
}
