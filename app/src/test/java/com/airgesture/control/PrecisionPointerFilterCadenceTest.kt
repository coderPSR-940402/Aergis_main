package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The jump gate is a physical-speed limit: a result cadence change must not make normal motion an outlier. */
class PrecisionPointerFilterCadenceTest {
    @Test fun stationaryNoiseRemainsSuppressedAtActualSlowCameraRates() {
        for (fps in listOf(4, 5, 8, 15, 30, 60)) {
            val filter = PrecisionPointerFilter()
            var inputEnergy = 0.0
            var outputEnergy = 0.0
            for (i in 0..fps * 10) {
                val noise = .006f * kotlin.math.sin(i * 1.7f)
                val point = filter.update(.5f + noise, .5f, 1000L + i * 1000L / fps)
                if (i > fps * 2) {
                    inputEnergy += noise.toDouble() * noise
                    outputEnergy += (point.x - .5).let { it * it }
                }
            }
            val ratio = kotlin.math.sqrt(outputEnergy / inputEnergy)
            assertTrue("$fps fps stationary RMS ratio $ratio", ratio < .45)
        }
    }

    @Test fun slowCadenceFineAimAndReversalsStayResponsiveWithoutOvershoot() {
        for (fps in listOf(4, 5, 8)) {
            val filter = PrecisionPointerFilter()
            var previous = filter.update(.3f, .5f, 1000)
            for (i in 1..fps * 6) {
                val target = if (i <= fps * 3) .3f + .03f * i / fps
                    else .39f - .03f * (i - fps * 3) / fps
                val point = filter.update(target, .5f, 1000L + i * 1000L / fps)
                assertTrue("Fine aim must not become sticky at $fps fps", kotlin.math.abs(point.x - target) < .012f)
                assertTrue(point.x in minOf(previous.x, target)..maxOf(previous.x, target))
                previous = point
            }
        }
    }
    @Test fun normalFastTravelIsTrustedAtSlowResultCadence() {
        val filter = PrecisionPointerFilter()
        filter.update(.2f, .5f, 1000)
        // 0.35 screen widths in 200 ms (1.75 screens/s) is an ordinary swipe, not a landmark glitch.
        filter.update(.55f, .5f, 1200)
        assertTrue(filter.measurementTrusted)
        assertEquals(PrecisionRejection.NONE, filter.lastRejection)
    }

    @Test fun sameDisplacementInOneFrameIsStillHeldAsASpike() {
        val filter = PrecisionPointerFilter()
        filter.update(.2f, .5f, 1000)
        filter.update(.55f, .5f, 1033) // 0.35 in 33 ms = 10.6 screens/s: physically implausible
        assertTrue(!filter.measurementTrusted)
        assertEquals(PrecisionRejection.JUMP_HELD, filter.lastRejection)
        assertTrue(filter.lastJumpDistance > filter.lastJumpLimit)
    }

    @Test fun invalidAndOutOfOrderInputsReportTheirReason() {
        val filter = PrecisionPointerFilter()
        filter.update(.3f, .5f, 1000)
        filter.update(.3f, .5f, 900)
        assertEquals(PrecisionRejection.INVALID_OR_OUT_OF_ORDER, filter.lastRejection)
        filter.update(Float.NaN, .5f, 1100)
        assertEquals(PrecisionRejection.INVALID_OR_OUT_OF_ORDER, filter.lastRejection)
    }
}
