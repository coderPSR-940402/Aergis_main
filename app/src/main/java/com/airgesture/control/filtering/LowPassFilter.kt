package com.airgesture.control.filtering

/** Single-dimension first-order low-pass filter used by the 1 Euro filter. */
internal class LowPassFilter {
    private var previous: Float? = null

    fun filter(value: Float, alpha: Float): Float {
        val result = previous?.let { alpha * value + (1f - alpha) * it } ?: value
        previous = result
        return result
    }

    fun lastValue(): Float? = previous

    fun reset() {
        previous = null
    }
}
