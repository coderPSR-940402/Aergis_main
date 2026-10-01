package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VisionRetryPolicyTest {
    @Test
    fun delayUsesBoundedExponentialBackoff() {
        assertEquals(250L, VisionRetryPolicy.delayForFailure(1))
        assertEquals(500L, VisionRetryPolicy.delayForFailure(2))
        assertEquals(1_000L, VisionRetryPolicy.delayForFailure(3))
        assertEquals(VisionRetryPolicy.MAX_DELAY_MS, VisionRetryPolicy.delayForFailure(6))
        assertEquals(VisionRetryPolicy.MAX_DELAY_MS, VisionRetryPolicy.delayForFailure(100))
    }

    @Test
    fun failureCountMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            VisionRetryPolicy.delayForFailure(0)
        }
    }
}
