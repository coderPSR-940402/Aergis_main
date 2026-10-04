package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerReplayBenchmarkTest {
    @Test
    fun replayReportsZeroErrorForExpectedLegacyMapping() {
        val trace = PointerReplayTrace(
            traceId = "legacy-corners-v1",
            frames = listOf(
                PointerReplayFrame(0, 100L, 0.10f, 0.08f, 1f, 0f),
                PointerReplayFrame(1, 116L, 0.50f, 0.50f, 0.5f, 0.5f),
                PointerReplayFrame(2, 132L, 0.90f, 0.92f, 0f, 1f)
            )
        )

        val report = PointerReplayBenchmark.run(trace) { x, y ->
            PointerCoordinateMapper.map(x, y)
        }

        assertTrue(report.isValid)
        assertEquals(3, report.metrics?.frameCount)
        assertEquals(32L, report.metrics?.durationMs)
        assertEquals(0f, report.metrics?.meanAbsoluteError ?: -1f, 0.0001f)
        assertEquals(0f, report.metrics?.p95AbsoluteError ?: -1f, 0.0001f)
        assertNotNull(report.metrics?.meanOutputStep)
    }

    @Test
    fun replayRejectsInvalidCoordinatesAndOutOfOrderFramesBeforeMapping() {
        val trace = PointerReplayTrace(
            traceId = "invalid-v1",
            frames = listOf(
                PointerReplayFrame(0, 100L, 0.2f, 0.2f),
                PointerReplayFrame(2, 90L, 1.2f, 0.2f)
            )
        )

        val report = PointerReplayBenchmark.run(trace) { _, _ ->
            error("invalid trace must not invoke mapper")
        }

        assertFalse(report.isValid)
        assertNull(report.metrics)
        assertTrue(report.validation.errors.any { it.contains("normalized") })
        assertTrue(report.validation.errors.any { it.contains("contiguous") })
        assertTrue(report.validation.errors.any { it.contains("out of order") })
    }

    @Test
    fun replayCalculatesP95AndOutputStepMetricsDeterministically() {
        val trace = PointerReplayTrace(
            traceId = "metric-v1",
            frames = listOf(
                PointerReplayFrame(0, 0L, 0f, 0f, 0f, 0f),
                PointerReplayFrame(1, 10L, 0.5f, 0.5f, 0.4f, 0.5f),
                PointerReplayFrame(2, 20L, 1f, 1f, 1f, 1f),
                PointerReplayFrame(3, 30L, 0.5f, 0.5f, 0.6f, 0.5f)
            )
        )

        val report = PointerReplayBenchmark.run(trace, { x, y ->
            PointerCoordinateMapper.Point(x, y)
        })
        val metrics = report.metrics

        assertTrue(report.isValid)
        assertEquals(0.05f, metrics?.meanAbsoluteError ?: -1f, 0.0001f)
        assertEquals(0.1f, metrics?.p95AbsoluteError ?: -1f, 0.0001f)
        assertEquals(0.70710677f, metrics?.maxOutputStep ?: -1f, 0.0001f)
    }
}
