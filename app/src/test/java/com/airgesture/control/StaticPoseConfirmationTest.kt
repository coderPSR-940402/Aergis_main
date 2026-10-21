package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class StaticPoseConfirmationTest {
    @Test fun legacyHoldValueCannotDelayAValidRecognizedOccurrence() {
        val gate = StaticPoseConfirmation()
        assertTrue(gate.accept("Victory", "owner", 0L, 100L, true, 1500L))
        assertTrue(gate.accept("Open_Palm", "owner", 0L, 101L, true, 100L))
    }

    @Test fun invalidEvidenceStillFailsClosed() {
        val gate = StaticPoseConfirmation()
        assertFalse(gate.accept("Victory", null, 0L, 100L, true, 100L))
        assertFalse(gate.accept("Victory", "", 0L, 100L, true, 100L))
        assertFalse(gate.accept("Victory", "owner", 0L, -1L, true, 100L))
        assertFalse(gate.accept("Victory", "owner", 0L, 100L, false, 100L))
    }
}
