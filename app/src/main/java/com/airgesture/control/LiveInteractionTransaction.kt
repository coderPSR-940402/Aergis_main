package com.airgesture.control

internal enum class LiveInteractionTerminal {
    SUCCESS,
    FAILED,
    TIMED_OUT,
    CANCELLED
}

internal data class LiveInteractionOutcome(
    val terminal: LiveInteractionTerminal,
    val requestId: Long,
    val action: String,
    val frozenTargetX: Float,
    val frozenTargetY: Float,
    val sessionEpoch: Long,
    val inputEpoch: Long,
    val openedUptimeMs: Long,
    val dispatchedUptimeMs: Long?,
    val finishedUptimeMs: Long,
    val reason: String?
)

/**
 * Correlates one concrete Aergis interaction from recognition to its terminal outcome.
 *
 * This class is intentionally Android-free so recorded interactions can be replayed in
 * deterministic unit tests. The first terminal signal wins; late completion/failure signals
 * return the original immutable outcome rather than rewriting history.
 */
internal class LiveInteractionTransaction private constructor(
    private val requestId: Long,
    private val openedUptimeMs: Long,
    private val action: String,
    private val frozenTargetX: Float,
    private val frozenTargetY: Float,
    private val sessionEpoch: Long,
    private val inputEpoch: Long
) {
    private var dispatchedUptimeMs: Long? = null
    private var terminalOutcome: LiveInteractionOutcome? = null

    @Synchronized
    fun markDispatched(uptimeMs: Long) {
        if (terminalOutcome != null) return
        if (dispatchedUptimeMs == null) dispatchedUptimeMs = uptimeMs
    }

    @Synchronized
    fun complete(uptimeMs: Long): LiveInteractionOutcome =
        finish(LiveInteractionTerminal.SUCCESS, uptimeMs, null)

    @Synchronized
    fun failDispatch(uptimeMs: Long, reason: String): LiveInteractionOutcome =
        finish(LiveInteractionTerminal.FAILED, uptimeMs, reason)

    @Synchronized
    fun timeout(uptimeMs: Long, reason: String): LiveInteractionOutcome =
        finish(LiveInteractionTerminal.TIMED_OUT, uptimeMs, reason)

    @Synchronized
    fun cancel(uptimeMs: Long, reason: String): LiveInteractionOutcome =
        finish(LiveInteractionTerminal.CANCELLED, uptimeMs, reason)

    private fun finish(
        terminal: LiveInteractionTerminal,
        uptimeMs: Long,
        reason: String?
    ): LiveInteractionOutcome {
        terminalOutcome?.let { return it }
        return LiveInteractionOutcome(
            terminal = terminal,
            requestId = requestId,
            action = action,
            frozenTargetX = frozenTargetX,
            frozenTargetY = frozenTargetY,
            sessionEpoch = sessionEpoch,
            inputEpoch = inputEpoch,
            openedUptimeMs = openedUptimeMs,
            dispatchedUptimeMs = dispatchedUptimeMs,
            finishedUptimeMs = uptimeMs,
            reason = reason
        ).also { terminalOutcome = it }
    }

    companion object {
        fun open(
            requestId: Long,
            openedUptimeMs: Long,
            action: String,
            frozenTargetX: Float,
            frozenTargetY: Float,
            sessionEpoch: Long,
            inputEpoch: Long
        ): LiveInteractionTransaction = LiveInteractionTransaction(
            requestId = requestId,
            openedUptimeMs = openedUptimeMs,
            action = action,
            frozenTargetX = frozenTargetX,
            frozenTargetY = frozenTargetY,
            sessionEpoch = sessionEpoch,
            inputEpoch = inputEpoch
        )
    }
}
