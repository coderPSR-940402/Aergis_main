package com.airgesture.control

import kotlin.math.abs
import kotlin.math.pow

/** Converts MediaPipe normalized coordinates into screen-normalized coordinates. */
object PointerCoordinateMapper {
    private const val ACTIVE_LEFT = 0.02f
    private const val ACTIVE_RIGHT = 0.98f
    private const val ACTIVE_TOP = 0.02f
    private const val ACTIVE_BOTTOM = 0.98f

    fun map(x: Float, y: Float): Point {
        // The recognition engine converts image-space landmarks to upright coordinates.
        // Apply front-camera horizontal mirroring once, after that rotation.
        val mirroredX = 1f - x.coerceIn(0f, 1f)
        val normalizedX =
            ((mirroredX - ACTIVE_LEFT) / (ACTIVE_RIGHT - ACTIVE_LEFT)).coerceIn(0f, 1f)
        val normalizedY =
            ((y.coerceIn(0f, 1f) - ACTIVE_TOP) / (ACTIVE_BOTTOM - ACTIVE_TOP)).coerceIn(0f, 1f)
        return Point(normalizedX, normalizedY)
    }

    /** Maps through a validated calibration profile without changing the legacy path. */
    fun map(x: Float, y: Float, profile: PointerCalibrationProfile): Point {
        val calibration = profile.validatedOrDefault()
        val sourceX = x.coerceIn(0f, 1f)
        val sourceY = y.coerceIn(0f, 1f)
        val mappedX = if (calibration.mirrorX) 1f - sourceX else sourceX
        val normalizedX =
            ((mappedX - calibration.left) / (calibration.right - calibration.left))
                .coerceIn(0f, 1f)
        val normalizedY =
            ((sourceY - calibration.top) / (calibration.bottom - calibration.top))
                .coerceIn(0f, 1f)
        return Point(response(normalizedX, calibration.curveX), response(normalizedY, calibration.curveY))
    }

    private fun response(value: Float, curve: Float): Float {
        if (curve == 1f) return value
        val signed = value * 2f - 1f
        val magnitude = abs(signed).toDouble().pow(curve.toDouble()).toFloat()
        return (0.5f + (if (signed < 0f) -magnitude else magnitude) * 0.5f).coerceIn(0f, 1f)
    }

    data class Point(val x: Float, val y: Float)
}
