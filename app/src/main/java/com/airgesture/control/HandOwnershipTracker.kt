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
    private val maxPalmSizeRatio: Float = 0.6f
) {
    data class Selection(val index: Int, val ownerId: String)

    private var owner: HandObservation? = null
    private var generation = 0L

    fun select(observations: List<HandObservation>, preference: ControlHandPreference): Selection? {
        val candidates = observations.filter { observation ->
            preference == ControlHandPreference.EITHER ||
                observation.handedness.equals(preference.name, ignoreCase = true)
        }
        if (candidates.isEmpty()) {
            owner = null
            return null
        }

        val previous = owner
        val selected = if (previous == null) {
            candidates.first()
        } else {
            candidates
                .map { candidate -> candidate to continuityScore(previous, candidate) }
                .minByOrNull { it.second }
                ?.takeIf { (_, score) -> score <= 1f }
                ?.first
        }

        if (selected == null) {
            owner = null
            return null
        }

        if (previous == null) generation++
        owner = selected
        return Selection(selected.index, "${selected.handedness}:$generation")
    }

    fun reset() {
        owner = null
    }

    private fun continuityScore(previous: HandObservation, candidate: HandObservation): Float {
        val centerDistance = hypot(
            (candidate.centerX - previous.centerX).toDouble(),
            (candidate.centerY - previous.centerY).toDouble()
        ).toFloat()
        val sizeRatio = candidate.palmSize / previous.palmSize.coerceAtLeast(MIN_PALM_SIZE)
        val normalizedSizeDelta = abs(sizeRatio - 1f)
        val handednessPenalty = if (candidate.handedness == previous.handedness) 0f else 0.35f
        if (centerDistance > maxCenterDistance || normalizedSizeDelta > maxPalmSizeRatio) {
            return Float.POSITIVE_INFINITY
        }
        return centerDistance / maxCenterDistance + normalizedSizeDelta / maxPalmSizeRatio + handednessPenalty
    }

    private companion object {
        const val MIN_PALM_SIZE = 0.001f
    }
}
