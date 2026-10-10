package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The jump gate is a physical-speed limit: a result cadence change must not make normal motion an outlier. */
class PrecisionPointerFilterCadenceTest {
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
