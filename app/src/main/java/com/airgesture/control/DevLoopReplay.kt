package com.airgesture.control

internal data class DevLoopReplayRecord(
    val step: String,
    val uptimeMs: Long,
    val requestId: Long = 0,
    val action: String = "TAP",
    val targetX: Float = Float.NaN,
    val targetY: Float = Float.NaN,
    val outcome: ActionDispatchOutcome? = null,
    val sessionEpoch: Long = 0,
    val inputEpoch: Long = 0
)
internal data class DevLoopReplayResult(val outcomes: List<LiveInteractionOutcome>, val events: List<LiveDiagnosticSnapshot>)

/** Replays the production correlator. Recognition replay still needs its original landmark evidence. */
internal object DevLoopReplay {
    fun replay(records: List<DevLoopReplayRecord>): DevLoopReplayResult {
        val outcomes = mutableListOf<LiveInteractionOutcome>()
        val events = mutableListOf<LiveDiagnosticSnapshot>()
        val live = LiveDiagnostics(LiveDiagnosticBuffer(3600, 40_000), LiveDiagnosticEventSink(events::add),
            postWindowMs = 0, interactionTimeoutMs = 2500, onTerminal = outcomes::add)
        for (r in records.sortedBy { it.uptimeMs }) when (r.step) {
            "open" -> live.openInteraction(r.requestId, r.uptimeMs, r.action, r.targetX, r.targetY, r.sessionEpoch, r.inputEpoch)
            "outcome" -> when (r.outcome) {
                ActionDispatchOutcome.SUBMITTED -> live.recordDispatch(r.requestId, r.uptimeMs)
                ActionDispatchOutcome.COMPLETED -> live.recordOutcome(r.requestId, LiveInteractionTerminal.SUCCESS, r.uptimeMs)
                ActionDispatchOutcome.CANCELLED -> live.recordOutcome(r.requestId, LiveInteractionTerminal.CANCELLED, r.uptimeMs)
                null -> Unit
                else -> live.recordOutcome(r.requestId, LiveInteractionTerminal.FAILED, r.uptimeMs, r.outcome.name.lowercase())
            }
            "sample", "tick" -> live.sample(LiveDiagnosticSample(r.uptimeMs, r.uptimeMs))
            "stop" -> live.finish(r.uptimeMs)
        }
        return DevLoopReplayResult(outcomes, events)
    }
}
