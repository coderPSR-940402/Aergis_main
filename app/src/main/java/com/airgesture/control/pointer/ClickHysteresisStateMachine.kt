package com.airgesture.control.pointer

import kotlin.math.hypot

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
    private var lastContactAt: Long? = null
    private var contactSampleIntervalMs = 0L
    private var lastClickAt: Long? = null
    private var lastTimestampMs: Long? = null

    val isPointerLocked: Boolean
        get() = currentState == State.ENGAGING || currentState == State.CLICKED

    fun processFrame(dNorm: Float, timestampMs: Long, depthSeparation: Float = 0f): Boolean {
        val previous = lastTimestampMs
        if (!dNorm.isFinite() || dNorm < 0f || !depthSeparation.isFinite() || depthSeparation < 0f || timestampMs < 0L ||
            (previous != null && (timestampMs <= previous || timestampMs - previous > MAX_GAP_MS))) {
            reset()
            return false
        }
        lastTimestampMs = timestampMs
        val coolingDown = lastClickAt?.let { timestampMs - it < refractoryPeriodMs } ?: false

        if (dNorm >= releaseThreshold) {
            // Release is evidence for the next occurrence, never an output command.
            observedOpen = true
            contactSince = null
            lastContactAt = null
            contactSampleIntervalMs = 0L
            currentState = if (coolingDown) State.REFRACTORY else State.IDLE
            return false
        }
        if (currentState == State.CLICKED) return false
        if (!observedOpen || coolingDown) return false

        // Projection can hide separation when the hand tilts. Use the existing
        // clearly-open boundary to veto spatially separated contact, tolerating the
        // smaller inferred-z discrepancies seen at visible contact. A depth refit
        // never rearms a consumed click or supplies projected opening evidence.
        if (hypot(dNorm, depthSeparation) >= releaseThreshold) {
            currentState = State.IDLE
            contactSince = null
            lastContactAt = null
            contactSampleIntervalMs = 0L
            return false
        }

        // Lock before contact, rather than after the finger movement has shifted the target.
        currentState = State.ENGAGING
        // A projected-distance refit may briefly cross the contact boundary while
        // the fingers remain together. Retain pending evidence for a bounded time,
        // but confirm only on another actual contact, never on the hysteresis band.
        if (lastContactAt?.let { timestampMs - it > CONTACT_TOLERANCE_MS } == true) {
            contactSince = null
            lastContactAt = null
            contactSampleIntervalMs = 0L
        }
        if (dNorm >= engageThreshold) {
            return false
        }
        val start = contactSince ?: timestampMs.also {
            contactSince = it
            contactSampleIntervalMs = previous?.let { prior -> timestampMs - prior } ?: 0L
        }
        lastContactAt = timestampMs
        // At slow cadence a contact may occupy one frame: accept the already observed
        // open-to-contact transition now. High-rate one-frame glitches still need
        // short internal evidence; there is no user-facing hold command or release tap.
        if (contactSampleIntervalMs < 80L && timestampMs - start < confirmationMs) return false

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
        lastContactAt = null
        lastClickAt = null
        lastTimestampMs = null
        contactSampleIntervalMs = 0L
    }

    private companion object {
        const val MAX_GAP_MS = 350L
        // The recorded interruption spans 233 ms of sensor time (two band samples).
        const val CONTACT_TOLERANCE_MS = 250L
    }
}
