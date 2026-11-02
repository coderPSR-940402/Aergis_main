package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class DevLoopReplayTest {
    @Test fun successKeepsFrozenTargetAndNeverPromotesMissedClick() {
        val result = DevLoopReplay.replay(listOf(
            DevLoopReplayRecord("open", 1000, 42, "TAP", 321f, 654f),
            DevLoopReplayRecord("outcome", 1100, 42, outcome = ActionDispatchOutcome.COMPLETED),
            DevLoopReplayRecord("tick", 4000)))
        assertEquals(listOf(LiveInteractionTerminal.SUCCESS), result.outcomes.map { it.terminal })
        assertEquals(321f, result.outcomes.single().frozenTargetX)
        assertTrue(result.events.isEmpty())
    }

    @Test fun capturedTimeoutReplaysExactlyOnceDespiteDuplicateLateSignals() {
        val result = DevLoopReplay.replay(listOf(
            DevLoopReplayRecord("open", 1000, 43, "TAP", 123f, 456f),
            DevLoopReplayRecord("tick", 3500),
            DevLoopReplayRecord("outcome", 3600, 43, outcome = ActionDispatchOutcome.COMPLETED),
            DevLoopReplayRecord("tick", 4000)))
        assertEquals(listOf(LiveInteractionTerminal.TIMED_OUT), result.outcomes.map { it.terminal })
        assertEquals(listOf(LiveDiagnosticKind.MISSED_CLICK), result.events.map { it.trigger.kind })
        assertEquals(43L, result.events.single().trigger.requestId)
    }
}
