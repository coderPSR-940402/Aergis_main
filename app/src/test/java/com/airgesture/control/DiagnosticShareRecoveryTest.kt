package com.airgesture.control

import android.view.accessibility.AccessibilityEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DiagnosticShareRecoveryTest {
    @After fun reset() {
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.setForegroundContext(ForegroundContextState())
    }

    @Test fun returningFromShareRestoresPointerWithoutAnotherAccessibilityEvent() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            AirRuntime.controlMode = ControlMode.ARMED
            AirRuntime.pointerEnabled = true
            AirRuntime.gesturesEnabled = false
            // The chooser can be implemented by the resolver package on Android/Samsung.
            AirRuntime.setForegroundContext(ForegroundContextPolicy.evaluate(
                "com.android.intentresolver", "com.android.intentresolver.ChooserActivity"))
            assertFalse(pointerActive())
            activity.get().onWindowFocusChanged(true)
            assertEquals(ForegroundSafety.SAFE, AirRuntime.state.value.foregroundContext.safety)
            assertTrue(pointerActive())
            assertEquals(ControlMode.ARMED, AirRuntime.controlMode)
            assertFalse(AirRuntime.gesturesEnabled)
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun unfocusedActivityCannotClearProtectionOrArmControl() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            AirRuntime.controlMode = ControlMode.READY
            AirRuntime.setForegroundContext(ForegroundContextPolicy.evaluate(
                "com.android.permissioncontroller", "GrantPermissionsActivity"))
            activity.get().onWindowFocusChanged(false)
            assertNotEquals(ForegroundSafety.SAFE, AirRuntime.state.value.foregroundContext.safety)
            assertEquals(ControlMode.READY, AirRuntime.controlMode)
            assertFalse(ActionSafetyPolicy.evaluate(AirAction.TAP, true, ControlMode.ARMED,
                false, AirRuntime.state.value.foregroundContext.safety).allowed)
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun protectedScreenStillBlocksActionsAfterRecovery() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        val service = Robolectric.buildService(AirAccessibilityService::class.java).create()
        try {
            AirRuntime.controlMode = ControlMode.ARMED
            activity.get().onWindowFocusChanged(true)
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
                packageName = "com.android.permissioncontroller"
                className = "GrantPermissionsActivity"
            }
            service.get().onAccessibilityEvent(event)
            assertEquals(ForegroundSafety.PROTECTED, AirRuntime.state.value.foregroundContext.safety)
            assertFalse(ActionSafetyPolicy.evaluate(AirAction.TAP, true, ControlMode.ARMED,
                false, AirRuntime.state.value.foregroundContext.safety).allowed)
        } finally { service.destroy(); activity.pause().stop().destroy() }
    }

    private fun pointerActive() = PointerTrackingPolicy.isActive(AirRuntime.pointerEnabled,
        AirRuntime.controlMode, false, AirRuntime.state.value.foregroundContext.safety)
}
