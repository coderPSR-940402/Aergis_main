package com.airgesture.control

import kotlin.math.abs
import kotlin.math.hypot

internal data class HandObservation(
    val index: Int,
    val handedness: String,
    val centerX: Float,
    val centerY: Float,
    val palmSize: Float
)

internal class HandOwnershipTracker(
    private val maxCenterDistance: Float = 0.22f,
    private val maxPalmSizeRatio: Float = 0.6f,
    private val ambiguityMargin: Float = 0.2f
) {
    data class Selection(val index: Int, val ownerId: String)

    private var owner: HandObservation? = null
    private var generation = 0L
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastTimestampMs = Long.MIN_VALUE

    fun select(
        observations: List<HandObservation>,
        preference: ControlHandPreference,
        timestampMs: Long
    ): Selection? {
        if (timestampMs <= lastTimestampMs) return null
        val candidates = observations.filter { observation ->
            preference == ControlHandPreference.EITHER ||
                observation.handedness.equals(preference.name, ignoreCase = true)
        }
        if (candidates.isEmpty()) {
            clearOwnership(timestampMs)
            return null
        }

        val previous = owner
        val selected = if (previous == null) {
            // With no track history there is no evidence to distinguish two hands.
            candidates.singleOrNull()
        } else {
            val scores = candidates.map { candidate ->
                candidate to continuityScore(previous, candidate, timestampMs)
            }.filter { (_, score) -> score.isFinite() }
                .sortedBy { (_, score) -> score }
            val best = scores.firstOrNull()
            val runnerUp = scores.getOrNull(1)
            if (best == null ||
                (runnerUp != null && runnerUp.second - best.second < ambiguityMargin)
            ) {
                null
            } else {
                best.first
            }
        }

        if (selected == null) {
            clearOwnership(timestampMs)
            return null
        }

        val prior = owner
        if (prior == null) generation++
        updateMotion(prior, selected, timestampMs)
        owner = selected
        lastTimestampMs = timestampMs
        return Selection(selected.index, "${selected.handedness}:$generation")
    }

    fun reset() {
        owner = null
        velocityX = 0f
        velocityY = 0f
        lastTimestampMs = Long.MIN_VALUE
    }

    private fun continuityScore(
        previous: HandObservation,
        candidate: HandObservation,
        timestampMs: Long
    ): Float {
        val elapsedSeconds = ((timestampMs - lastTimestampMs).coerceAtLeast(1L)) / 1000f
        val predictionSeconds = elapsedSeconds.coerceAtMost(MAX_PREDICTION_SECONDS)
        val predictedX = previous.centerX + velocityX * predictionSeconds
        val predictedY = previous.centerY + velocityY * predictionSeconds
        val centerDistance = hypot(
            (candidate.centerX - predictedX).toDouble(),
            (candidate.centerY - predictedY).toDouble()
        ).toFloat()
        val sizeRatio = candidate.palmSize / previous.palmSize.coerceAtLeast(MIN_PALM_SIZE)
        val normalizedSizeDelta = abs(sizeRatio - 1f)
        val handednessPenalty = if (candidate.handedness == previous.handedness) 0f else HANDEDNESS_PENALTY
        if (centerDistance > maxCenterDistance || normalizedSizeDelta > maxPalmSizeRatio) {
            return Float.POSITIVE_INFINITY
        }
        return centerDistance / maxCenterDistance +
            normalizedSizeDelta / maxPalmSizeRatio +
            handednessPenalty
    }

    private fun updateMotion(
        previous: HandObservation?,
        selected: HandObservation,
        timestampMs: Long
    ) {
        if (previous == null || lastTimestampMs == Long.MIN_VALUE) {
            velocityX = 0f
            velocityY = 0f
            return
        }
        val elapsedSeconds = ((timestampMs - lastTimestampMs).coerceAtLeast(1L)) / 1000f
        val observedVelocityX = (selected.centerX - previous.centerX) / elapsedSeconds
        val observedVelocityY = (selected.centerY - previous.centerY) / elapsedSeconds
        velocityX = velocityX * VELOCITY_SMOOTHING + observedVelocityX * (1f - VELOCITY_SMOOTHING)
        velocityY = velocityY * VELOCITY_SMOOTHING + observedVelocityY * (1f - VELOCITY_SMOOTHING)
    }

    private fun clearOwnership(timestampMs: Long) {
        owner = null
        velocityX = 0f
        velocityY = 0f
        lastTimestampMs = timestampMs
    }

    private companion object {
        const val MIN_PALM_SIZE = 0.001f
        const val HANDEDNESS_PENALTY = 0.35f
        const val MAX_PREDICTION_SECONDS = 0.25f
        const val VELOCITY_SMOOTHING = 0.5f
    }
}
