package com.airgesture.control

/** Deliberate one-shot classifier commands; independent of pointer and pinch touches. */
internal class GestureTransactionStateMachine(
    private val minimumConfidence: Float = 0.65f,
    private val confirmationMs: Long = 100L,
    private val neutralReleaseMs: Long = 100L,
    private val cooldownMs: Long = 700L,
    private val maxFrameGapMs: Long = 250L
) {
    enum class State { NEUTRAL, CANDIDATE, COOLDOWN }

    data class Input(
        val action: AirAction,
        val confidence: Float,
        val timestampMs: Long,
        val tracking: Boolean,
        val ownershipId: String?
    )

    init {
        require(minimumConfidence.isFinite() && minimumConfidence in 0f..1f)
        require(confirmationMs > 0L && neutralReleaseMs > 0L)
        require(cooldownMs >= 0L && maxFrameGapMs > 0L)
    }

    private var state = State.NEUTRAL
    private var candidateAction = AirAction.NONE
    private var candidateOwner: String? = null
    private var candidateSince = 0L
    private var neutralOwner: String? = null
    private var neutralSince: Long? = null
    private var cooldownStartedAt = 0L
    private var lastTimestampMs: Long? = null

    fun process(input: Input): AirAction? {
        if (input.timestampMs < 0L) return null
        val previous = lastTimestampMs
        if (previous != null && input.timestampMs <= previous) return null
        if (previous != null && input.timestampMs - previous > maxFrameGapMs) reset()
        lastTimestampMs = input.timestampMs

        // Missing/ambiguous tracking is absence of evidence, never proof of release.
        if (!input.tracking || input.ownershipId.isNullOrBlank() ||
            !input.confidence.isFinite() || input.confidence !in 0f..1f) {
            discardEvidence()
            return null
        }
        if (input.action == AirAction.NONE) {
            processNeutral(input)
            return null
        }
        if (input.confidence < minimumConfidence) {
            discardEvidence()
            return null
        }

        neutralSince = null
        neutralOwner = null
        if (state == State.COOLDOWN) return null

        if (state != State.CANDIDATE || candidateAction != input.action ||
            candidateOwner != input.ownershipId) {
            candidateAction = input.action
            candidateOwner = input.ownershipId
            candidateSince = input.timestampMs
            state = State.CANDIDATE
            return null
        }
        if (input.timestampMs - candidateSince < confirmationMs) return null

        state = State.COOLDOWN
        cooldownStartedAt = input.timestampMs
        return candidateAction
    }

    fun currentState(): State = state

    /** Loss/recovery resets evidence, but cannot replay a command still being held. */
    fun reset() {
        discardEvidence()
        lastTimestampMs = null
    }

    private fun discardEvidence() {
        if (state != State.COOLDOWN) state = State.NEUTRAL
        candidateAction = AirAction.NONE
        candidateOwner = null
        candidateSince = 0L
        neutralOwner = null
        neutralSince = null
    }

    private fun processNeutral(input: Input) {
        if (state != State.COOLDOWN) {
            discardEvidence()
            return
        }
        if (neutralSince == null || neutralOwner != input.ownershipId) {
            neutralSince = input.timestampMs
            neutralOwner = input.ownershipId
        }
        if (input.timestampMs - checkNotNull(neutralSince) >= neutralReleaseMs &&
            input.timestampMs - cooldownStartedAt >= cooldownMs) {
            state = State.NEUTRAL
            discardEvidence()
        }
    }
}
