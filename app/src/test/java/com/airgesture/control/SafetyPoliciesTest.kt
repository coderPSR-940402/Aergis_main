package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPoliciesTest {
    @Test fun allConfiguredActionsDependOnlyOnExplicitControlAndForegroundState() {
        for (action in AirAction.entries.filter { it != AirAction.NONE }) {
            assertTrue(action.name, ActionSafetyPolicy.evaluate(action, true, ControlMode.ARMED, ForegroundSafety.SAFE).allowed)
            for (mode in listOf(ControlMode.OFF, ControlMode.READY, ControlMode.PAUSED)) {
                assertFalse(ActionSafetyPolicy.evaluate(action, true, mode, ForegroundSafety.SAFE).allowed)
            }
            assertFalse(ActionSafetyPolicy.evaluate(action, false, ControlMode.ARMED, ForegroundSafety.SAFE).allowed)
            for (safety in listOf(ForegroundSafety.UNKNOWN, ForegroundSafety.PROTECTED)) {
                assertFalse(ActionSafetyPolicy.evaluate(action, true, ControlMode.ARMED, safety).allowed)
            }
        }
    }

    @Test
    fun settingsIsProtectedWithoutBlockingSimilarPackages() {
        assertEquals(ForegroundSafety.PROTECTED,
            ForegroundContextPolicy.evaluate("com.android.settings", "Settings").safety)
        assertEquals(ForegroundSafety.SAFE,
            ForegroundContextPolicy.evaluate("com.android.settings.example", "MainActivity").safety)
    }

    @Test
    fun notificationShadeDoesNotLatchTheProtectedState() {
        assertEquals(ForegroundSafety.SAFE,
            ForegroundContextPolicy.evaluate("com.android.systemui", "NotificationShadeWindowView").safety)
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
            foregroundSafety = ForegroundSafety.SAFE
        )

        assertTrue(decision.allowed)
    }
}
