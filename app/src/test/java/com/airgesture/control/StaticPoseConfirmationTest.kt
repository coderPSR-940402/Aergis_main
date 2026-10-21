package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class StaticPoseConfirmationTest {
    @Test fun oneTransientClassifierFrameCanNeverConfirmACommand() {
        val gate = StaticPoseConfirmation()
        assertFalse(gate.accept("Victory", "owner", 0L, 100L, true, 1500L))
        gate.reset()
        assertFalse(gate.accept("Open_Palm", "owner", 0L, 200L, true, 100L))
    }

    @Test fun highRateCandidateConfirmsAfterThreeConsecutiveFreshFramesWithoutLegacyHoldDelay() {
        val gate = StaticPoseConfirmation()
        assertFalse(gate.accept("Victory", "owner", 0L, 100L, true, 1500L))
        assertFalse(gate.accept("Victory", "owner", 0L, 133L, true, 1500L))
        assertTrue(gate.accept("Victory", "owner", 0L, 166L, true, 1500L))
    }

    @Test fun lowRateCandidateCanConfirmAfterTwoFramesSpanningARealConfirmationWindow() {
        val gate = StaticPoseConfirmation()
        assertFalse(gate.accept("Victory", "owner", 0L, 100L, true, 1500L))
        assertTrue(gate.accept("Victory", "owner", 0L, 200L, true, 1500L))
    }

    @Test fun changingPoseOwnerOrEpochRestartsConfirmation() {
        val gate = StaticPoseConfirmation()
        assertFalse(gate.accept("Victory", "owner", 0L, 100L, true, 100L))
        assertFalse(gate.accept("Open_Palm", "owner", 0L, 133L, true, 100L))
        assertFalse(gate.accept("Open_Palm", "new-owner", 0L, 166L, true, 100L))
        assertFalse(gate.accept("Open_Palm", "new-owner", 1L, 199L, true, 100L))
        assertFalse(gate.accept("Open_Palm", "new-owner", 1L, 232L, true, 100L))
        assertTrue(gate.accept("Open_Palm", "new-owner", 1L, 265L, true, 100L))
    }

    @Test fun invalidEvidenceBreaksAPartialCandidateAndFailsClosed() {
        val gate = StaticPoseConfirmation()
        assertFalse(gate.accept("Victory", "owner", 0L, 100L, true, 100L))
        assertFalse(gate.accept("Victory", "owner", 0L, 133L, false, 100L))
        assertFalse(gate.accept("Victory", "owner", 0L, 166L, true, 100L))
        assertFalse(gate.accept("Victory", "owner", 0L, 199L, true, 100L))
        assertTrue(gate.accept("Victory", "owner", 0L, 232L, true, 100L))
        assertFalse(gate.accept("Victory", null, 0L, 300L, true, 100L))
        assertFalse(gate.accept("Victory", "", 0L, 300L, true, 100L))
        assertFalse(gate.accept("Victory", "owner", 0L, -1L, true, 100L))
    }
}
