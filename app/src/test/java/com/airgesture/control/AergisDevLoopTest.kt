package com.airgesture.control

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AergisDevLoopTest {
    private class RecordingSink : LiveDiagnosticEventSink {
        val snapshots = mutableListOf<LiveDiagnosticSnapshot>()
        override fun publish(snapshot: LiveDiagnosticSnapshot) {
            snapshots += snapshot
        }
    }

    @After fun tearDown() {
        AergisDevLoop.resetForTest()
    }

    private fun install(timeoutMs: Long = 500L): Pair<LiveDiagnostics, RecordingSink> {
        val sink = RecordingSink()
        val diagnostics = LiveDiagnostics(
            buffer = LiveDiagnosticBuffer(maxSamples = 200, retentionMs = 60_000L),
            sink = sink,
            preWindowMs = 30_000L,
            postWindowMs = 0L,
            interactionTimeoutMs = timeoutMs,
            dedupWindowMs = 2_000L
        )
        AergisDevLoop.installForTest(diagnostics)
        return diagnostics to sink
    }

    @Test fun submittedThenCompletedActionProducesNoFailure() {
        val (_, sink) = install()
        AergisDevLoop.openAction(
            requestId = 101L,
            openedUptimeMs = 1_000L,
            action = "TAP",
            frozenTargetX = 420f,
            frozenTargetY = 840f,
            sessionEpoch = 7L,
            inputEpoch = 11L
        )

        AergisDevLoop.recordActionOutcome(ActionDispatchOutcome.SUBMITTED, 101L, 1_020L)
        AergisDevLoop.recordActionOutcome(ActionDispatchOutcome.COMPLETED, 101L, 1_080L)

        assertTrue(sink.snapshots.isEmpty())
    }

    @Test fun rejectedActionPromotesFailureUsingExistingRequestIdentity() {
        val (_, sink) = install()
        AergisDevLoop.openAction(
            requestId = 102L,
            openedUptimeMs = 2_000L,
            action = "BACK",
            frozenTargetX = Float.NaN,
            frozenTargetY = Float.NaN,
            sessionEpoch = 8L,
            inputEpoch = 12L
        )

        AergisDevLoop.recordActionOutcome(ActionDispatchOutcome.PLATFORM_REJECTED, 102L, 2_010L)

        val event = sink.snapshots.single().trigger
        assertEquals(LiveDiagnosticKind.ACTION_FAILED, event.kind)
        assertEquals(102L, event.requestId)
        assertEquals("BACK", event.action)
        assertEquals("platform_rejected", event.reasonCode)
        assertEquals(8L, event.sessionEpoch)
        assertEquals(12L, event.inputEpoch)
    }

    @Test fun submittedTapWithoutCompletionBecomesOneMissedClick() {
        val (diagnostics, sink) = install(timeoutMs = 500L)
        AergisDevLoop.openAction(
            requestId = 103L,
            openedUptimeMs = 3_000L,
            action = "TAP",
            frozenTargetX = 321f,
            frozenTargetY = 654f,
            sessionEpoch = 9L,
            inputEpoch = 13L
        )
        AergisDevLoop.recordActionOutcome(ActionDispatchOutcome.SUBMITTED, 103L, 3_020L)

        diagnostics.sample(LiveDiagnosticSample(3_500L, 33_500L))
        diagnostics.sample(LiveDiagnosticSample(4_000L, 34_000L))

        assertEquals(1, sink.snapshots.size)
        val event = sink.snapshots.single().trigger
        assertEquals(LiveDiagnosticKind.MISSED_CLICK, event.kind)
        assertEquals(103L, event.requestId)
        assertEquals(321f, event.frozenTargetX)
        assertEquals(654f, event.frozenTargetY)
    }

    @Test fun cancelledActionDoesNotBecomeFailure() {
        val (_, sink) = install()
        AergisDevLoop.openAction(
            requestId = 104L,
            openedUptimeMs = 4_000L,
            action = "SELECT_ITEM",
            frozenTargetX = Float.NaN,
            frozenTargetY = Float.NaN,
            sessionEpoch = 10L,
            inputEpoch = 14L
        )

        AergisDevLoop.recordActionOutcome(ActionDispatchOutcome.CANCELLED, 104L, 4_020L)

        assertTrue(sink.snapshots.isEmpty())
    }
}
