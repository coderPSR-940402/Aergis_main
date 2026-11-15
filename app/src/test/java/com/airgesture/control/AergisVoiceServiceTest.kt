package com.airgesture.control

import android.Manifest
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AergisVoiceServiceTest {
    @Test fun unconfiguredStartNeverActivatesMicrophoneSession() {
        // CI/default builds deliberately ship no broker configuration.
        org.junit.Assume.assumeTrue(BuildConfig.VOICE_TOKEN_BROKER_URL.isEmpty())
        val controller = Robolectric.buildService(AergisVoiceService::class.java).create()
        val service = controller.get()
        shadowOf(service.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        try {
            val result = service.onStartCommand(Intent(service, AergisVoiceService::class.java)
                .setAction(AergisVoiceService.ACTION_START)
                .putExtra(AergisVoiceService.EXTRA_ACCESS_TOKEN, "temporary-broker-token"), 0, 1)
            assertEquals(android.app.Service.START_NOT_STICKY, result)
            assertFalse(AergisVoiceService.status.value.active)
            assertFalse(AergisVoiceService.isConnected())
        } finally { controller.destroy() }
    }
    @Test fun processRestartWithoutExplicitStartNeverActivatesVoice() {
        val controller = Robolectric.buildService(AergisVoiceService::class.java).create()
        try {
            controller.get().onStartCommand(null, 0, 1)
            assertFalse(AergisVoiceService.status.value.active)
        } finally { controller.destroy() }
    }
}
