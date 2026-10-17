package com.airgesture.control

/** One classifier command per occurrence. A fresh, confident neutral interval rearms it. */
internal class GestureTransactionStateMachine(
    private val minimumConfidence: Float = 0.65f,
    private val neutralReleaseMs: Long = 100L,
    private val maxFrameGapMs: Long = 250L
) {
    // NEUTRAL means ready; COOLDOWN means consumed/waiting for neutral, not a timer.
    enum class State { NEUTRAL, COOLDOWN }

    data class Input(
        val action: AirAction,
        val confidence: Float,
        val timestampMs: Long,
        val tracking: Boolean,
        val ownershipId: String?,
        // null only for explicit classifier None evidence; never for a disabled pose.
        val gestureId: String? = null,
        val tuning: GestureTuning? = null,
        val actionEpoch: Long = 0L
    )

    init {
        require(minimumConfidence.isFinite() && minimumConfidence in 0f..1f)
        require(neutralReleaseMs > 0L && maxFrameGapMs > 0L)
    }

    // Starting/recreating an engine with a pose already visible must not execute it.
    private var state = State.COOLDOWN
    private var owner: String? = null
    private var epoch: Long? = null
    private var neutralSince: Long? = null
    private var lastTimestampMs: Long? = null

    fun process(input: Input): AirAction? {
        if (input.timestampMs < 0L) return null
        val previous = lastTimestampMs
        if (previous != null && input.timestampMs <= previous) return null
        if (previous != null && input.timestampMs - previous > maxFrameGapMs) reset()
        lastTimestampMs = input.timestampMs

        if (epoch != input.actionEpoch || owner != input.ownershipId) reset()
        epoch = input.actionEpoch
        owner = input.ownershipId
        if (!input.tracking || input.ownershipId.isNullOrBlank()) {
            reset()
            return null
        }
        val threshold = input.tuning?.bounded()?.confidencePercent?.div(100f) ?: minimumConfidence
        if (!input.confidence.isFinite() || input.confidence !in threshold..1f) {
            // An uncertain category is not release; break any partial neutral interval.
            neutralSince = null
            return null
        }

        if (input.action == AirAction.NONE && input.gestureId == null) {
            if (neutralSince == null) neutralSince = input.timestampMs
            if (input.timestampMs - checkNotNull(neutralSince) >= neutralReleaseMs) state = State.NEUTRAL
            return null
        }

        neutralSince = null
        if (state != State.NEUTRAL) return null
        // Consume even an unavailable/disabled assignment. Changing eligibility while
        // this pose persists must not turn it into a new occurrence or an automatic retry.
        state = State.COOLDOWN
        return input.action.takeUnless { it == AirAction.NONE }
    }

    fun currentState(): State = state

    /** Invalidation always requires fresh neutral, including when no command had fired. */
    fun reset() {
        state = State.COOLDOWN
        neutralSince = null
        // Retain the timestamp high-water mark: reset cannot make old samples fresh.
    }
}
