package com.airgesture.control

/** Converts MediaPipe normalized coordinates into screen-normalized coordinates. */
object PointerCoordinateMapper {
    private const val ACTIVE_LEFT = 0.02f
    private const val ACTIVE_RIGHT = 0.98f
    private const val ACTIVE_TOP = 0.02f
    private const val ACTIVE_BOTTOM = 0.98f

    fun map(x: Float, y: Float): Point {
        // MediaPipe normalized coordinates are already expressed in the upright
        // coordinate space produced by the CameraX rotation options. For a
        // front-facing camera, mirror horizontal movement only; never swap axes.
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
        return Point(normalizedX, normalizedY)
    }

    data class Point(val x: Float, val y: Float)
}
