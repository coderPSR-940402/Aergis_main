package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerMappingComparisonTest {
    private val calibratedTrace = PointerReplayTrace(
        traceId = "calibrated-corners-v1",
        frames = listOf(
            PointerReplayFrame(0, 0L, 0.10f, 0.08f, 1f, 0f)
        )
    )

    @Test
    fun calibratedProfileCanBeComparedAgainstLegacyBaseline() {
        val comparison = PointerMappingComparisonBenchmark.compare(
            trace = calibratedTrace,
            candidateMapper = { x, y ->
                PointerCoordinateMapper.map(x, y, PointerCalibrationProfile.DEFAULT)
            }
        )

        assertTrue(comparison?.passed == true)
        assertEquals(0f, comparison?.candidate?.meanAbsoluteError ?: -1f, 0.0001f)
        assertTrue((comparison?.meanErrorDelta ?: 0f) < 0f)
        assertTrue((comparison?.p95ErrorDelta ?: 0f) < 0f)
    }

    @Test
    fun candidateRegressionFailsWhenErrorExceedsExplicitThreshold() {
        val comparison = PointerMappingComparisonBenchmark.compare(
            trace = calibratedTrace,
            candidateMapper = { x, y ->
                PointerCoordinateMapper.Point(
                    (x + 0.2f).coerceIn(0f, 1f),
                    y
                )
            },
            thresholds = PointerMappingRegressionThresholds(
                maxMeanErrorIncrease = 0.01f,
                maxP95ErrorIncrease = 0.01f,
                maxPeakErrorIncrease = 0.01f
            )
        )

        assertFalse(comparison?.passed == true)
        assertTrue(comparison?.failures?.any { it.contains("error regression") } == true)
    }

    @Test
    fun invalidTraceProducesNoComparisonReport() {
        val invalid = calibratedTrace.copy(
            frames = calibratedTrace.frames.drop(1)
        )
        val comparison = PointerMappingComparisonBenchmark.compare(
            trace = invalid,
            candidateMapper = { x, y -> PointerCoordinateMapper.map(x, y) }
        )

        assertNull(comparison)
    }
}
