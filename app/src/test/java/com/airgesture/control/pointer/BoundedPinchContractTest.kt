package com.airgesture.control.pointer

import org.junit.Assert.*
import org.junit.Test

class BoundedPinchContractTest {
    @Test fun lowCadenceContactActsBeforeReleaseAndReleaseNeverActs() {
        for (interval in listOf(100L, 134L, 200L)) {
            val gate = ClickHysteresisStateMachine()
            assertFalse(gate.processFrame(.8f, 0L))
            assertTrue("Contact at $interval ms must act now", gate.processFrame(.1f, interval))
            assertFalse("Release only rearms", gate.processFrame(.8f, interval * 2))
        }
    }

    @Test fun persistentContactAndTrackingLossCannotRepeat() {
        val gate = ClickHysteresisStateMachine()
        gate.processFrame(.8f, 0L)
        assertTrue(gate.processFrame(.1f, 134L))
        for (t in 268L..2000L step 134L) assertFalse(gate.processFrame(.1f, t))
        gate.reset()
        for (t in 2100L..3000L step 100L) assertFalse(gate.processFrame(.1f, t))
        assertFalse(gate.processFrame(.8f, 3100L))
        assertTrue(gate.processFrame(.1f, 3234L))
    }

    @Test fun releaseCannotSynthesizeARejectedHighRateContact() {
        val gate = ClickHysteresisStateMachine()
        gate.processFrame(.8f, 0L)
        assertFalse(gate.processFrame(.1f, 16L))
        assertFalse(gate.processFrame(.8f, 150L))
    }
}
