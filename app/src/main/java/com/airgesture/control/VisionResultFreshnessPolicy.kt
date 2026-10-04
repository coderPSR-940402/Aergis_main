package com.airgesture.control

/**
 * Rejects vision results that are duplicated, out of order, too old, or separated
 * from the accepted stream by an unsafe processing gap.
 *
 * Timestamps must use one monotonic clock domain. The policy deliberately does not
 * dispatch actions; callers must reset their control state when recovery is required.
 */
internal class VisionResultFreshnessPolicy(
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val maxGapMs: Long = DEFAULT_MAX_GAP_MS
) {
    init {
        require(maxAgeMs >= 0L) { "maxAgeMs must be non-negative" }
        require(maxGapMs >= 0L) { "maxGapMs must be non-negative" }
    }

    enum class RejectionReason {
        ACCEPTED,
        INVALID_CLOCK_ORDER,
        DUPLICATE_TIMESTAMP,
        OUT_OF_ORDER_TIMESTAMP,
        STALE_RESULT,
        EXCESSIVE_GAP
    }

    data class Decision(
        val accepted: Boolean,
        val reason: RejectionReason,
        val ageMs: Long,
        val gapMs: Long,
        val requiresRecovery: Boolean
    )

    private var lastAcceptedTimestampMs = Long.MIN_VALUE

    fun evaluate(resultTimestampMs: Long, observedAtMs: Long): Decision {
        require(resultTimestampMs >= 0L) { "resultTimestampMs must be non-negative" }
        require(observedAtMs >= 0L) { "observedAtMs must be non-negative" }

        val previous = lastAcceptedTimestampMs
        if (observedAtMs < resultTimestampMs) {
            return Decision(
                accepted = false,
                reason = RejectionReason.INVALID_CLOCK_ORDER,
                ageMs = 0L,
                gapMs = 0L,
                requiresRecovery = true
            )
        }
        val ageMs = observedAtMs - resultTimestampMs
        val gapMs = if (previous == Long.MIN_VALUE) 0L else resultTimestampMs - previous

        if (previous != Long.MIN_VALUE && resultTimestampMs == previous) {
            return Decision(false, RejectionReason.DUPLICATE_TIMESTAMP, ageMs, 0L, true)
        }
        if (previous != Long.MIN_VALUE && resultTimestampMs < previous) {
            return Decision(false, RejectionReason.OUT_OF_ORDER_TIMESTAMP, ageMs, gapMs, true)
        }
        if (ageMs > maxAgeMs) {
            // Advance the watermark so a subsequent fresh result can recover instead
            // of being rejected forever because it follows the same old frame.
            lastAcceptedTimestampMs = resultTimestampMs
            return Decision(false, RejectionReason.STALE_RESULT, ageMs, gapMs, true)
        }
        if (previous != Long.MIN_VALUE && gapMs > maxGapMs) {
            // The current result is not trusted to continue the old transaction, but
            // it becomes the recovery watermark for the next ordered fresh result.
            lastAcceptedTimestampMs = resultTimestampMs
            return Decision(false, RejectionReason.EXCESSIVE_GAP, ageMs, gapMs, true)
        }

        lastAcceptedTimestampMs = resultTimestampMs
        return Decision(true, RejectionReason.ACCEPTED, ageMs, gapMs, false)
    }

    fun reset() {
        lastAcceptedTimestampMs = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 220L
        const val DEFAULT_MAX_GAP_MS = 250L
    }
}
