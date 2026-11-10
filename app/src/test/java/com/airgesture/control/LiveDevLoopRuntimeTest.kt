package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LiveDevLoopRuntimeTest {
    @Test fun replayRetainsBothRawInputAndDisplayedPointerForTheSameFrame() {
        val root = Files.createTempDirectory("devloop-raw-filtered").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "synthetic", "motion", lines::add)
        session.start(1000, 100_000)
        session.sample(LiveDiagnosticSample(1100, 100_100, frameId = 99, handId = "owner",
            pointerX = .6f, pointerY = .7f), rawX = .3f, rawY = .4f, tracking = true)
        session.stop(1200, 100_200)
        val row = lines.single { it.contains("\"step\":\"sample\"") }
        assertTrue(row.contains("\"raw_x\":0.3"))
        assertTrue(row.contains("\"raw_y\":0.4"))
        assertTrue(row.contains("\"pointer_x\":0.6"))
        assertTrue(row.contains("\"frame_id\":99"))
    }

    @Test fun nearContactGeometrySurvivesWithoutCrossingTheRecognitionThreshold() {
        val root = Files.createTempDirectory("devloop-near-contact").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "synthetic", "contact", lines::add)
        session.start(1000, 100_000)
        session.contactGeometry(10000, 1100, "owner", 7, 8, .8f, .1f, "before_recognition_gates")
        session.contactGeometry(10033, 1133, "owner", 7, 8, .24f, .1f, "before_recognition_gates")
        session.contactGeometry(10066, 1166, "owner", 7, 8, .8f, .1f, "before_recognition_gates")
        session.stop(1200, 100_200)
        assertTrue("The same recognition threshold must not hide its own near misses",
            lines.any { it.contains("\"contact_distance\":0.24") })
        assertTrue(root.resolve("session.json").readText().contains("\"contact_candidates\":0"))
        assertTrue(root.resolve("session.json").readText().contains("\"missed_clicks\":0"))
    }

    @Test fun contactLinksRetainTheAuthoritativeRequestAndFrozenTarget() {
        val root = Files.createTempDirectory("devloop-contact-link").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "synthetic", "contact", lines::add)
        session.start(1000, 100_000)
        session.contactGeometry(10000, 1100, "owner", 7, 8, .12f, .1f, "before_recognition_gates")
        session.contactDecision(10000, 1100, "recognized_for_dispatch")
        session.open(42, 1110, "TAP", 321f, 654f, 7, 8)
        session.outcome(ActionDispatchOutcome.COMPLETED, 42, 1120)
        session.contactGeometry(10033, 1133, "owner", 7, 8, .8f, .1f, "before_recognition_gates")
        session.stop(1200, 100_200)
        val open = lines.single { it.contains("\"step\":\"open\"") }
        assertTrue(open.contains("\"request_id\":42"))
        assertTrue(open.contains("\"contact_id\":1"))
        assertTrue(open.contains("\"target_x\":321.0"))
        assertTrue(open.contains("\"target_y\":654.0"))
        assertTrue(root.resolve("session.json").readText().contains("\"contacts_linked\":1"))
    }

    @Test fun selectionPinchLinksToItsContactWithoutInflatingTapCounters() {
        val root = Files.createTempDirectory("devloop-selection-contact").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "synthetic", "selection", lines::add)
        session.start(1000, 100_000)
        session.contactGeometry(10000, 1100, "owner", 7, 8, .12f, .1f, "before_recognition_gates")
        session.contactDecision(10000, 1100, "recognized_for_selection_dispatch")
        session.open(42, 1110, "SELECT_ITEM", Float.NaN, Float.NaN, 7, 8)
        session.outcome(ActionDispatchOutcome.SUBMITTED, 42, 1115)
        session.outcome(ActionDispatchOutcome.COMPLETED, 42, 1120)
        session.contactGeometry(10033, 1133, "owner", 7, 8, .8f, .1f, "before_recognition_gates")
        session.stop(1200, 100_200)
        val open = lines.single { it.contains("\"step\":\"open\"") }
        assertTrue(open.contains("\"request_id\":42"))
        assertTrue("Recognized selection must explain the same contact", open.contains("\"contact_id\":1"))
        assertTrue("Selection has no pointer target", open.contains("\"target_x\":null"))
        assertTrue(open.contains("\"target_y\":null"))
        assertTrue(lines.single { it.contains("\"step\":\"contact_end\"") }
            .contains("\"outcome\":\"linked_to_request\""))
        val manifest = root.resolve("session.json").readText()
        for ((key, expected) in mapOf("contacts_linked" to 1, "contacts_without_action" to 0,
                "click_attempts" to 0, "click_success" to 0, "missed_clicks" to 0, "action_failures" to 0))
            assertTrue(key, manifest.contains("\"$key\":$expected"))
    }

    @Test fun contactRecognitionCannotLinkADifferentAction() {
        for ((gate, action) in listOf(
                "recognized_for_dispatch" to "SELECT_ITEM",
                "recognized_for_selection_dispatch" to "TAP",
                "recognized_for_selection_dispatch" to "HOME",
                "selection_touch_gate_blocked" to "SELECT_ITEM")) {
            val root = Files.createTempDirectory("devloop-contact-action").toFile()
            val lines = mutableListOf<String>()
            val session = DevLoopSession(root, "synthetic", "contact", lines::add)
            session.start(1000, 100_000)
            session.contactGeometry(10000, 1100, "owner", 7, 8, .12f, .1f, "before_recognition_gates")
            session.contactDecision(10000, 1100, gate)
            session.open(42, 1110, action, Float.NaN, Float.NaN, 7, 8)
            session.contactGeometry(10033, 1133, "owner", 7, 8, .8f, .1f, "before_recognition_gates")
            session.stop(1200, 100_200)
            assertFalse("$gate cannot explain $action",
                lines.single { it.contains("\"step\":\"open\"") }.contains("\"contact_id\""))
            assertTrue(root.resolve("session.json").readText().contains("\"contacts_without_action\":1"))
        }
    }

    @Test fun selectionContactLinksRequireFreshSameFrameAndEpochEvidence() {
        // Expired observation, different session/input epoch, or a new frame without recognition.
        for (scenario in 0..3) {
            val root = Files.createTempDirectory("devloop-selection-authority").toFile()
            val lines = mutableListOf<String>()
            val session = DevLoopSession(root, "synthetic", "contact", lines::add)
            session.start(1000, 100_000)
            session.contactGeometry(10000, 1100, "owner", 7, 8, .12f, .1f, "before_recognition_gates")
            session.contactDecision(10000, 1100, "recognized_for_selection_dispatch")
            if (scenario == 3)
                session.contactGeometry(10016, 1116, "owner", 7, 8, .12f, .1f, "before_recognition_gates")
            session.open(42, if (scenario == 0) 1451 else 1120, "SELECT_ITEM", Float.NaN, Float.NaN,
                if (scenario == 1) 9 else 7, if (scenario == 2) 9 else 8)
            assertFalse("Invalid selection correlation scenario $scenario",
                lines.single { it.contains("\"step\":\"open\"") }.contains("\"contact_id\""))
            session.stop(1500, 100_500)
        }
    }

    @Test fun failedSelectionStillLinksItsRequestWithoutBecomingAMissedTap() {
        val root = Files.createTempDirectory("devloop-selection-failed").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "synthetic", "selection", lines::add)
        session.start(1000, 100_000)
        session.contactGeometry(10000, 1100, "owner", 7, 8, .12f, .1f, "before_recognition_gates")
        session.contactDecision(10000, 1100, "recognized_for_selection_dispatch")
        session.open(42, 1110, "SELECT_ITEM", Float.NaN, Float.NaN, 7, 8)
        session.selectionUnconfirmed(42, 1200, "no_matching_click_event")
        session.contactGeometry(10133, 1233, "owner", 7, 8, .8f, .1f, "before_recognition_gates")
        session.stop(1300, 100_300)
        val manifest = root.resolve("session.json").readText()
        assertTrue(manifest.contains("\"contacts_linked\":1"))
        assertTrue(manifest.contains("\"contacts_without_action\":0"))
        assertTrue(manifest.contains("\"action_failures\":1"))
        assertTrue(manifest.contains("\"missed_clicks\":0"))
        assertTrue(lines.any { it.contains("\"event_kind\":\"SELECTION_FAILED\"") })
    }

    @Test fun laterTapCannotBeAttributedToAStaleContactEpisode() {
        val root = Files.createTempDirectory("devloop-stale-contact").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "synthetic", "contact", lines::add)
        session.start(1000, 100_000)
        session.contactGeometry(10000, 1100, "owner", 7, 8, .12f, .1f, "clicking_disabled")
        session.contactDecision(10000, 1100, "recognized_for_dispatch")
        session.open(43, 5000, "TAP", 321f, 654f, 7, 8)
        val open = lines.single { it.contains("\"step\":\"open\"") }
        assertFalse("A later action cannot explain an old ignored contact", open.contains("\"contact_id\""))
        session.stop(5100, 104_100)
    }

    @Test fun nearbyTapWithoutRecognitionEvidenceCannotExplainAContact() {
        val root = Files.createTempDirectory("devloop-unrelated-tap").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "synthetic", "contact", lines::add)
        session.start(1000, 100_000)
        session.contactGeometry(10000, 1100, "owner", 7, 8, .12f, .1f, "clicking_disabled")
        session.open(44, 1110, "TAP", 321f, 654f, 7, 8)
        assertFalse(lines.single { it.contains("\"step\":\"open\"") }.contains("\"contact_id\""))
        session.stop(1200, 100_200)
    }

    @Test fun stableContactEvidenceIsSampledWithoutDuplicateDecisionFlooding() {
        val root = Files.createTempDirectory("devloop-contact-bound").toFile()
        val lines = mutableListOf<String>()
        val session = DevLoopSession(root, "synthetic", "contact", lines::add)
        session.start(1000, 100_000)
        repeat(100) { i ->
            session.contactGeometry(10000L + i * 16, 1100L + i * 16, "owner", 7, 8,
                .12f, .1f, "before_recognition_gates")
            session.contactDecision(10000L + i * 16, 1100L + i * 16, "awaiting_contact_confirmation_or_release")
        }
        session.stop(3000, 102_000)
        assertTrue("A stable episode must retain bounded samples, not two records per input frame", lines.size < 24)
        assertEquals(1, lines.count { it.contains("\"step\":\"contact_decision\"") })
    }

    @Test fun smallPointerReversalsAreMeasuredOutsidePinchWithoutInventingFailures() {
        val root = Files.createTempDirectory("devloop-pointer-reversals").toFile()
        val session = DevLoopSession(root, "synthetic", "motion", {})
        session.start(1000, 100_000)
        listOf(.5f, .52f, .5f, .52f).forEachIndexed { i, x ->
            session.sample(LiveDiagnosticSample(1100L + i * 33, 100_100L + i * 33,
                handId = "owner", sessionEpoch = 1, inputEpoch = 2,
                pointerX = x, pointerY = .5f, contactState = "AIMING", cameraAgeMs = 180))
        }
        session.stop(1300, 100_300)
        val manifest = root.resolve("session.json").readText()
        assertTrue("Small rapid reversals must not disappear below the pinch jump threshold",
            manifest.contains("\"pointer_reversals\":2"))
        assertTrue(manifest.contains("\"slow_frame_count\":4"))
        assertTrue(manifest.contains("\"missed_clicks\":0"))
    }

    @Test fun pointerStatisticsDoNotBridgeOwnershipOrEpochChanges() {
        val root = Files.createTempDirectory("devloop-motion-boundary").toFile()
        val session = DevLoopSession(root, "synthetic", "motion", {})
        session.start(1000, 100_000)
        session.sample(LiveDiagnosticSample(1100, 100_100, handId = "one", sessionEpoch = 1,
            pointerX = .1f, pointerY = .5f, contactState = "PINCHING"))
        session.sample(LiveDiagnosticSample(1133, 100_133, handId = "one", sessionEpoch = 2,
            pointerX = .9f, pointerY = .5f, contactState = "PINCHING"))
        session.stop(1200, 100_200)
        assertFalse("A discontinuity across authority epochs is not a pinch movement measurement",
            root.resolve("session.json").readText().contains("\"pinch_jump_max\""))
    }

    @Test fun firstCaptureAfterUpgradePreservesTheLegacyPrivateTrace() {
        val root = Files.createTempDirectory("devloop-legacy-trace").toFile()
        root.resolve("session.json").writeText("""{"schema":1,"session_id":"old","build_id":"sha:1381","seq":10,"state":"recording","clean":false}""")
        root.resolve("session.open").writeText("{}")
        root.resolve("trace.json").writeText("""{"schema":1,"session_id":"old","build_id":"sha:1381","records":[{"request_id":42,"step":"open","seq":1}]}""")
        DevLoopSession(root, "new-sha", "new", {}).start(1000, 100_000)
        assertTrue("Starting the new recorder must not erase pre-upgrade evidence", root.resolve("traces/old.json").isFile)
        assertTrue(root.resolve("traces/old.json").readText().contains("\"request_id\":42"))
    }

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
