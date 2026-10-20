package com.airgesture.control

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlatformActionsTest {
    @Test fun publicFlowsSubmitGenericIntentsWithoutClaimingCompletion() {
        val cases = mapOf(AirAction.WEB_SEARCH to Intent.ACTION_WEB_SEARCH,
            AirAction.WIFI_CONTROLS to Settings.ACTION_WIFI_SETTINGS,
            AirAction.BLUETOOTH_SETTINGS to Settings.ACTION_BLUETOOTH_SETTINGS)
        for ((action, expected) in cases) {
            var submitted: Intent? = null
            val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun startActivity(intent: Intent) { submitted = intent }
            }
            AirRuntime.resetActionDispatchTelemetry()
            PlatformActions(context).execute(action)
            assertEquals(expected, submitted?.action)
            assertNull(submitted?.component)
            assertNull(submitted?.`package`)
            assertEquals(ActionDispatchOutcome.SUBMITTED, AirRuntime.state.value.actionDispatchTelemetry.lastOutcome)
        }
    }

    @Test fun missingHandlerPermissionDenialAndExecutionFailureRemainDistinct() {
        val cases = listOf(ActivityNotFoundException() to ActionDispatchOutcome.PLATFORM_UNAVAILABLE,
            SecurityException() to ActionDispatchOutcome.PLATFORM_REJECTED,
            IllegalStateException() to ActionDispatchOutcome.EXECUTION_FAILED)
        for ((failure, expected) in cases) {
            val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun startActivity(intent: Intent) { throw failure }
            }
            AirRuntime.resetActionDispatchTelemetry()
            PlatformActions(context).execute(AirAction.WEB_SEARCH)
            assertEquals(expected, AirRuntime.state.value.actionDispatchTelemetry.lastOutcome)
            assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)
        }
    }
}
