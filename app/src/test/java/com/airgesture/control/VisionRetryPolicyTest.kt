package com.airgesture.control

import org.junit.Assert.*
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

    @Test fun gpuExecutionFailureSwitchesFutureFramesToCpuWithoutRetryingTheExposure() {
        val recovery = VisionDelegateRecovery()
        assertTrue(recovery.preferGpu)
        var calls = 0
        val failure = IllegalStateException("GPU execution failed")
        try {
            recovery.infer(onGpu = true) { calls++; throw failure }
            fail("Inference failures must reach the existing cleanup and retry path")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals(1, calls)
        assertFalse(recovery.preferGpu)
        assertEquals("fresh", recovery.infer(onGpu = false) { "fresh" })
        assertTrue(VisionDelegateRecovery().preferGpu)
    }

    @Test fun cpuExecutionFailureDoesNotDisableGpu() {
        val recovery = VisionDelegateRecovery()
        try { recovery.infer(onGpu = false) { throw IllegalStateException("CPU failure") } }
        catch (_: IllegalStateException) {}
        assertTrue(recovery.preferGpu)
    }
}
