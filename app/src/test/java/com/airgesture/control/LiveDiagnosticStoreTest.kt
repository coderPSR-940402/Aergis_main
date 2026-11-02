package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LiveDiagnosticStoreTest {
    private fun snapshot(requestId: Long, uptimeMs: Long): LiveDiagnosticSnapshot =
        LiveDiagnosticSnapshot(
            trigger = LiveDiagnosticTrigger(
                kind = LiveDiagnosticKind.MISSED_CLICK,
                severity = LiveDiagnosticSeverity.ERROR,
                uptimeMs = uptimeMs,
                wallClockMs = uptimeMs + 10_000L,
                requestId = requestId,
                action = "TAP",
                reasonCode = "completion_timeout",
                frozenTargetX = 100f + requestId,
                frozenTargetY = 200f + requestId,
                sessionEpoch = 7L,
                inputEpoch = 11L
            ),
            before = listOf(
                LiveDiagnosticSample(
                    uptimeMs = uptimeMs - 10L,
                    wallClockMs = uptimeMs + 9_990L,
                    requestId = requestId,
                    gesture = "PINCH"
                )
            ),
            after = listOf(
                LiveDiagnosticSample(
                    uptimeMs = uptimeMs + 10L,
                    wallClockMs = uptimeMs + 10_010L,
                    requestId = requestId,
                    completionState = "TIMEOUT"
                )
            )
        )

    @Test fun publishWritesLatestAndBoundedEventHistory() {
        val root = Files.createTempDirectory("aergis-live-store").toFile()
        val store = LiveDiagnosticStore(rootDir = root, maxEvents = 2)

        store.publish(snapshot(1L, 1_000L))
        store.publish(snapshot(2L, 2_000L))
        store.publish(snapshot(3L, 3_000L))

        val latest = root.resolve("latest.json")
        assertTrue(latest.isFile)
        assertTrue(latest.readText().contains("\"request_id\":3"))

        val events = root.resolve("events").listFiles { file -> file.extension == "json" }!!.sortedBy { it.name }
        assertEquals(2, events.size)
        assertFalse(events.any { it.readText().contains("\"request_id\":1") })
        assertTrue(events.any { it.readText().contains("\"request_id\":2") })
        assertTrue(events.any { it.readText().contains("\"request_id\":3") })
    }

    @Test fun startSessionReportsUncleanPriorSessionAndCleanStopClearsMarker() {
        val root = Files.createTempDirectory("aergis-live-session").toFile()
        val first = LiveDiagnosticStore(rootDir = root, maxEvents = 2)

        assertFalse(first.startSession("build-a", 100L))

        val restarted = LiveDiagnosticStore(rootDir = root, maxEvents = 2)
        assertTrue(restarted.startSession("build-b", 200L))
        restarted.stopSession(clean = true, stoppedWallClockMs = 300L)

        val next = LiveDiagnosticStore(rootDir = root, maxEvents = 2)
        assertFalse(next.startSession("build-c", 400L))
    }

    @Test fun cleanStopWritesSessionManifest() {
        val root = Files.createTempDirectory("aergis-live-manifest").toFile()
        val store = LiveDiagnosticStore(rootDir = root, maxEvents = 2)

        store.startSession("sha-123", 1_000L)
        store.publish(snapshot(9L, 1_100L))
        store.stopSession(clean = true, stoppedWallClockMs = 1_200L)

        val manifest = root.resolve("session.json")
        assertTrue(manifest.isFile)
        val text = manifest.readText()
        assertTrue(text.contains("\"build_id\":\"sha-123\""))
        assertTrue(text.contains("\"clean\":true"))
        assertTrue(text.contains("\"event_count\":1"))
    }
}
