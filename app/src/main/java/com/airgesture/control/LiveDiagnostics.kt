package com.airgesture.control

/**
 * Correlates Aergis interactions with their terminal outcomes and promotes only meaningful
 * diagnostic events. All storage/relay work is delegated to [LiveDiagnosticEventSink] and sink
 * failures are isolated from the control path.
 */
internal class LiveDiagnostics(
    private val buffer: LiveDiagnosticBuffer,
    private val sink: LiveDiagnosticEventSink,
    private val preWindowMs: Long = 30_000L,
    private val postWindowMs: Long = 10_000L,
    private val interactionTimeoutMs: Long = 500L,
    private val dedupWindowMs: Long = 2_000L
) {
    private data class ActiveInteraction(
        val transaction: LiveInteractionTransaction,
        val deadlineUptimeMs: Long
    )

    private data class PendingEvent(
        val trigger: LiveDiagnosticTrigger,
        val before: List<LiveDiagnosticSample>,
        val readyUptimeMs: Long
    )

    private val lock = Any()
    private val active = LinkedHashMap<Long, ActiveInteraction>()
    private val pending = ArrayList<PendingEvent>()
    private val emittedRequestKinds = HashSet<Pair<Long, LiveDiagnosticKind>>()
    private val lastGenericEventAt = HashMap<String, Long>()

    init {
        require(preWindowMs >= 0L) { "preWindowMs must be non-negative" }
        require(postWindowMs >= 0L) { "postWindowMs must be non-negative" }
        require(interactionTimeoutMs >= 0L) { "interactionTimeoutMs must be non-negative" }
        require(dedupWindowMs >= 0L) { "dedupWindowMs must be non-negative" }
    }

    fun sample(sample: LiveDiagnosticSample) {
        val ready = ArrayList<LiveDiagnosticSnapshot>()
        synchronized(lock) {
            buffer.add(sample)
            expireInteractionsLocked(sample, ready)
            finalizePendingLocked(sample.uptimeMs, ready)
        }
        publishSafely(ready)
    }

    fun openInteraction(
        requestId: Long,
        openedUptimeMs: Long,
        action: String,
        frozenTargetX: Float,
        frozenTargetY: Float,
        sessionEpoch: Long,
        inputEpoch: Long
    ) {
        synchronized(lock) {
            if (active.containsKey(requestId)) return
            active[requestId] = ActiveInteraction(
                transaction = LiveInteractionTransaction.open(
                    requestId = requestId,
                    openedUptimeMs = openedUptimeMs,
                    action = action,
                    frozenTargetX = frozenTargetX,
                    frozenTargetY = frozenTargetY,
                    sessionEpoch = sessionEpoch,
                    inputEpoch = inputEpoch
                ),
                deadlineUptimeMs = openedUptimeMs + interactionTimeoutMs
            )
        }
    }

    fun recordDispatch(requestId: Long, uptimeMs: Long) {
        synchronized(lock) {
            active[requestId]?.transaction?.markDispatched(uptimeMs)
        }
    }

    fun recordOutcome(
        requestId: Long,
        terminal: LiveInteractionTerminal,
        uptimeMs: Long,
        reasonCode: String? = null
    ) {
        val ready = ArrayList<LiveDiagnosticSnapshot>()
        synchronized(lock) {
            val current = active.remove(requestId) ?: return
            val outcome = when (terminal) {
                LiveInteractionTerminal.SUCCESS -> current.transaction.complete(uptimeMs)
                LiveInteractionTerminal.FAILED -> current.transaction.failDispatch(
                    uptimeMs,
                    reasonCode ?: "dispatch_failed"
                )
                LiveInteractionTerminal.TIMED_OUT -> current.transaction.timeout(
                    uptimeMs,
                    reasonCode ?: "completion_timeout"
                )
                LiveInteractionTerminal.CANCELLED -> current.transaction.cancel(
                    uptimeMs,
                    reasonCode ?: "cancelled"
                )
            }

            when (outcome.terminal) {
                LiveInteractionTerminal.SUCCESS,
                LiveInteractionTerminal.CANCELLED -> Unit

                LiveInteractionTerminal.FAILED -> queueTriggerLocked(
                    triggerForOutcome(
                        outcome = outcome,
                        kind = LiveDiagnosticKind.ACTION_FAILED,
                        severity = LiveDiagnosticSeverity.ERROR,
                        reasonCode = outcome.reason ?: "dispatch_failed"
                    ),
                    ready
                )

                LiveInteractionTerminal.TIMED_OUT -> queueTriggerLocked(
                    triggerForOutcome(
                        outcome = outcome,
                        kind = if (outcome.action == "TAP") {
                            LiveDiagnosticKind.MISSED_CLICK
                        } else {
                            LiveDiagnosticKind.ACTION_FAILED
                        },
                        severity = LiveDiagnosticSeverity.ERROR,
                        reasonCode = outcome.reason ?: "completion_timeout"
                    ),
                    ready
                )
            }
        }
        publishSafely(ready)
    }

    fun trigger(trigger: LiveDiagnosticTrigger) {
        val ready = ArrayList<LiveDiagnosticSnapshot>()
        synchronized(lock) {
            queueTriggerLocked(trigger, ready)
        }
        publishSafely(ready)
    }

    private fun expireInteractionsLocked(
        sample: LiveDiagnosticSample,
        ready: MutableList<LiveDiagnosticSnapshot>
    ) {
        if (active.isEmpty()) return
        val expired = active.entries
            .filter { sample.uptimeMs >= it.value.deadlineUptimeMs }
            .map { it.key to it.value }

        for ((requestId, current) in expired) {
            if (active.remove(requestId) == null) continue
            val outcome = current.transaction.timeout(
                current.deadlineUptimeMs,
                "completion_timeout"
            )
            val wallClockAtDeadline = (sample.wallClockMs -
                (sample.uptimeMs - current.deadlineUptimeMs)).coerceAtLeast(0L)
            queueTriggerLocked(
                LiveDiagnosticTrigger(
                    kind = if (outcome.action == "TAP") {
                        LiveDiagnosticKind.MISSED_CLICK
                    } else {
                        LiveDiagnosticKind.ACTION_FAILED
                    },
                    severity = LiveDiagnosticSeverity.ERROR,
                    uptimeMs = current.deadlineUptimeMs,
                    wallClockMs = wallClockAtDeadline,
                    requestId = outcome.requestId,
                    action = outcome.action,
                    reasonCode = "completion_timeout",
                    frozenTargetX = finiteOrNull(outcome.frozenTargetX),
                    frozenTargetY = finiteOrNull(outcome.frozenTargetY),
                    sessionEpoch = outcome.sessionEpoch,
                    inputEpoch = outcome.inputEpoch
                ),
                ready
            )
        }
    }

    private fun triggerForOutcome(
        outcome: LiveInteractionOutcome,
        kind: LiveDiagnosticKind,
        severity: LiveDiagnosticSeverity,
        reasonCode: String
    ): LiveDiagnosticTrigger = LiveDiagnosticTrigger(
        kind = kind,
        severity = severity,
        uptimeMs = outcome.finishedUptimeMs,
        wallClockMs = outcome.finishedUptimeMs,
        requestId = outcome.requestId,
        action = outcome.action,
        reasonCode = reasonCode,
        frozenTargetX = finiteOrNull(outcome.frozenTargetX),
        frozenTargetY = finiteOrNull(outcome.frozenTargetY),
        sessionEpoch = outcome.sessionEpoch,
        inputEpoch = outcome.inputEpoch
    )

    private fun queueTriggerLocked(
        trigger: LiveDiagnosticTrigger,
        ready: MutableList<LiveDiagnosticSnapshot>
    ) {
        if (!acceptTriggerLocked(trigger)) return
        val before = buffer.snapshotBefore(trigger.uptimeMs, preWindowMs)
        if (postWindowMs == 0L) {
            ready += LiveDiagnosticSnapshot(trigger, before, emptyList())
            return
        }
        pending += PendingEvent(
            trigger = trigger,
            before = before,
            readyUptimeMs = trigger.uptimeMs + postWindowMs
        )
    }

    private fun acceptTriggerLocked(trigger: LiveDiagnosticTrigger): Boolean {
        trigger.requestId?.let { requestId ->
            return emittedRequestKinds.add(requestId to trigger.kind)
        }

        val key = trigger.dedupKey ?: buildString {
            append(trigger.kind.name)
            append(':')
            append(trigger.reasonCode.orEmpty())
        }
        val previous = lastGenericEventAt[key]
        if (previous != null && trigger.uptimeMs - previous < dedupWindowMs) return false
        lastGenericEventAt[key] = trigger.uptimeMs
        return true
    }

    private fun finalizePendingLocked(
        currentUptimeMs: Long,
        ready: MutableList<LiveDiagnosticSnapshot>
    ) {
        if (pending.isEmpty()) return
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val event = iterator.next()
            if (currentUptimeMs < event.readyUptimeMs) continue
            ready += LiveDiagnosticSnapshot(
                trigger = event.trigger,
                before = event.before,
                after = buffer.snapshotAfter(event.trigger.uptimeMs, postWindowMs)
            )
            iterator.remove()
        }
    }

    private fun publishSafely(snapshots: List<LiveDiagnosticSnapshot>) {
        for (snapshot in snapshots) {
            runCatching { sink.publish(snapshot) }
        }
    }

    private fun finiteOrNull(value: Float): Float? = if (value.isFinite()) value else null
}
