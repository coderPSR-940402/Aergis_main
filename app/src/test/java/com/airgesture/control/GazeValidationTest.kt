package com.airgesture.control

import com.airgesture.control.gaze.GazeValidation
import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class GazeValidationTest {
    @Test fun portraitScreenUsesPixelDimensionsInsteadOfSquareNormalizedCoordinates() {
        val horizontal = GazeValidation.measure(.6f, .5f, .5f, .5f, 1080f, 2400f)!!
        val vertical = GazeValidation.measure(.5f, .6f, .5f, .5f, 1080f, 2400f)!!
        assertEquals(108.0, horizontal.distancePx, .001)
        assertEquals(240.0, vertical.distancePx, .001)
        assertEquals(108.0 / hypot(1080.0, 2400.0) * 100.0,
            horizontal.diagonalPercent, .0001)
        assertTrue(vertical.diagonalPercent > horizontal.diagonalPercent * 2)
        assertEquals(240.0, vertical.verticalPx, .001)
    }

    @Test fun landscapeOrientationWeightsHorizontalDistanceMoreThanVertical() {
        val horizontal = GazeValidation.measure(.6f, .5f, .5f, .5f, 2400f, 1080f)!!
        val vertical = GazeValidation.measure(.5f, .6f, .5f, .5f, 2400f, 1080f)!!
        assertEquals(240.0, horizontal.horizontalPx, .001)
        assertEquals(108.0, vertical.verticalPx, .001)
        assertTrue(horizontal.diagonalPercent > vertical.diagonalPercent * 2)
    }

    @Test fun reportsSignedOffsetsAndRejectsInvalidScreenGeometry() {
        val error = GazeValidation.measure(.4f, .6f, .5f, .5f, 1000f, 2000f)!!
        assertEquals(-100.0, error.horizontalPx, .001)
        assertEquals(200.0, error.verticalPx, .001)
        assertNull(GazeValidation.measure(Float.NaN, .5f, .5f, .5f, 1000f, 2000f))
        assertNull(GazeValidation.measure(.5f, .5f, .5f, .5f, 0f, 2000f))
        assertNull(GazeValidation.measure(1.5f, .5f, .5f, .5f, 1000f, 2000f))
    }

    @Test fun medianAndWorstIndicesWorkForOddAndEvenHeldOutSets() {
        fun e(n: Double) = GazeValidation.Error(n, 0.0, 0.0, 0.0)
        val four = GazeValidation.summarize(listOf(e(13.2), e(6.4), e(6.0), e(4.5)))!!
        assertEquals(6.2, four.medianPercent, .0001)
        assertEquals(13.2, four.worstPercent, .0001)
        assertEquals(0, four.worstIndex)
        val five = GazeValidation.summarize(listOf(e(2.0), e(8.0), e(3.0), e(1.0), e(4.0)))!!
        assertEquals(3.0, five.medianPercent, .0001)
        assertEquals(1, five.worstIndex)
        assertNull(GazeValidation.summarize(emptyList()))
    }

    @Test fun validationTargetsCoverCanvasCornersEdgesAndCenterWithoutReusingTrainingPairs() {
        val targets = GazeValidation.targets
        assertEquals(9, targets.size)
        assertEquals(9, targets.map { it.x to it.y }.distinct().size)
        val training = listOf(.15f, .5f, .85f).flatMap { x ->
            listOf(.15f, .5f, .85f).map { y -> x to y }
        }
        assertTrue(targets.none { (it.x to it.y) in training })
        assertTrue(targets.any { it.label == "Center" })
        assertTrue(targets.any { it.label == "Top-left" })
        assertTrue(targets.any { it.label == "Bottom-right" })
    }
}
