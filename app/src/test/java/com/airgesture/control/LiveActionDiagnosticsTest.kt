package com.airgesture.control

import android.os.SystemClock
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveActionDiagnosticsTest {
    @After fun reset() = AergisDevLoop.uninstall()

    @Test fun serviceRejectionAndCentralFeedbackUseTheSameAuthoritativeRequest() {
        val events = mutableListOf<LiveDiagnosticSnapshot>()
        AergisDevLoop.install(LiveDiagnostics(LiveDiagnosticBuffer(100, 60_000),
            LiveDiagnosticEventSink(events::add), postWindowMs = 0))
        val controller = Robolectric.buildService(AirAccessibilityService::class.java).create()
        try {
            AirRuntime.controlMode = ControlMode.OFF
            controller.get().dispatchPointerTap()
            val request = AirRuntime.state.value.actionFeedback!!.requestId
            assertEquals(1, events.size)
            assertEquals(request, events.single().trigger.requestId)
            assertEquals("policy_denied", events.single().trigger.reasonCode)
            assertEquals("TAP", events.single().trigger.action)
        } finally { controller.destroy() }
    }

    @Test fun centralCompletionSuppressesTheExistingClickTimeout() {
        val events = mutableListOf<LiveDiagnosticSnapshot>()
        val live = LiveDiagnostics(LiveDiagnosticBuffer(100, 60_000),
            LiveDiagnosticEventSink(events::add), postWindowMs = 0)
        AergisDevLoop.install(live)
        val now = SystemClock.uptimeMillis()
        val request = AirRuntime.beginActionFeedback(AirAction.TAP)
        AergisDevLoop.openAction(request, now, "TAP", 321f, 654f, 7, 8)
        AirRuntime.recordActionFeedback(ActionDispatchOutcome.COMPLETED, request)
        live.sample(LiveDiagnosticSample(now + 1000, 100_000))
        assertTrue(events.isEmpty())
    }
}
