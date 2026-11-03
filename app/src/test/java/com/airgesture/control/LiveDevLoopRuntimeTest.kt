package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LiveDevLoopRuntimeTest {
    @Test fun finalManifestIsDurableBeforeBulkFrameTransport() {
        val root = Files.createTempDirectory("devloop-final-first").toFile()
        var stopping = false
        val lines = mutableListOf<String>()
        val durableBeforeTransport = mutableListOf<Boolean>()
        val session = DevLoopSession(root, "sha", "id", { line ->
            if (stopping && line.contains("\"step\":\"sample\"")) {
                durableBeforeTransport.add(root.resolve("session.json").readText().contains("\"clean\":true") &&
                    lines.any { it.contains("\"state\":\"stopped\"") })
            }
            lines.add(line)
        })
        session.start(1000, 100_000)
        session.sample(LiveDiagnosticSample(1100, 100_100, frameId = 99))
        stopping = true
        session.stop(1200, 100_200)
        assertTrue("Lost logcat must not lose finalization", durableBeforeTransport.isNotEmpty() && durableBeforeTransport.all { it })
    }

    @Test fun periodicManifestCarriesTheActualObservationHeartbeat() {
        val root = Files.createTempDirectory("devloop-heartbeat").toFile()
        val session = DevLoopSession(root, "sha", "id", {})
        session.start(1000, 100_000)
        session.tick(6000, 105_000)
        assertTrue(root.resolve("session.json").readText().contains("\"observed_wall_clock_ms\":105000"))
    }

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
        assertEquals(100_010L, events.single().trigger.wallClockMs)
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

    @Test fun heldDragHasNoInventedCompletionDeadline() {
        val root = Files.createTempDirectory("devloop-drag").toFile()
        val session = DevLoopSession(root, "sha", "id", {})
        session.start(1000, 100_000)
        session.open(1, 1000, "POINTER_DRAG", 321f, 654f, 1, 2)
        session.tick(20_000, 119_000)
        session.outcome(ActionDispatchOutcome.COMPLETED, 1, 20_100)
        session.stop(20_200, 119_200)
        assertFalse(root.resolve("latest.json").exists())
        assertTrue(root.resolve("trace.json").readText().contains("SUCCESS"))
    }

    @Test fun duplicateOpenDoesNotInflateTheSessionClickDenominator() {
        val root = Files.createTempDirectory("devloop-duplicate").toFile()
        val session = DevLoopSession(root, "sha", "id", {})
        session.start(1000, 100_000)
        session.open(1, 1000, "TAP", 321f, 654f, 1, 2)
        session.outcome(ActionDispatchOutcome.COMPLETED, 1, 1100)
        session.open(1, 1200, "TAP", 321f, 654f, 1, 2)
        session.stop(1300, 100_300)
        assertTrue(root.resolve("session.json").readText().contains("\"click_attempts\":1"))
    }

    @Test fun semanticScrollAndRotationSubmissionCannotManufactureCompletionFailures() {
        for (action in listOf("SCROLL_UP", "SCROLL_DOWN", "ROTATION_TOGGLE")) {
            val root = Files.createTempDirectory("devloop-submission").toFile()
            val session = DevLoopSession(root, "sha", "id", {})
            session.start(1000, 100_000)
            session.open(1, 1000, action, Float.NaN, Float.NaN, 1, 2)
            session.outcome(ActionDispatchOutcome.SUBMITTED, 1, 1100)
            session.tick(20_000, 119_000)
            session.stop(20_100, 119_100)
            assertFalse(action, root.resolve("latest.json").exists())
        }
    }

    @Test fun promotedEventTransportsItsFrameContextBeforeTheProcessCanStop() {
        val root = Files.createTempDirectory("devloop-context").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "sha", "id", lines::add)
        session.start(1000, 100_000)
        session.sample(LiveDiagnosticSample(1100, 100_100, frameId = 99, cameraAgeMs = 42))
        session.open(1, 1200, "TAP", 321f, 654f, 1, 2)
        session.outcome(ActionDispatchOutcome.PLATFORM_REJECTED, 1, 1250)
        session.tick(11_250, 110_250)
        assertTrue(lines.any { it.contains("\"event_kind\":\"ACTION_FAILED\"") })
        assertTrue(lines.any { it.contains("\"frame_id\":99") && it.contains("\"camera_age_ms\":42") })
    }

    @Test fun recoveredUncleanManifestIsTransportedWithItsOriginalIdentity() {
        val root = Files.createTempDirectory("devloop-unclean-transport").toFile()
        DevLoopSession(root, "old-sha", "old", {}).start(1000, 100_000)
        val lines = mutableListOf<String>()
        DevLoopSession(root, "new-sha", "new", lines::add).start(2000, 101_000)
        assertTrue(lines.any { it.contains("\"session_id\":\"old\"") &&
            it.contains("\"build_id\":\"old-sha\"") && it.contains("\"state\":\"unclean\"") })
    }

    @Test fun frameConditionsProduceObservationsAndRecoveryMetricsWithoutChangingInput() {
        val root = Files.createTempDirectory("devloop-detectors").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "sha", "id", lines::add)
        session.start(1000, 100_000)
        session.sample(LiveDiagnosticSample(1100, 100_100, handId = "owner", pointerX = .1f,
            pointerY = .2f, contactState = "PINCHING", cameraAgeMs = 20, inferenceMs = 10))
        session.sample(LiveDiagnosticSample(1200, 100_200, handId = "owner", pointerX = .4f,
            pointerY = .2f, contactState = "PINCHING", cameraAgeMs = 700, inferenceMs = 300))
        session.sample(LiveDiagnosticSample(1300, 100_300))
        session.sample(LiveDiagnosticSample(1500, 100_500, handId = "owner"), .95f, .5f, true)
        session.stop(1600, 100_600)
        for (kind in listOf("HAND_LOST", "TRACKING_JUMP", "CAMERA_DELAY", "INFERENCE_STALL"))
            assertTrue(kind, lines.any { it.contains("\"event_kind\":\"$kind\"") })
        val manifest = root.resolve("session.json").readText()
        assertTrue(manifest.contains("\"hand_loss_recovery_p95_ms\":200"))
        assertTrue(manifest.contains("\"edge_tracked\":1"))
    }

    @Test fun expiredSelectionConfirmationHasItsOwnEvidenceKind() {
        val events = mutableListOf<LiveDiagnosticSnapshot>()
        val live = LiveDiagnostics(LiveDiagnosticBuffer(100, 60_000), LiveDiagnosticEventSink(events::add), postWindowMs = 0)
        live.openInteraction(1, 1000, "SELECT_ITEM", Float.NaN, Float.NaN, 1, 2)
        live.recordOutcome(1, LiveInteractionTerminal.TIMED_OUT, 2001, "activation_unconfirmed")
        assertEquals(LiveDiagnosticKind.SELECTION_FAILED, events.single().trigger.kind)
    }
}
