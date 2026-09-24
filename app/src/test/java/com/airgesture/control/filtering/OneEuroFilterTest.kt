package com.airgesture.control.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneEuroFilterTest {
    @Test fun firstSampleIsReturnedUnfiltered() {
        val filter = OneEuroFilter()
        assertEquals(10f, filter.filter(10f, 0L), 0f)
    }

    @Test fun constantInputStaysAtTheConstantValue() {
        val filter = OneEuroFilter()
        filter.filter(10f, 0L)
        repeat(10) { i -> assertEquals(10f, filter.filter(10f, (i + 1) * 33L), 1e-3f) }
    }

    @Test fun suddenJumpIsSmoothedNotPassedThroughInstantly() {
        val filter = OneEuroFilter()
        filter.filter(5f, 0L)
        val result = filter.filter(10f, 100L)
        assertTrue("expected $result to lag behind the jump to 10", result in 5f..9.999f)
    }

    @Test fun repeatedJumpsConvergeTowardTheNewValue() {
        val filter = OneEuroFilter()
        filter.filter(0f, 0L)
        var last = 0f
        var t = 0L
        repeat(30) {
            t += 33L
            last = filter.filter(10f, t)
        }
        assertEquals(10f, last, 0.05f)
    }

    @Test fun sameTimestampReturnsLastValueInsteadOfDividingByZero() {
        val filter = OneEuroFilter()
        filter.filter(5f, 0L)
        val result = filter.filter(99f, 0L)
        assertEquals(5f, result, 0f)
    }

    @Test fun resetReturnsFilterToFirstSampleBehavior() {
        val filter = OneEuroFilter()
        filter.filter(5f, 0L)
        filter.filter(10f, 100L)
        filter.reset()
        assertEquals(20f, filter.filter(20f, 200L), 0f)
    }

    @Test fun higherBetaTracksFastMotionMoreClosely() {
        val lowBeta = OneEuroFilter(minCutoff = 1.0f, beta = 0.0f, dCutoff = 1.0f)
        val highBeta = OneEuroFilter(minCutoff = 1.0f, beta = 5.0f, dCutoff = 1.0f)
        lowBeta.filter(0f, 0L)
        highBeta.filter(0f, 0L)
        val lowBetaResult = lowBeta.filter(10f, 33L)
        val highBetaResult = highBeta.filter(10f, 33L)
        assertTrue("expected higher beta ($highBetaResult) to track the jump more closely than low beta ($lowBetaResult)", highBetaResult > lowBetaResult)
    }
}
