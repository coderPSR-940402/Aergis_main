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

/** A GPU that starts successfully can still fail on its first or a later inference. */
internal class VisionDelegateRecovery {
    var preferGpu = true
        private set

    inline fun <T> infer(onGpu: Boolean, block: () -> T): T = try {
        block()
    } catch (failure: RuntimeException) {
        if (onGpu) preferGpu = false
        throw failure
    }
}
