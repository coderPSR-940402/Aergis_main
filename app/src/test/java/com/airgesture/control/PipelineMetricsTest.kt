package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineMetricsTest {
    @Test fun percentilesAndMaxDescribeTheRecordedDistribution() {
        val metrics = PipelineMetrics(window = 100)
        for (i in 1..100) metrics.recordFrame(i * 33L, 10, 2, i.toLong(), 3, i.toLong() + 20)
        val snapshot = metrics.snapshot(3_300L)
        assertEquals(50L, snapshot.inference.p50)
        assertEquals(95L, snapshot.inference.p95)
        assertEquals(100L, snapshot.inference.max)
        assertEquals(120L, snapshot.frameToPointer.max)
    }

    @Test fun ringKeepsOnlyTheNewestWindow() {
        val metrics = PipelineMetrics(window = 10)
        repeat(50) { metrics.recordFrame(it * 10L, 0, 0, 500, 0, 500) }
        repeat(10) { metrics.recordFrame(1_000L + it * 10L, 0, 0, 20, 0, 20) }
        assertEquals(20L, metrics.snapshot(1_100L).inference.max)
    }

    @Test fun ratesAreMeasuredFromEventSpacingAndGoStaleWhenEventsStop() {
        val metrics = PipelineMetrics()
        for (i in 0..30) metrics.recordAcceptedPointer(i * 50L) // 20 Hz
        assertEquals(20f, metrics.snapshot(1_500L).acceptedPointerHz, 0.5f)
        assertEquals(0f, metrics.snapshot(10_000L).acceptedPointerHz, 0f)
    }

    @Test fun rejectionReasonsAndOutOfOrderSkipsAreCounted() {
        val metrics = PipelineMetrics()
        metrics.recordRejection("VISION_STALE_RESULT"); metrics.recordRejection("VISION_STALE_RESULT")
        metrics.recordOutOfOrderSkip()
        val snapshot = metrics.snapshot(0L)
        assertEquals(2L, snapshot.rejections["VISION_STALE_RESULT"])
        assertEquals(1L, snapshot.outOfOrderSkipped)
        assertTrue(snapshot.summary().contains("frame→pointer"))
    }
}
