package com.airgesture.control

import android.app.Service
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GestureCaptureServiceRestartTest {
    @Test
    fun notificationActionsRevokeInputAndNeverArmCapture() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val service = Robolectric.buildService(GestureCaptureService::class.java).get()
        val notification = GestureCaptureService::class.java.getDeclaredMethod("notification")
            .apply { isAccessible = true }.invoke(service) as android.app.Notification
        val actions = notification.actions.map { action ->
            org.robolectric.Shadows.shadowOf(action.actionIntent).savedIntent
        }
        assertEquals(listOf(GestureCaptureService.ACTION_PAUSE, GestureCaptureService.ACTION_STOP),
            actions.map { it.action })
        val receiver = GestureCaptureNotificationReceiver()
        try {
            AirRuntime.running = true
            AirRuntime.controlMode = ControlMode.ARMED
            val armedEpoch = AirRuntime.actionEpoch
            receiver.onReceive(context, actions[0])
            assertEquals(ControlMode.PAUSED, AirRuntime.controlMode)
            org.junit.Assert.assertTrue(AirRuntime.actionEpoch > armedEpoch)
            receiver.onReceive(context, actions[0])
            assertEquals(ControlMode.PAUSED, AirRuntime.controlMode)
            receiver.onReceive(context, actions[1])
            assertEquals(ControlMode.OFF, AirRuntime.controlMode)

            AirRuntime.running = false
            receiver.onReceive(context, actions[0])
            assertEquals(ControlMode.OFF, AirRuntime.controlMode)
        } finally {
            AirRuntime.running = false
            AirRuntime.controlMode = ControlMode.OFF
        }
    }

    @Test
    fun startedCaptureDoesNotRequestAutomaticProcessRestart() {
        // Do not call create(): this tests restart policy without opening a camera.
        val service = Robolectric.buildService(GestureCaptureService::class.java).get()
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent(), 0, 1))
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 2))
    }
}
