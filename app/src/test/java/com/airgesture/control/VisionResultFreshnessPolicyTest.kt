package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionResultFreshnessPolicyTest {
    @Test
    fun acceptsOrderedFreshResults() {
        val policy = VisionResultFreshnessPolicy(maxAgeMs = 220L, maxGapMs = 250L)

        assertTrue(policy.evaluate(1_000L, 1_050L).accepted)
        val decision = policy.evaluate(1_100L, 1_150L)

        assertTrue(decision.accepted)
        assertEquals(VisionResultFreshnessPolicy.RejectionReason.ACCEPTED, decision.reason)
        assertEquals(100L, decision.gapMs)
        assertFalse(decision.requiresRecovery)
    }

    @Test
    fun rejectsDuplicateAndOutOfOrderResults() {
        val policy = VisionResultFreshnessPolicy()
        policy.evaluate(1_000L, 1_020L)

        val duplicate = policy.evaluate(1_000L, 1_030L)
        val outOfOrder = policy.evaluate(999L, 1_040L)

        assertEquals(VisionResultFreshnessPolicy.RejectionReason.DUPLICATE_TIMESTAMP, duplicate.reason)
        assertEquals(VisionResultFreshnessPolicy.RejectionReason.OUT_OF_ORDER_TIMESTAMP, outOfOrder.reason)
        assertTrue(duplicate.requiresRecovery)
        assertTrue(outOfOrder.requiresRecovery)
    }

    @Test
    fun rejectsStaleResultAndAllowsFreshRecovery() {
        val policy = VisionResultFreshnessPolicy(maxAgeMs = 220L)
        policy.evaluate(1_000L, 1_020L)

        val stale = policy.evaluate(1_100L, 1_321L)
        val recovery = policy.evaluate(1_150L, 1_170L)

        assertFalse(stale.accepted)
        assertEquals(VisionResultFreshnessPolicy.RejectionReason.STALE_RESULT, stale.reason)
        assertTrue(stale.requiresRecovery)
        assertTrue(recovery.accepted)
    }

    @Test
    fun rejectsExcessiveGapButUsesItAsRecoveryWatermark() {
        val policy = VisionResultFreshnessPolicy(maxGapMs = 250L)
        policy.evaluate(1_000L, 1_020L)

        val gap = policy.evaluate(1_300L, 1_320L)
        val recovery = policy.evaluate(1_350L, 1_370L)

        assertFalse(gap.accepted)
        assertEquals(VisionResultFreshnessPolicy.RejectionReason.EXCESSIVE_GAP, gap.reason)
        assertEquals(300L, gap.gapMs)
        assertTrue(recovery.accepted)
    }

    @Test
    fun resetStartsAUsableNewStream() {
        val policy = VisionResultFreshnessPolicy()
        policy.evaluate(5_000L, 5_010L)
        policy.reset()

        val decision = policy.evaluate(100L, 120L)

        assertTrue(decision.accepted)
        assertEquals(0L, decision.gapMs)
    }

    @Test
    fun rejectsInvalidConfigurationAndNegativeTimestamps() {
        assertThrows<IllegalArgumentException> {
            VisionResultFreshnessPolicy(maxAgeMs = -1L)
        }
        assertThrows<IllegalArgumentException> {
            VisionResultFreshnessPolicy().evaluate(-1L, 0L)
        }
        assertThrows<IllegalArgumentException> {
            VisionResultFreshnessPolicy().evaluate(0L, -1L)
        }
    }

    @Test
    fun rejectsImpossibleObservationClockOrder() {
        val decision = VisionResultFreshnessPolicy().evaluate(200L, 100L)

        assertFalse(decision.accepted)
        assertEquals(
            VisionResultFreshnessPolicy.RejectionReason.INVALID_CLOCK_ORDER,
            decision.reason
        )
        assertTrue(decision.requiresRecovery)
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (throwable: Throwable) {
            assertTrue(throwable is T)
            return
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
