package com.airgesture.control

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DevLoopCapturedReplayTest {
    @Test fun replayExportedSessionThroughProductionCorrelator() {
        val input = System.getenv("AERGIS_DEVLOOP_REPLAY_FIXTURE")?.let { File(it).readText() }
            ?: javaClass.getResource("/devloop/synthetic-contract.json")!!.readText()
        val fixture = JSONObject(input)
        val records = fixture.getJSONArray("records")
        val steps = (0 until records.length()).map { i ->
            val r = records.getJSONObject(i)
            DevLoopReplayRecord(r.optString("step", "event"), r.optLong("uptime_ms", r.optLong("event_uptime_ms")),
                r.optLong("request_id"), r.optString("action", "TAP"),
                r.optDouble("target_x", Double.NaN).toFloat(), r.optDouble("target_y", Double.NaN).toFloat(),
                runCatching { ActionDispatchOutcome.valueOf(r.optString("outcome")) }.getOrNull(),
                r.optLong("session_epoch"), r.optLong("input_epoch"), r.optString("reason_code", ""))
        }
        val result = DevLoopReplay.replay(steps)
        val expected = fixture.getJSONObject("expected")
        fun strings(key: String) = expected.getJSONArray(key).let { a -> (0 until a.length()).map(a::getString) }
        assertEquals(strings("terminals"), result.outcomes.map { it.terminal.name })
        assertEquals(strings("event_kinds"), result.events.map { it.trigger.kind.name })
    }
}
