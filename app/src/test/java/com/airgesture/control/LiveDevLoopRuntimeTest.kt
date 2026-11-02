package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LiveDevLoopRuntimeTest {
    @Test fun stoppingFlushesPostWindowAndCancelsUnfinishedWorkWithoutInventingMisses() {
        val events = mutableListOf<LiveDiagnosticSnapshot>()
        val live = LiveDiagnostics(LiveDiagnosticBuffer(100, 60_000), LiveDiagnosticEventSink(events::add))
        live.sample(LiveDiagnosticSample(1000, 100_000))
        live.openInteraction(1, 1000, "TAP", 23f, 42f, 1, 2)
        live.recordOutcome(1, LiveInteractionTerminal.FAILED, 1010, "platform_rejected")
        live.openInteraction(2, 1010, "TAP", 23f, 42f, 1, 2)
        live.finish(1020)
        live.sample(LiveDiagnosticSample(20_000, 119_000))
        assertEquals(1, events.size)
        assertEquals(100_010, events.single().trigger.wallClockMs)
        assertEquals(1L, events.single().trigger.requestId)
    }

    @Test fun completedRequestCannotBeReopenedByDuplicateObservation() {
        val events = mutableListOf<LiveDiagnosticSnapshot>()
        val live = LiveDiagnostics(LiveDiagnosticBuffer(100, 60_000), LiveDiagnosticEventSink(events::add), postWindowMs = 0)
        live.openInteraction(3, 1000, "TAP", 23f, 42f, 1, 2)
        live.recordOutcome(3, LiveInteractionTerminal.SUCCESS, 1050)
        live.openInteraction(3, 1060, "TAP", 23f, 42f, 1, 2)
        live.sample(LiveDiagnosticSample(2000, 100_000))
        assertTrue(events.isEmpty())
    }

    @Test fun productionSessionPersistsSuccessTimeoutIdentityAndCleanManifest() {
        val root = Files.createTempDirectory("devloop-runtime").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "source-sha:1374", "session-1", lines::add)
        session.start(1000, 100_000)
        session.open(10, 1000, "TAP", 321f, 654f, 7, 8)
        session.outcome(ActionDispatchOutcome.COMPLETED, 10, 1100)
        session.open(11, 1200, "TAP", 123f, 456f, 7, 8)
        session.tick(4000, 103_000)
        session.stop(4100, 103_100)
        val manifest = root.resolve("session.json").readText()
        assertTrue(manifest.contains("\"clean\":true"))
        assertTrue(manifest.contains("\"click_success\":1"))
        assertTrue(manifest.contains("\"missed_clicks\":1"))
        assertTrue(manifest.contains("session-1"))
        assertTrue(root.resolve("latest.json").readText().contains("\"request_id\":11"))
        assertTrue(lines.any { it.contains("\"record\":\"session\"") })
        assertTrue(lines.any { it.contains("\"record\":\"event\"") && it.contains("\"target_x\":123.0") })
        assertTrue(lines.any { it.contains("\"record\":\"trace\"") && it.contains("\"request_id\":10") })
        assertTrue(lines.all { it.length <= 3500 })
    }

    @Test fun restartPreservesUncleanPriorIdentityAndEmitsRestartEvidence() {
        val root = Files.createTempDirectory("devloop-restart").toFile()
        DevLoopSession(root, "old-sha", "old", {}).start(1000, 100_000)
        val lines = mutableListOf<String>()
        val next = DevLoopSession(root, "new-sha", "new", lines::add)
        next.start(2000, 101_000)
        next.stop(2010, 101_010)
        assertTrue(root.resolve("sessions/old.json").readText().contains("\"clean\":false"))
        assertTrue(lines.any { it.contains("PROCESS_RESTART") })
        assertTrue(root.resolve("session.json").readText().contains("new-sha"))
    }

    @Test fun submittedOnlyCommandsDoNotBecomeFabricatedFailures() {
        val root = Files.createTempDirectory("devloop-submitted").toFile()
        val session = DevLoopSession(root, "sha", "id", {})
        session.start(1000, 100_000)
        session.open(1, 1000, "MEDIA_PLAY_PAUSE", Float.NaN, Float.NaN, 1, 2)
        session.outcome(ActionDispatchOutcome.SUBMITTED, 1, 1100)
        session.tick(10_000, 109_000)
        session.stop(10_100, 109_100)
        assertFalse(root.resolve("latest.json").exists())
        assertTrue(root.resolve("session.json").readText().contains("\"submitted_only\":1"))
    }
}
