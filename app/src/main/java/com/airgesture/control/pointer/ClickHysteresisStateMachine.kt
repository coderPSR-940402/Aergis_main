package com.airgesture.control.pointer

class ClickHysteresisStateMachine(
    private val engageThreshold: Float = 0.18f,
    private val releaseThreshold: Float = 0.32f,
    private val dwellRequiredFrames: Int = 2,
    private val refractoryPeriodMs: Long = 150L,
    private val minApproachVelocity: Float = -0.5f
) {
    enum class State {
        IDLE,
        ENGAGING,
        CLICKED,
        REFRACTORY
    }

    private var currentState = State.IDLE
    private var dwellCounter = 0
    private var lastRefractoryStartTime = 0L
    private var lastDNorm = 1.0f
    private var lastTimestampMs = 0L

    fun processFrame(dNorm: Float, timestampMs: Long): Boolean {
        val safeDNorm = dNorm.coerceAtLeast(0f)
        val dt = if (lastTimestampMs > 0L) {
            ((timestampMs - lastTimestampMs).coerceAtLeast(0L)) / 1000.0f
        } else {
            0.033f
        }
        lastTimestampMs = timestampMs

        val approachVelocity = if (dt > 0.001f) {
            (safeDNorm - lastDNorm) / dt
        } else {
            0.0f
        }
        lastDNorm = safeDNorm

        return when (currentState) {
            State.REFRACTORY -> {
                if (timestampMs - lastRefractoryStartTime >= refractoryPeriodMs) {
                    currentState = State.IDLE
                    dwellCounter = 0
                }
                false
            }

            State.IDLE -> {
                if (isEngaging(safeDNorm, approachVelocity)) {
                    dwellCounter = 1
                    currentState = State.ENGAGING
                }
                false
            }

            State.ENGAGING -> {
                if (safeDNorm < engageThreshold) {
                    dwellCounter++
                    if (dwellCounter >= dwellRequiredFrames.coerceAtLeast(1)) {
                        currentState = State.CLICKED
                        return true
                    }
                } else {
                    currentState = State.IDLE
                    dwellCounter = 0
                }
                false
            }

            State.CLICKED -> {
                if (safeDNorm > releaseThreshold) {
                    currentState = State.REFRACTORY
                    lastRefractoryStartTime = timestampMs
                    dwellCounter = 0
                }
                false
            }
        }
    }

    fun getCurrentState(): State = currentState

    fun reset() {
        currentState = State.IDLE
        dwellCounter = 0
        lastRefractoryStartTime = 0L
        lastDNorm = 1.0f
        lastTimestampMs = 0L
    }

    private fun isEngaging(dNorm: Float, approachVelocity: Float): Boolean {
        if (dNorm >= engageThreshold) return false
        return approachVelocity <= minApproachVelocity || dNorm <= engageThreshold * 0.75f
    }
}
