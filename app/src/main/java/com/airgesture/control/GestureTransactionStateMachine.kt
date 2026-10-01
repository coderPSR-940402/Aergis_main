package com.airgesture.control

/**
 * Converts frame-level gesture candidates into deliberate one-shot commands.
 * Pointer movement remains continuous; this gate is for mapped system actions.
 */
internal class GestureTransactionStateMachine(
    private val minimumConfidence: Float = 0.65f,
    private val stableFramesRequired: Int = 2,
    private val neutralFramesRequired: Int = 2,
    private val cooldownMs: Long = 700L,
    private val maxFrameGapMs: Long = 250L
) {
    enum class State {
        NEUTRAL,
        CANDIDATE,
        STABLE,
        ARMED,
        COOLDOWN
    }

    data class Input(
        val action: AirAction,
        val confidence: Float,
        val timestampMs: Long,
        val tracking: Boolean,
        val ownershipId: String?
    )

    private var state = State.NEUTRAL
    private var candidateAction = AirAction.NONE
    private var candidateOwner: String? = null
    private var stableFrames = 0
    private var neutralFrames = 0
    private var cooldownStartedAt = 0L
    private var lastTimestampMs = Long.MIN_VALUE

    fun process(input: Input): AirAction? {
        if (input.timestampMs <= lastTimestampMs) return null
        if (lastTimestampMs != Long.MIN_VALUE && input.timestampMs - lastTimestampMs > maxFrameGapMs) {
            reset()
        }
        lastTimestampMs = input.timestampMs

        val valid = input.tracking &&
            input.action != AirAction.NONE &&
            input.confidence >= minimumConfidence
        if (!valid) {
            return processNeutral(input.timestampMs)
        }

        neutralFrames = 0
        if (state == State.COOLDOWN) return null

        val sameCandidate = candidateAction == input.action && candidateOwner == input.ownershipId
        if (!sameCandidate) {
            candidateAction = input.action
            candidateOwner = input.ownershipId
            stableFrames = 1
            state = State.CANDIDATE
            return null
        }

        when (state) {
            State.NEUTRAL -> {
                stableFrames = 1
                state = State.CANDIDATE
            }
            State.CANDIDATE -> {
                stableFrames++
                if (stableFrames >= stableFramesRequired.coerceAtLeast(1)) {
                    state = State.STABLE
                }
            }
            State.STABLE -> state = State.ARMED
            State.ARMED -> {
                state = State.COOLDOWN
                cooldownStartedAt = input.timestampMs
                return candidateAction
            }
            State.COOLDOWN -> Unit
        }
        return null
    }

    fun currentState(): State = state

    fun reset() {
        state = State.NEUTRAL
        candidateAction = AirAction.NONE
        candidateOwner = null
        stableFrames = 0
        neutralFrames = 0
        cooldownStartedAt = 0L
        lastTimestampMs = Long.MIN_VALUE
    }

    private fun processNeutral(timestampMs: Long): AirAction? {
        if (state == State.COOLDOWN) {
            if (timestampMs - cooldownStartedAt >= cooldownMs) {
                neutralFrames++
                if (neutralFrames >= neutralFramesRequired.coerceAtLeast(1)) {
                    state = State.NEUTRAL
                    candidateAction = AirAction.NONE
                    candidateOwner = null
                    stableFrames = 0
                    neutralFrames = 0
                }
            }
            return null
        }

        state = State.NEUTRAL
        candidateAction = AirAction.NONE
        candidateOwner = null
        stableFrames = 0
        neutralFrames = 0
        return null
    }
}
