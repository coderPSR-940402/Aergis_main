package com.airgesture.control

import kotlin.math.abs

/** A persisted, validated pointer transform learned from the calibration workspace. */
data class PointerCalibration(
    val version: Int = CURRENT_VERSION,
    val left: Float = 0.02f,
    val top: Float = 0.02f,
    val right: Float = 0.98f,
    val bottom: Float = 0.98f,
    val xGain: Float = 1f,
    val yGain: Float = 1f,
    val xOffset: Float = 0f,
    val yOffset: Float = 0f
) {
    init {
        require(version == CURRENT_VERSION) { "Unsupported calibration version: $version" }
        require(left in 0f..0.45f && top in 0f..0.45f)
        require(right in 0.55f..1f && bottom in 0.55f..1f)
        require(right > left && bottom > top)
        require(xGain in 0.25f..4f && yGain in 0.25f..4f)
        require(xOffset in -1f..1f && yOffset in -1f..1f)
    }

    fun map(x: Float, y: Float, mirrorX: Boolean = true): PointerCoordinateMapper.Point {
        val sourceX = if (mirrorX) 1f - x else x
        val normalizedX = ((sourceX - left) / (right - left) * xGain + xOffset)
            .coerceIn(0f, 1f)
        val normalizedY = ((y - top) / (bottom - top) * yGain + yOffset)
            .coerceIn(0f, 1f)
        return PointerCoordinateMapper.Point(normalizedX, normalizedY)
    }

    companion object {
        const val CURRENT_VERSION = 2
        val DEFAULT = PointerCalibration()
    }
}
