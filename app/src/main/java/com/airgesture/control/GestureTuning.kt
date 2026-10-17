package com.airgesture.control

/** Saved per gesture. Legacy hold/repeat values are retained for rollback, not activation. */
data class GestureTuning(
    val holdMs: Long = 100L,
    val repeatDelayMs: Long = 700L,
    val confidencePercent: Int = 65,
    val volumeSteps: Int = 1,
    val scrollPercent: Int = 50,
    val longPressMs: Long = 650L
) {
    fun bounded() = copy(holdMs = holdMs.coerceIn(100L, 1500L),
        repeatDelayMs = repeatDelayMs.coerceIn(300L, 3000L),
        confidencePercent = confidencePercent.coerceIn(65, 95),
        volumeSteps = volumeSteps.coerceIn(1, 5), scrollPercent = scrollPercent.coerceIn(15, 85),
        longPressMs = longPressMs.coerceIn(500L, 2000L))
}
