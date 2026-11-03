package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LiveInteractionTransactionTest {
    private fun click(
        requestId: Long = 42L,
        x: Float = 320f,
        y: Float = 640f
    ) = LiveInteractionTransaction.open(
        requestId = requestId,
        openedUptimeMs = 1_000L,
        action = "TAP",
        frozenTargetX = x,
        frozenTargetY = y,
        sessionEpoch = 7L,
        inputEpoch = 11L
    )

    @Test fun successfulCompletionIsTerminalAndPreservesIdentity() {
        val transaction = click()
        transaction.markDispatched(1_020L)

        val outcome = transaction.complete(1_080L)

        assertEquals(LiveInteractionTerminal.SUCCESS, outcome.terminal)
        assertEquals(42L, outcome.requestId)
        assertEquals("TAP", outcome.action)
        assertEquals(320f, outcome.frozenTargetX)
        assertEquals(640f, outcome.frozenTargetY)
        assertEquals(7L, outcome.sessionEpoch)
        assertEquals(11L, outcome.inputEpoch)
        assertEquals(1_020L, outcome.dispatchedUptimeMs)
        assertEquals(1_080L, outcome.finishedUptimeMs)
    }

    @Test fun dispatchFailureIsTerminal() {
        val outcome = click().failDispatch(1_015L, "gesture_rejected")

        assertEquals(LiveInteractionTerminal.FAILED, outcome.terminal)
        assertEquals("gesture_rejected", outcome.reason)
        assertNull(outcome.dispatchedUptimeMs)
    }

    @Test fun timeoutIsTerminal() {
        val transaction = click()
        transaction.markDispatched(1_020L)

        val outcome = transaction.timeout(1_500L, "completion_timeout")

        assertEquals(LiveInteractionTerminal.TIMED_OUT, outcome.terminal)
        assertEquals("completion_timeout", outcome.reason)
    }

    @Test fun cancellationIsTerminal() {
        val outcome = click().cancel(1_010L, "session_changed")

        assertEquals(LiveInteractionTerminal.CANCELLED, outcome.terminal)
        assertEquals("session_changed", outcome.reason)
    }

    @Test fun firstTerminalOutcomeWinsAgainstLateSignals() {
        val transaction = click()
        transaction.markDispatched(1_020L)
        val first = transaction.complete(1_080L)

        assertSame(first, transaction.timeout(1_500L, "late_timeout"))
        assertSame(first, transaction.cancel(1_600L, "late_cancel"))
        assertSame(first, transaction.failDispatch(1_700L, "late_failure"))
        assertSame(first, transaction.complete(1_800L))
    }

    @Test fun repeatedDispatchDoesNotRewriteOriginalDispatchTime() {
        val transaction = click()
        transaction.markDispatched(1_020L)
        transaction.markDispatched(1_030L)

        val outcome = transaction.complete(1_080L)

        assertEquals(1_020L, outcome.dispatchedUptimeMs)
    }
}
