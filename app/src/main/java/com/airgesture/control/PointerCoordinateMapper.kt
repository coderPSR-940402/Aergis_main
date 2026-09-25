package com.airgesture.control

/** Converts MediaPipe front-camera normalized coordinates into screen-normalized coordinates. */
object PointerCoordinateMapper {
    private const val ACTIVE_LEFT = 0.02f
    private const val ACTIVE_RIGHT = 0.98f
    private const val ACTIVE_TOP = 0.02f
    private const val ACTIVE_BOTTOM = 0.98f

    fun map(x: Float, y: Float): Point {
        val mirroredX = 1f - x.coerceIn(0f, 1f)
        return Point(
            ((mirroredX - ACTIVE_LEFT) / (ACTIVE_RIGHT - ACTIVE_LEFT)).coerceIn(0f, 1f),
            ((y.coerceIn(0f, 1f) - ACTIVE_TOP) / (ACTIVE_BOTTOM - ACTIVE_TOP)).coerceIn(0f, 1f)
        )
    }

    data class Point(val x: Float, val y: Float)
}
