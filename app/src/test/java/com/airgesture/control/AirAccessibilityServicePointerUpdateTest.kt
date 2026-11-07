package com.airgesture.control

import android.os.Looper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class AirAccessibilityServicePointerUpdateTest {
    @Test fun hiddenUpdatesSkipMainThreadUntilVisibilityOrAuthorityChanges() {
        val controller = Robolectric.buildService(AirAccessibilityService::class.java).create()
        val service = controller.get()
        val mainLooper = Looper.getMainLooper()
        try {
            AirAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
                .apply { isAccessible = true }.invoke(service)
            val overlay = AirAccessibilityService::class.java.getDeclaredField("pointerOverlay")
                .apply { isAccessible = true }.get(service) as PointerOverlay
            AirRuntime.controlMode = ControlMode.ARMED
            AirRuntime.pointerEnabled = true

            service.updatePointer(.5f, .5f, true)
            shadowOf(mainLooper).idle()
            assertTrue(overlay.isHotspotVisible)

            service.updatePointer(0f, 0f, false)
            shadowOf(mainLooper).idle()
            assertFalse(overlay.isHotspotVisible)
            assertTrue(mainLooper.queue.isIdle)

            repeat(5) { service.updatePointer(0f, 0f, false) }
            assertTrue("An already hidden pointer should leave the main queue idle", mainLooper.queue.isIdle)

            AirRuntime.invalidatePendingActions()
            shadowOf(mainLooper).idle()
            service.updatePointer(0f, 0f, false)
            assertFalse("A new authority epoch must process its hidden update", mainLooper.queue.isIdle)
            shadowOf(mainLooper).idle()

            service.updatePointer(.5f, .5f, true)
            shadowOf(mainLooper).idle()
            assertTrue(overlay.isHotspotVisible)
        } finally {
            controller.destroy()
            AirRuntime.pointerEnabled = true
            AirRuntime.controlMode = ControlMode.OFF
            AirRuntime.setForegroundContext(ForegroundContextState())
        }
    }
}
