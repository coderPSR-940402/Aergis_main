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
        val dtSeconds = if (lastTimestampMs > 0L) {
            ((timestampMs - lastTimestampMs).coerceAtLeast(1L)) / 1000.0f
        } else {
            0.033f
        }
        lastTimestampMs = timestampMs

        val approachVelocity = (safeDNorm - lastDNorm) / dtSeconds
        lastDNorm = safeDNorm

        when (currentState) {
            State.REFRACTORY -> {
                if (timestampMs - lastRefractoryStartTime >= refractoryPeriodMs) {
                    currentState = State.IDLE
                    dwellCounter = 0
                }
                return false
            }

            State.IDLE -> {
                if (safeDNorm < engageThreshold && approachVelocity <= minApproachVelocity) {
                    dwellCounter = 1
                    currentState = State.ENGAGING
                }
                return false
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
                return false
            }

            State.CLICKED -> {
                if (safeDNorm > releaseThreshold) {
                    currentState = State.REFRACTORY
                    lastRefractoryStartTime = timestampMs
                    dwellCounter = 0
                }
                return true
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
}
