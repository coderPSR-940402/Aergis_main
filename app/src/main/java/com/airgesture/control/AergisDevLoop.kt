package com.airgesture.control

/**
 * Thin observer bridge between Aergis' existing action pipeline and [LiveDiagnostics].
 *
 * This object deliberately owns no gesture, pointer, dispatch, or accessibility state. The
 * authoritative request id, frozen target and terminal outcomes remain owned by the existing
 * runtime; DevLoop only mirrors them into the diagnostic correlator.
 */
internal object AergisDevLoop {
    @Volatile private var worker: DevLoopWorker? = null

    @Synchronized fun start(context: android.content.Context) {
        if (worker != null) return
        runCatching {
            DevLoopWorker(java.io.File(context.filesDir, "devloop"),
                "${BuildConfig.SOURCE_COMMIT}:${BuildConfig.VERSION_CODE}",
                android.os.SystemClock::uptimeMillis, System::currentTimeMillis,
                { android.util.Log.i("AergisDevLoop", it) }).also { worker = it; it.start() }
        }
    }

    @Synchronized fun stop(): java.util.concurrent.Future<*>? {
        val previous = worker
        worker = null
        return runCatching { previous?.stop() }.getOrNull()
    }

    fun sampleFrame(sample: LiveDiagnosticSample, rawX: Float?, rawY: Float?, tracking: Boolean) {
        runCatching { worker?.observe { sample(sample, rawX, rawY, tracking) } }
    }

    fun checkpoint() {
        val now = android.os.SystemClock.uptimeMillis()
        val wall = System.currentTimeMillis()
        runCatching { worker?.observe { checkpoint(now, wall) } }
    }

    @Volatile
    private var diagnostics: LiveDiagnostics? = null

    fun install(liveDiagnostics: LiveDiagnostics) {
        diagnostics = liveDiagnostics
    }

    fun uninstall() {
        diagnostics = null
    }

    internal fun installForTest(liveDiagnostics: LiveDiagnostics) = install(liveDiagnostics)

    internal fun resetForTest() = uninstall()

    fun openAction(
        requestId: Long,
        openedUptimeMs: Long,
        action: String,
        frozenTargetX: Float,
        frozenTargetY: Float,
        sessionEpoch: Long,
        inputEpoch: Long
    ) {
        runCatching { worker?.observe { open(requestId, openedUptimeMs, action, frozenTargetX, frozenTargetY, sessionEpoch, inputEpoch) } }
        val current = diagnostics ?: return
        runCatching {
            current.openInteraction(
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

    fun recordActionOutcome(
        outcome: ActionDispatchOutcome,
        requestId: Long,
        uptimeMs: Long
    ) {
        runCatching { worker?.observe { outcome(outcome, requestId, uptimeMs) } }
        val current = diagnostics ?: return
        runCatching {
            when (outcome) {
                ActionDispatchOutcome.SUBMITTED -> current.recordDispatch(requestId, uptimeMs)
                ActionDispatchOutcome.COMPLETED -> current.recordOutcome(
                    requestId = requestId,
                    terminal = LiveInteractionTerminal.SUCCESS,
                    uptimeMs = uptimeMs
                )
                ActionDispatchOutcome.CANCELLED -> current.recordOutcome(
                    requestId = requestId,
                    terminal = LiveInteractionTerminal.CANCELLED,
                    uptimeMs = uptimeMs,
                    reasonCode = "cancelled"
                )
                ActionDispatchOutcome.POLICY_DENIED -> current.recordFailure(
                    requestId, uptimeMs, "policy_denied"
                )
                ActionDispatchOutcome.PLATFORM_REJECTED -> current.recordFailure(
                    requestId, uptimeMs, "platform_rejected"
                )
                ActionDispatchOutcome.PLATFORM_UNAVAILABLE -> current.recordFailure(
                    requestId, uptimeMs, "platform_unavailable"
                )
                ActionDispatchOutcome.EXECUTION_FAILED -> current.recordFailure(
                    requestId, uptimeMs, "execution_failed"
                )
            }
        }
    }

    private fun LiveDiagnostics.recordFailure(
        requestId: Long,
        uptimeMs: Long,
        reasonCode: String
    ) {
        recordOutcome(
            requestId = requestId,
            terminal = LiveInteractionTerminal.FAILED,
            uptimeMs = uptimeMs,
            reasonCode = reasonCode
        )
    }
}
