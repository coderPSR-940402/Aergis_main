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

    /** Applies a validated user calibration; the default mapping remains unchanged. */
    fun map(x: Float, y: Float, calibration: PointerCalibration): Point =
        calibration.map(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))

    data class Point(val x: Float, val y: Float)
}
