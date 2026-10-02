package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPoliciesTest {
    @Test
    fun motionCancellationActivatesOnRotationAndReleasesAfterQuietPeriod() {
        val monitor = DeviceMotionCancellation(releaseHoldMs = 100L)

        assertTrue(monitor.update(1.5f, 0f, 0L).active)
        assertTrue(monitor.update(0.2f, 0.1f, 50L).active)
        assertFalse(monitor.update(0.2f, 0.1f, 150L).active)
    }

    @Test
    fun accelerationDeviationDetectsDeviceMovementWithoutAssumingOrientation() {
        val monitor = DeviceMotionCancellation()

        val state = monitor.update(0f, accelerationDeviation(0f, 0f, 13f), 0L)

        assertTrue(state.active)
    }

    @Test
    fun protectedForegroundContextsAreBlocked() {
        val context = ForegroundContextPolicy.evaluate(
            "com.android.systemui",
            "com.android.systemui.keyguard.KeyguardViewMediator"
        )

        assertEquals(ForegroundSafety.PROTECTED, context.safety)
        assertFalse(
            ActionSafetyPolicy.evaluate(
                AirAction.BACK,
                gesturesEnabled = true,
                controlMode = ControlMode.ARMED,
                motionActive = false,
                foregroundSafety = context.safety
            ).allowed
        )
    }

    @Test
    fun readyModeBlocksActionsUntilExplicitlyArmed() {
        val decision = ActionSafetyPolicy.evaluate(
            AirAction.TAP,
            gesturesEnabled = true,
            controlMode = ControlMode.READY,
            motionActive = false,
            foregroundSafety = ForegroundSafety.SAFE
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun armedSafeModeAllowsConfiguredActions() {
        val decision = ActionSafetyPolicy.evaluate(
            AirAction.SCROLL_UP,
            gesturesEnabled = true,
            controlMode = ControlMode.ARMED,
            motionActive = false,
            foregroundSafety = ForegroundSafety.SAFE
        )

        assertTrue(decision.allowed)
    }
}
