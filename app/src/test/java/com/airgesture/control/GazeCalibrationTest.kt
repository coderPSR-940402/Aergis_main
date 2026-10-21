package com.airgesture.control
import com.airgesture.control.gaze.GazeCalibration
import org.junit.Assert.*
import org.junit.Test
class GazeCalibrationTest {
    @Test fun personalizedFitPredictsAHeldOutTarget() {
        val fit = GazeCalibration()
        val samples = listOf(.1, .5, .9).flatMap { x -> listOf(.1, .5, .9).map { y ->
            GazeCalibration.Sample(doubleArrayOf(x, y, 0.0, 0.0), x, y)
        } }
        assertTrue(fit.fit(samples))
        val prediction = fit.estimate(doubleArrayOf(.3, .7, 0.0, 0.0))!!
        assertEquals(.3f, prediction.first, .01f); assertEquals(.7f, prediction.second, .01f)
    }
    @Test fun absentDegenerateAndNonFiniteCalibrationNeverProducesTarget() {
        val fit = GazeCalibration()
        assertNull(fit.estimate(doubleArrayOf(.5, .5, 0.0, 0.0)))
        assertFalse(fit.fit(List(9) { GazeCalibration.Sample(doubleArrayOf(.5,.5,0.0,0.0),.5,.5) }))
        assertNull(fit.estimate(doubleArrayOf(Double.NaN, .5, 0.0, 0.0)))
    }
}
