package com.airgesture.control
import org.junit.Assert.*
import org.junit.Test
class ActionFeedbackTest {
    @Test fun oldCallbackCannotOverwriteNewerRequest() {
        val latest = ActionFeedback(2,AirAction.HOME,updatedAtMs=100)
        assertEquals(latest,latest.result(1,ActionDispatchOutcome.COMPLETED,110))
        assertEquals(ActionDispatchOutcome.PLATFORM_REJECTED, latest.result(2,ActionDispatchOutcome.PLATFORM_REJECTED,120).outcome)
    }
    @Test fun terminalOutcomeCannotRegressAndFeedbackExpires() {
        val submitted = ActionFeedback(1,AirAction.TAP,ActionDispatchOutcome.SUBMITTED,0)
        val done = submitted.result(1,ActionDispatchOutcome.COMPLETED,10)
        assertEquals(done,done.result(1,ActionDispatchOutcome.SUBMITTED,20))
        assertEquals(done,done.result(1,ActionDispatchOutcome.CANCELLED,30))
        assertTrue(done.visibleAt(4010)); assertFalse(done.visibleAt(4011)); assertFalse(done.visibleAt(0))
    }
}
