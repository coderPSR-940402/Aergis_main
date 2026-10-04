package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDispatchGateTest {
    @Test
    fun rejectsQueuedActionWhenRuntimeEpochChanged() {
        val decision = ActionDispatchGate.evaluate(
            action = AirAction.TAP,
            expectedEpoch = 10L,
            currentEpoch = 11L,
            gesturesEnabled = true,
            controlMode = ControlMode.ARMED,
            motionActive = false,
            foregroundSafety = ForegroundSafety.SAFE
        )

        assertFalse(decision.allowed)
        assertEquals("Runtime safety state changed", decision.reason)
    }

    @Test
    fun allowsMatchingEpochOnlyWhenPolicyAlsoAllowsAction() {
        val decision = ActionDispatchGate.evaluate(
            action = AirAction.SCROLL_UP,
            expectedEpoch = 7L,
            currentEpoch = 7L,
            gesturesEnabled = true,
            controlMode = ControlMode.ARMED,
            motionActive = false,
            foregroundSafety = ForegroundSafety.SAFE
        )

        assertTrue(decision.allowed)
        assertEquals("Allowed", decision.reason)
    }

    @Test
    fun currentPolicyStillBlocksMatchingEpochOnProtectedScreen() {
        val decision = ActionDispatchGate.evaluate(
            action = AirAction.BACK,
            expectedEpoch = 4L,
            currentEpoch = 4L,
            gesturesEnabled = true,
            controlMode = ControlMode.ARMED,
            motionActive = false,
            foregroundSafety = ForegroundSafety.PROTECTED
        )

        assertFalse(decision.allowed)
        assertEquals("Protected screen detected", decision.reason)
    }

    @Test
    fun nullExpectedEpochSupportsInitialDispatchCheck() {
        val decision = ActionDispatchGate.evaluate(
            action = AirAction.TAP,
            expectedEpoch = null,
            currentEpoch = 99L,
            gesturesEnabled = false,
            controlMode = ControlMode.ARMED,
            motionActive = false,
            foregroundSafety = ForegroundSafety.SAFE
        )

        assertFalse(decision.allowed)
        assertEquals("Gesture actions disabled", decision.reason)
    }
}
