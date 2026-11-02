package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDiagnosticBufferTest {
    private fun sample(at: Long) = LiveDiagnosticSample(uptimeMs = at, wallClockMs = at)

    @Test fun snapshotsExactlyThirtySecondsBeforeEvent() {
        val buffer = LiveDiagnosticBuffer(maxSamples = 100, retentionMs = 60_000L)
        listOf(69_999L, 70_000L, 85_000L, 100_000L, 100_001L).forEach { buffer.add(sample(it)) }

        assertEquals(listOf(70_000L, 85_000L, 100_000L),
            buffer.snapshotBefore(100_000L, 30_000L).map { it.uptimeMs })
    }

    @Test fun snapshotsExactlyTenSecondsAfterEventWithoutRepeatingTriggerSample() {
        val buffer = LiveDiagnosticBuffer(maxSamples = 100, retentionMs = 60_000L)
        listOf(99_999L, 100_000L, 100_001L, 105_000L, 110_000L, 110_001L).forEach { buffer.add(sample(it)) }

        assertEquals(listOf(100_001L, 105_000L, 110_000L),
            buffer.snapshotAfter(100_000L, 10_000L).map { it.uptimeMs })
    }

    @Test fun evictsOldestSamplesByCount() {
        val buffer = LiveDiagnosticBuffer(maxSamples = 3, retentionMs = 60_000L)
        (1L..5L).forEach { buffer.add(sample(it)) }

        assertEquals(listOf(3L, 4L, 5L), buffer.all().map { it.uptimeMs })
    }

    @Test fun evictsSamplesOutsideRetentionWindow() {
        val buffer = LiveDiagnosticBuffer(maxSamples = 100, retentionMs = 40_000L)
        buffer.add(sample(1_000L))
        buffer.add(sample(40_999L))
        buffer.add(sample(41_000L))
        buffer.add(sample(41_001L))

        assertEquals(listOf(40_999L, 41_000L, 41_001L), buffer.all().map { it.uptimeMs })
    }

    @Test fun preservesMonotonicOrderingAndRejectsTimeTravel() {
        val buffer = LiveDiagnosticBuffer(maxSamples = 10, retentionMs = 60_000L)
        buffer.add(sample(100L))
        buffer.add(sample(101L))
        try {
            buffer.add(sample(99L))
            throw AssertionError("Expected out-of-order sample rejection")
        } catch (_: IllegalArgumentException) {
            assertEquals(listOf(100L, 101L), buffer.all().map { it.uptimeMs })
        }
    }

    @Test fun emptyBufferSnapshotsAreEmpty() {
        val buffer = LiveDiagnosticBuffer(maxSamples = 10, retentionMs = 60_000L)
        assertTrue(buffer.snapshotBefore(100L, 30_000L).isEmpty())
        assertTrue(buffer.snapshotAfter(100L, 10_000L).isEmpty())
        assertTrue(buffer.all().isEmpty())
    }
}
