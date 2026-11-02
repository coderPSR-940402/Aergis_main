package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DevLoopWorkerTest {
    @Test fun productionWorkerDrainsOutcomesBeforeFinalizationDespiteRelayFailure() {
        val root = Files.createTempDirectory("devloop-worker").toFile()
        val worker = DevLoopWorker(root, "sha:1374", { 1000L }, { 100_000L }, { error("relay unavailable") })
        worker.start()
        worker.observe { open(1, 1000, "TAP", 321f, 654f, 7, 8) }
        worker.observe { outcome(ActionDispatchOutcome.COMPLETED, 1, 1100) }
        worker.stop().get(5, TimeUnit.SECONDS)
        val manifest = root.resolve("session.json").readText()
        assertTrue(manifest.contains("\"clean\":true"))
        assertTrue(manifest.contains("\"click_success\":1"))
    }
}
