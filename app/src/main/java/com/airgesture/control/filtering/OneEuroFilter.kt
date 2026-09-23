package com.airgesture.control.filtering

import kotlin.math.PI
import kotlin.math.abs

/**
 * Adaptive low-pass filter that reduces jitter while limiting lag during motion.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.01f,
    private val dCutoff: Float = 1.0f
) {
    private val valueFilter = LowPassFilter()
    private val derivativeFilter = LowPassFilter()
    private var lastTimestampMs = -1L

    fun filter(value: Float, timestampMs: Long): Float {
        if (lastTimestampMs < 0L) {
            lastTimestampMs = timestampMs
            return valueFilter.filter(value, 1f)
        }

        val dt = (timestampMs - lastTimestampMs) / 1000f
        lastTimestampMs = timestampMs
        if (dt <= 0.0001f) return valueFilter.lastValue() ?: value

        val previousValue = valueFilter.lastValue() ?: value
        val derivative = (value - previousValue) / dt
        val filteredDerivative = derivativeFilter.filter(
            derivative,
            alpha(dt, dCutoff)
        )
        val cutoff = minCutoff + beta * abs(filteredDerivative)
        return valueFilter.filter(value, alpha(dt, cutoff))
    }

    fun reset() {
        valueFilter.reset()
        derivativeFilter.reset()
        lastTimestampMs = -1L
    }

    private fun alpha(dt: Float, cutoff: Float): Float {
        val safeCutoff = cutoff.coerceAtLeast(0.001f)
        val tau = 1f / (2f * PI.toFloat() * safeCutoff)
        return 1f / (1f + tau / dt)
    }
}
