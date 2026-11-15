package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.async
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceRequestResultTest {
    @Test fun overlappingGestureDoesNotDiscardVoiceOutcome() {
        val first = AirRuntime.beginActionFeedback(AirAction.NEXT_ITEM)
        val pending = AirRuntime.observeActionResult(first)
        val next = AirRuntime.beginActionFeedback(AirAction.BACK)
        AirRuntime.recordActionFeedback(ActionDispatchOutcome.COMPLETED, first)
        assertEquals(ActionDispatchOutcome.COMPLETED, pending.value)
        assertEquals(next, AirRuntime.state.value.actionFeedback?.requestId)
        AirRuntime.releaseActionResult(first)
    }

    @Test fun submittedDoesNotReleaseWaiterBeforeTerminalOutcome() = kotlinx.coroutines.runBlocking {
        val request = AirRuntime.beginActionFeedback(AirAction.SELECT_ITEM)
        val pending = AirRuntime.observeActionResult(request)
        try {
            val waiter = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                awaitVoiceActionTerminal(pending, 1000L)
            }
            AirRuntime.recordActionFeedback(ActionDispatchOutcome.SUBMITTED, request)
            AirRuntime.beginActionFeedback(AirAction.BACK) // Replaces the visual slot only.
            kotlinx.coroutines.yield()
            assertFalse(waiter.isCompleted)
            AirRuntime.recordActionFeedback(ActionDispatchOutcome.CANCELLED, request)
            assertEquals(ActionDispatchOutcome.CANCELLED, waiter.await())
        } finally { AirRuntime.releaseActionResult(request) }
    }
    @Test fun submittedWithoutTerminalBecomesBoundedUnconfirmedResult() = kotlinx.coroutines.runBlocking {
        val pending = kotlinx.coroutines.flow.MutableStateFlow<ActionDispatchOutcome?>(ActionDispatchOutcome.SUBMITTED)
        assertNull(awaitVoiceActionTerminal(pending, 10L))
    }
}
