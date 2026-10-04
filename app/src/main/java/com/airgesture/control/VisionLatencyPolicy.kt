package com.airgesture.control

/**
 * Safety limits for asynchronous vision results.
 *
 * Pointer updates tolerate less staleness than one-shot gesture commands because
 * stale pointer motion is immediately visible and can inject an unintended touch.
 */
object VisionLatencyPolicy {
    const val POINTER_HARD_MAX_RESULT_AGE_MS = 220L
    const val GESTURE_HARD_MAX_RESULT_AGE_MS = 450L
    const val POINTER_MAX_IN_FLIGHT = 2
    const val GESTURE_MAX_IN_FLIGHT = 2

    fun maxInFlight(pointer: Boolean): Int =
        if (pointer) POINTER_MAX_IN_FLIGHT else GESTURE_MAX_IN_FLIGHT

    fun resultIsFreshEnough(pointer: Boolean, ageMs: Long): Boolean =
        ageMs in 0L..if (pointer) POINTER_HARD_MAX_RESULT_AGE_MS else GESTURE_HARD_MAX_RESULT_AGE_MS
}
