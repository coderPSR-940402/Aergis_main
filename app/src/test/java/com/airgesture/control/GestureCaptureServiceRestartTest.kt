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
    fun startedCaptureDoesNotRequestAutomaticProcessRestart() {
        // Do not call create(): this tests restart policy without opening a camera.
        val service = Robolectric.buildService(GestureCaptureService::class.java).get()
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(Intent(), 0, 1))
        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 2))
    }
}
