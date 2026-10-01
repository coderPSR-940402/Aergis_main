package com.airgesture.control

internal object VisionRetryPolicy {
    const val BASE_DELAY_MS = 250L
    const val MAX_DELAY_MS = 5_000L

    fun delayForFailure(failureCount: Int): Long {
        require(failureCount > 0) { "failureCount must be positive" }
        val shift = (failureCount - 1).coerceAtMost(30)
        return (BASE_DELAY_MS shl shift).coerceAtMost(MAX_DELAY_MS)
    }
}
