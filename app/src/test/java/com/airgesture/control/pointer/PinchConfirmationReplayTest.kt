package com.airgesture.control.pointer

import org.junit.Assert.*
import org.junit.Test

class PinchConfirmationReplayTest {
    @Test fun recordedContactSurvivesTwoBriefThresholdExcursionsAndClicksOnce() {
        val click = ClickHysteresisStateMachine()
        val resource = javaClass.getResourceAsStream("/recordings/pinch-20261030-105s.csv")!!
        val clicks = resource.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("timeMs") }
                .mapNotNull { line ->
                    val row = line.split(',')
                    assertEquals("All replayed frames had touch permission", "true", row[3])
                    val time = row[0].toLong()
                    if (click.processFrame(row[1].toFloat(), time, row[2].toFloat())) time else null
                }.toList()
        }
        // Sensor-time 90071576: second actual contact, at recording time 106.117 s.
        assertEquals(listOf(1200L), clicks)
    }

    @Test fun hysteresisBandAloneCannotFinishConfirmationOrReplayConsumedContact() {
        val click = ClickHysteresisStateMachine()
        click.processFrame(.8f, 0)
        assertFalse(click.processFrame(.17f, 33))
        assertFalse(click.processFrame(.20f, 66))
        assertTrue(click.processFrame(.17f, 99))
        for (time in 132L..990L step 33L) assertFalse(click.processFrame(if (time % 2L == 0L) .20f else .16f, time))
        click.processFrame(.33f, 1023)
        assertFalse(click.processFrame(.17f, 1056))
        assertTrue(click.processFrame(.17f, 1122))
    }

    @Test fun oneOldContactCannotAccumulateConfirmationThroughLongBandDwell() {
        val click = ClickHysteresisStateMachine()
        click.processFrame(.8f, 0)
        click.processFrame(.17f, 33)
        for (time in 66L..330L step 33L) assertFalse(click.processFrame(.20f, time))
        assertFalse("Expired contact must start a new confirmation", click.processFrame(.17f, 363))
        assertTrue(click.processFrame(.17f, 429))
    }

    @Test fun depthVetoAndInvalidEvidenceDiscardPendingTolerance() {
        for (depth in listOf(.4f, Float.NaN)) {
            val click = ClickHysteresisStateMachine()
            click.processFrame(.8f, 0)
            click.processFrame(.17f, 33)
            assertFalse(click.processFrame(.20f, 66, depth))
            assertFalse(click.processFrame(.17f, 99))
        }
    }

    @Test fun resetRevokesBothPendingAndConsumedContactUntilARealOpening() {
        for (consume in listOf(false,true)) {
            val click = ClickHysteresisStateMachine()
            click.processFrame(.8f,0)
            click.processFrame(.17f,33)
            if (consume) assertTrue(click.processFrame(.17f,99))
            click.reset()
            for (time in 132L..330L step 33L) assertFalse(click.processFrame(.17f,time))
            click.processFrame(.33f,363)
            assertFalse(click.processFrame(.17f,396))
            assertTrue(click.processFrame(.17f,462))
        }
    }
}
