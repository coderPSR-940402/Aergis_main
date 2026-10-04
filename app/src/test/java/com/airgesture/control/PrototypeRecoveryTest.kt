package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import com.airgesture.control.pointer.CenterGateSwipeDetector
import com.airgesture.control.pointer.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrototypeRecoveryTest {
    @Test
    fun visionTelemetryReportsLatencyYieldAndStaleDrops() {
        val monitor = VisionPerformanceMonitor()
        monitor.recordSubmitted(0L)
        monitor.recordSubmitted(33L)
        monitor.recordResult(0L, 40L)
        monitor.recordStaleResultDropped()
        val snapshot = monitor.snapshot()
        assertEquals(2L, snapshot.submittedFrames)
        assertEquals(1L, snapshot.resultFrames)
        assertEquals(1L, snapshot.staleDroppedResults)
        assertEquals(50f, snapshot.resultYieldPercent, 0.001f)
        assertEquals(50f, snapshot.staleDropPercent, 0.001f)
        assertEquals(40L, snapshot.lastResultAgeMs)
        assertEquals(40L, snapshot.inferenceMs)
        assertEquals(0, snapshot.inFlight)
    }

    @Test
    fun latencyPolicyRejectsNegativeAndStaleResults() {
        assertTrue(VisionLatencyPolicy.resultIsFreshEnough(pointer = true, ageMs = 220L))
        assertFalse(VisionLatencyPolicy.resultIsFreshEnough(pointer = true, ageMs = 221L))
        assertFalse(VisionLatencyPolicy.resultIsFreshEnough(pointer = true, ageMs = -1L))
        assertEquals(2, VisionLatencyPolicy.maxInFlight(pointer = false))
    }

    @Test
    fun calibrationMapsAndClampsWithIndependentAxisGain() {
        val calibration = PointerCalibration(
            left = 0.1f,
            top = 0.1f,
            right = 0.9f,
            bottom = 0.9f,
            xGain = 1.2f,
            yGain = 0.8f
        )
        val mapped = PointerCoordinateMapper.map(0.9f, 0.5f, calibration)
        assertEquals(0f, mapped.x, 0.0001f)
        assertEquals(0.4f, mapped.y, 0.0001f)
    }

    @Test
    fun centerGateRequiresCrossingAndRejectsDiagonalMotion() {
        val detector = CenterGateSwipeDetector(minDisplacement = 0.12f, minSpeedPerSecond = 0.2f)
        assertEquals(SwipeDirection.NONE, detector.update(Point3D(0.1f, 0.5f), 0L))
        assertEquals(SwipeDirection.RIGHT, detector.update(Point3D(0.9f, 0.5f), 400L))

        detector.reset()
        assertEquals(SwipeDirection.NONE, detector.update(Point3D(0.1f, 0.1f), 0L))
        assertEquals(SwipeDirection.NONE, detector.update(Point3D(0.9f, 0.9f), 400L))
    }
}
