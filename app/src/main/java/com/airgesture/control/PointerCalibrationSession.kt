package com.airgesture.control

import kotlin.math.max
import kotlin.math.min

/** Collects normalized calibration samples and produces a validated profile. */
class PointerCalibrationSession(
    private val minimumSamples: Int = 4,
    private val mirrorX: Boolean = true
) {
    enum class State { IDLE, COLLECTING, READY, INVALID }

    private var state = State.IDLE
    private var sampleCount = 0
    private var minX = 1f
    private var maxX = 0f
    private var minY = 1f
    private var maxY = 0f

    fun start() {
        state = State.COLLECTING
        sampleCount = 0
        minX = 1f
        maxX = 0f
        minY = 1f
        maxY = 0f
    }

    fun addSample(x: Float, y: Float): Boolean {
        if (state != State.COLLECTING || !x.isFinite() || !y.isFinite()) return false
        if (x !in 0f..1f || y !in 0f..1f) return false
        sampleCount++
        minX = min(minX, x)
        maxX = max(maxX, x)
        minY = min(minY, y)
        maxY = max(maxY, y)
        state = if (sampleCount >= minimumSamples) State.READY else State.COLLECTING
        return true
    }

    fun complete(): PointerCalibrationProfile? {
        if (sampleCount < minimumSamples || state == State.IDLE) {
            state = State.INVALID
            return null
        }
        val profile = PointerCalibrationProfile(
            left = minX,
            right = maxX,
            top = minY,
            bottom = maxY,
            mirrorX = mirrorX
        )
        return profile.takeIf { it.isValid() } ?: run {
            state = State.INVALID
            null
        }
    }

    fun state(): State = state
    fun sampleCount(): Int = sampleCount

    fun reset() {
        state = State.IDLE
        sampleCount = 0
        minX = 1f
        maxX = 0f
        minY = 1f
        maxY = 0f
    }
}
