package com.airgesture.control.pointer

/** Thumb-middle contact. Tracking must observe separation before accepting a click. */
class ClickHysteresisStateMachine(
    private val engageThreshold: Float = 0.18f,
    private val releaseThreshold: Float = 0.32f,
    private val confirmationMs: Long = 40L,
    private val refractoryPeriodMs: Long = 110L
) {
    enum class State { IDLE, ENGAGING, CLICKED, REFRACTORY }

    private var currentState = State.IDLE
    private var observedOpen = false
    private var contactSince: Long? = null
    private var contactSampleIntervalMs = 0L
    private var lastClickAt: Long? = null
    private var lastTimestampMs: Long? = null

    val isPointerLocked: Boolean
        get() = currentState == State.ENGAGING || currentState == State.CLICKED

    fun processFrame(dNorm: Float, timestampMs: Long): Boolean {
        val previous = lastTimestampMs
        if (!dNorm.isFinite() || dNorm < 0f || timestampMs < 0L ||
            (previous != null && (timestampMs <= previous || timestampMs - previous > MAX_GAP_MS))) {
            reset()
            return false
        }
        lastTimestampMs = timestampMs
        val coolingDown = lastClickAt?.let { timestampMs - it < refractoryPeriodMs } ?: false

        if (dNorm >= releaseThreshold) {
            // At 4-6 fps a complete pinch can occupy one sample. Confirm that sequence on
            // release; never synthesize a held press from it, and never accept a one-frame
            // high-rate glitch. A held pinch has already clicked and cannot click twice.
            val releaseClick = currentState == State.ENGAGING && !coolingDown &&
                contactSampleIntervalMs >= 80L &&
                contactSince?.let { timestampMs - it >= confirmationMs } == true
            if (releaseClick) lastClickAt = timestampMs
            observedOpen = true
            contactSince = null
            contactSampleIntervalMs = 0L
            currentState = if (coolingDown || releaseClick) State.REFRACTORY else State.IDLE
            return releaseClick
        }
        if (currentState == State.CLICKED) return false
        if (!observedOpen || coolingDown) return false

        // Lock before contact, rather than after the finger movement has shifted the target.
        currentState = State.ENGAGING
        if (dNorm >= engageThreshold) {
            contactSince = null
            return false
        }
        val start = contactSince ?: timestampMs.also {
            contactSince = it
            contactSampleIntervalMs = previous?.let { prior -> timestampMs - prior } ?: 0L
        }
        if (timestampMs - start < confirmationMs) return false

        currentState = State.CLICKED
        observedOpen = false
        lastClickAt = timestampMs
        return true
    }

    fun getCurrentState(): State = currentState

    fun reset() {
        currentState = State.IDLE
        observedOpen = false
        contactSince = null
        lastClickAt = null
        lastTimestampMs = null
        contactSampleIntervalMs = 0L
    }

    private companion object { const val MAX_GAP_MS = 350L }
}
