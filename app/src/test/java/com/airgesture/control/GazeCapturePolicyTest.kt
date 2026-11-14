package com.airgesture.control

import com.airgesture.control.gaze.GazeCapturePolicy
import com.airgesture.control.gaze.GazeLab
import org.junit.Assert.*
import org.junit.Test

class GazeCapturePolicyTest {
    private fun sample(time: Long, x: Double = .3, y: Double = .4) =
        GazeLab.Observation(doubleArrayOf(x, y, .5, .5), time, "Eyes detected")

    @Test fun stableCurrentTargetCapturesAfterSettling() {
        val observations = (0..7).map { sample(1000L + it * 100L) }
        assertNull(GazeCapturePolicy.automaticCapture(observations, 1750L, 1000L))
        val settled = (0..7).map { sample(2200L + it * 100L) }
        assertNotNull(GazeCapturePolicy.automaticCapture(settled, 2900L, 1000L))
    }

    @Test fun priorTargetAndStaleObservationsCannotCaptureTheNextTarget() {
        val prior = (0..7).map { sample(1000L + it * 100L) }
        assertNull(GazeCapturePolicy.stableMedian(prior, 1700L, 1750L))
        assertNull(GazeCapturePolicy.stableMedian(prior, 2200L, 1000L))
        assertNull(GazeCapturePolicy.automaticCapture(prior, 2900L, 1000L))
    }

    @Test fun unstableOrInvalidFeaturesFailClosed() {
        val moving = (0..7).map { sample(2200L + it * 100L, if (it == 3) .7 else .3) }
        assertNull(GazeCapturePolicy.stableMedian(moving, 2900L, 2000L))
        val nonFinite = (0..7).map { sample(2200L + it * 100L) }.toMutableList()
        nonFinite[5] = sample(2700L, Double.NaN)
        assertNull(GazeCapturePolicy.stableMedian(nonFinite, 2900L, 2000L))
    }

    @Test fun duplicateTimestampsCannotFakeFiveSamples() {
        val repeated = List(8) { sample(2700L) }
        assertNull(GazeCapturePolicy.stableMedian(repeated, 2750L, 1000L))
        assertNull(GazeCapturePolicy.automaticCapture(repeated, 2750L, 1000L))
    }
}
