package com.airgesture.control.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LowPassFilterTest {
    @Test fun firstCallReturnsRawValueRegardlessOfAlpha() {
        val filter = LowPassFilter()
        assertEquals(7f, filter.filter(7f, alpha = 0.05f), 0f)
    }

    @Test fun subsequentCallBlendsTowardNewValueByAlpha() {
        val filter = LowPassFilter()
        filter.filter(10f, alpha = 1f)
        val result = filter.filter(20f, alpha = 0.5f)
        assertEquals(15f, result, 1e-4f)
    }

    @Test fun lowAlphaStaysCloseToPreviousValue() {
        val filter = LowPassFilter()
        filter.filter(0f, alpha = 1f)
        val result = filter.filter(100f, alpha = 0.01f)
        assertEquals(1f, result, 1e-4f)
    }

    @Test fun constantInputStaysConstantRegardlessOfAlpha() {
        val filter = LowPassFilter()
        filter.filter(5f, alpha = 0.9f)
        val result = filter.filter(5f, alpha = 0.02f)
        assertEquals(5f, result, 1e-5f)
    }

    @Test fun lastValueIsNullBeforeAnyCalls() {
        assertNull(LowPassFilter().lastValue())
    }

    @Test fun lastValueTracksMostRecentFilteredResult() {
        val filter = LowPassFilter()
        val result = filter.filter(10f, alpha = 1f)
        assertEquals(result, filter.lastValue())
    }

    @Test fun resetClearsStateSoNextCallIsRawAgain() {
        val filter = LowPassFilter()
        filter.filter(10f, alpha = 1f)
        filter.filter(20f, alpha = 0.5f)
        filter.reset()
        assertNull(filter.lastValue())
        assertEquals(3f, filter.filter(3f, alpha = 0.05f), 0f)
    }
}
