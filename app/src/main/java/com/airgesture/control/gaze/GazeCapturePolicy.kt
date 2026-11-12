package com.airgesture.control.gaze

import kotlin.math.abs

/**
 * Stable, fresh, distinct exposure-time samples from the current target only.
 * These authorize calibration capture, never pointer movement or Android actions.
 */
internal object GazeCapturePolicy {
    const val TARGET_SETTLE_MS = 1800L
    private const val FRESH_MS = 800L
    private const val LAST_SAMPLE_MS = 250L

    fun stableMedian(observations: List<GazeLab.Observation>, nowMs: Long,
        targetShownAtMs: Long): DoubleArray? {
        if (nowMs < targetShownAtMs) return null
        // A missing or corrupted current-target frame breaks stable fixation.
        // Never manufacture five clean samples by silently ignoring bad ones.
        if (observations.any { sample ->
            sample.uptimeMs in targetShownAtMs..nowMs &&
                nowMs - sample.uptimeMs <= FRESH_MS &&
                (sample.features == null || sample.features.size != 4 ||
                    sample.features.any { !it.isFinite() })
        }) return null
        val fresh = observations.filter { sample ->
            sample.features?.let { values -> values.size == 4 && values.all(Double::isFinite) } == true &&
                sample.uptimeMs in targetShownAtMs..nowMs && nowMs - sample.uptimeMs <= FRESH_MS
        }
        if (fresh.size < 5 || fresh.map { it.uptimeMs }.distinct().size < 5 ||
            nowMs - fresh.last().uptimeMs > LAST_SAMPLE_MS) return null
        val center = DoubleArray(4) { i ->
            fresh.map { it.features!![i] }.sorted()[fresh.size / 2]
        }
        if (fresh.any { observation ->
            (0..3).any { i ->
                abs(observation.features!![i] - center[i]) > if (i < 2) .04 else .035
            }
        }) return null
        return center
    }

    fun automaticCapture(observations: List<GazeLab.Observation>, nowMs: Long,
        targetShownAtMs: Long): DoubleArray? =
        if (nowMs >= targetShownAtMs && nowMs - targetShownAtMs >= TARGET_SETTLE_MS)
            stableMedian(observations, nowMs, targetShownAtMs) else null
}
