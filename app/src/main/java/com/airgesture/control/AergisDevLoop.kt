package com.airgesture.control

/**
 * Thin observer bridge between Aergis' existing action pipeline and [LiveDiagnostics].
 *
 * This object deliberately owns no gesture, pointer, dispatch, or accessibility state. The
 * authoritative request id, frozen target and terminal outcomes remain owned by the existing
 * runtime; DevLoop only mirrors them into the diagnostic correlator.
 */
internal object AergisDevLoop {
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
