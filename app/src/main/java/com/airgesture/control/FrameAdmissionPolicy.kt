package com.airgesture.control

/** Admission before expensive native work; timestamps keep their original clock. */
internal object FrameAdmissionPolicy {
    const val POINTER_MAX_AGE_MS = 600L
    enum class Rejection { SOURCE_CONTEXT_CHANGED, STALE_BEFORE_INFERENCE, INVALID_CLOCK_ORDER }

    fun rejection(capturedEpoch: Long, currentEpoch: Long, ageMs: Long,
        pointerEnabled: Boolean, mode: ControlMode): Rejection? {
        if (capturedEpoch != currentEpoch) return Rejection.SOURCE_CONTEXT_CHANGED
        if (ageMs < 0L) return Rejection.INVALID_CLOCK_ORDER
        val allowance = if (mode == ControlMode.ARMED && !pointerEnabled)
            VisionResultFreshnessPolicy.DEFAULT_MAX_AGE_MS else POINTER_MAX_AGE_MS
        return if (ageMs > allowance) Rejection.STALE_BEFORE_INFERENCE else null
    }
}
