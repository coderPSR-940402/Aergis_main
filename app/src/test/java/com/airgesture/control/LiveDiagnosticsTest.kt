package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDiagnosticsTest {
    private class RecordingSink : LiveDiagnosticEventSink {
        val snapshots = mutableListOf<LiveDiagnosticSnapshot>()
        override fun publish(snapshot: LiveDiagnosticSnapshot) {
            snapshots += snapshot
        }
    }

    private fun sample(at: Long, gesture: String? = null) = LiveDiagnosticSample(
        uptimeMs = at,
        wallClockMs = at,
        gesture = gesture
    )

    private fun diagnostics(
        sink: LiveDiagnosticEventSink,
        preWindowMs: Long = 30_000L,
        postWindowMs: Long = 0L,
        interactionTimeoutMs: Long = 500L,
        dedupWindowMs: Long = 2_000L
    ) = LiveDiagnostics(
        buffer = LiveDiagnosticBuffer(maxSamples = 200, retentionMs = 60_000L),
        sink = sink,
        preWindowMs = preWindowMs,
        postWindowMs = postWindowMs,
        interactionTimeoutMs = interactionTimeoutMs,
        dedupWindowMs = dedupWindowMs
    )

    @Test fun successfulTapSuppressesMissedClick() {
        val sink = RecordingSink()
        val diagnostics = diagnostics(sink)
        diagnostics.sample(sample(1_000L))
        diagnostics.openInteraction(
            requestId = 41L,
            openedUptimeMs = 1_000L,
            action = "TAP",
            frozenTargetX = 320f,
            frozenTargetY = 640f,
            sessionEpoch = 7L,
            inputEpoch = 11L
        )
        diagnostics.recordDispatch(41L, 1_020L)
        diagnostics.recordOutcome(41L, LiveInteractionTerminal.SUCCESS, 1_080L)

        diagnostics.sample(sample(2_000L))

        assertTrue(sink.snapshots.isEmpty())
    }

    @Test fun confirmedTapTimeoutPromotesOneMissedClick() {
        val sink = RecordingSink()
        val diagnostics = diagnostics(sink)
        diagnostics.sample(sample(1_000L))
        diagnostics.openInteraction(
            requestId = 42L,
            openedUptimeMs = 1_000L,
            action = "TAP",
            frozenTargetX = 321f,
            frozenTargetY = 641f,
            sessionEpoch = 7L,
            inputEpoch = 11L
        )
        diagnostics.recordDispatch(42L, 1_020L)

        diagnostics.sample(sample(1_499L))
        diagnostics.sample(sample(1_500L))
        diagnostics.sample(sample(2_000L))

        assertEquals(1, sink.snapshots.size)
        val event = sink.snapshots.single().trigger
        assertEquals(LiveDiagnosticKind.MISSED_CLICK, event.kind)
        assertEquals(42L, event.requestId)
        assertEquals("TAP", event.action)
        assertEquals("completion_timeout", event.reasonCode)
    }

    @Test fun failedDispatchPromotesOneActionFailure() {
        val sink = RecordingSink()
        val diagnostics = diagnostics(sink)
        diagnostics.openInteraction(
            requestId = 43L,
            openedUptimeMs = 1_000L,
            action = "SCROLL_DOWN",
            frozenTargetX = Float.NaN,
            frozenTargetY = Float.NaN,
            sessionEpoch = 7L,
            inputEpoch = 11L
        )

        diagnostics.recordOutcome(
            requestId = 43L,
            terminal = LiveInteractionTerminal.FAILED,
            uptimeMs = 1_015L,
            reasonCode = "gesture_rejected"
        )
        diagnostics.recordOutcome(
            requestId = 43L,
            terminal = LiveInteractionTerminal.FAILED,
            uptimeMs = 1_020L,
            reasonCode = "duplicate_signal"
        )

        assertEquals(1, sink.snapshots.size)
        assertEquals(LiveDiagnosticKind.ACTION_FAILED, sink.snapshots.single().trigger.kind)
        assertEquals("gesture_rejected", sink.snapshots.single().trigger.reasonCode)
    }

    @Test fun promotedEventContainsExactPreAndPostWindows() {
        val sink = RecordingSink()
        val diagnostics = diagnostics(
            sink = sink,
            preWindowMs = 30_000L,
            postWindowMs = 10_000L
        )
        listOf(69_999L, 70_000L, 85_000L, 100_000L).forEach { diagnostics.sample(sample(it)) }

        diagnostics.trigger(
            LiveDiagnosticTrigger(
                kind = LiveDiagnosticKind.CAMERA_DELAY,
                severity = LiveDiagnosticSeverity.WARNING,
                uptimeMs = 100_000L,
                wallClockMs = 100_000L,
                reasonCode = "frame_age_threshold"
            )
        )
        listOf(100_001L, 105_000L, 110_000L).forEach { diagnostics.sample(sample(it)) }

        assertEquals(1, sink.snapshots.size)
        val snapshot = sink.snapshots.single()
        assertEquals(listOf(70_000L, 85_000L, 100_000L), snapshot.before.map { it.uptimeMs })
        assertEquals(listOf(100_001L, 105_000L, 110_000L), snapshot.after.map { it.uptimeMs })
    }

    @Test fun duplicateRequestTriggerIsPromotedExactlyOnce() {
        val sink = RecordingSink()
        val diagnostics = diagnostics(sink)
        val trigger = LiveDiagnosticTrigger(
            kind = LiveDiagnosticKind.ACTION_FAILED,
            severity = LiveDiagnosticSeverity.ERROR,
            uptimeMs = 1_000L,
            wallClockMs = 1_000L,
            requestId = 77L,
            action = "BACK",
            reasonCode = "dispatch_rejected"
        )

        diagnostics.trigger(trigger)
        diagnostics.trigger(trigger.copy(uptimeMs = 10_000L, wallClockMs = 10_000L))

        assertEquals(1, sink.snapshots.size)
    }

    @Test fun sinkFailureNeverEscapesObservationBoundary() {
        val diagnostics = diagnostics(
            sink = LiveDiagnosticEventSink { throw IllegalStateException("disk unavailable") }
        )

        diagnostics.trigger(
            LiveDiagnosticTrigger(
                kind = LiveDiagnosticKind.PROCESS_RESTART,
                severity = LiveDiagnosticSeverity.ERROR,
                uptimeMs = 1_000L,
                wallClockMs = 1_000L,
                reasonCode = "unclean_previous_session"
            )
        )
    }

    @Test fun relayIsCompactAndDoesNotIncludeRawTimelineStrings() {
        val privateText = "PRIVATE SCREEN CONTENT THAT MUST NOT LEAVE THE DEVICE"
        val snapshot = LiveDiagnosticSnapshot(
            trigger = LiveDiagnosticTrigger(
                kind = LiveDiagnosticKind.MISSED_CLICK,
                severity = LiveDiagnosticSeverity.ERROR,
                uptimeMs = 1_000L,
                wallClockMs = 2_000L,
                requestId = 42L,
                action = "TAP",
                reasonCode = "completion_timeout"
            ),
            before = listOf(sample(900L, gesture = privateText)),
            after = listOf(sample(1_100L, gesture = privateText))
        )

        val line = LiveDiagnosticRelay(maxLineChars = 512).serialize(snapshot)

        assertTrue(line.startsWith("AERGISTEL|"))
        assertTrue(line.length <= 512)
        assertFalse(line.contains(privateText))
        assertTrue(line.contains("MISSED_CLICK"))
        assertTrue(line.contains("\"request_id\":42"))
    }
}
