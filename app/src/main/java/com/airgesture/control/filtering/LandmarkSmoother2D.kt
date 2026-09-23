package com.airgesture.control.filtering

import android.os.SystemClock
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/** 2D 1 Euro smoothing for normalized MediaPipe landmark coordinates. */
class LandmarkSmoother2D(
    minCutoff: Float = 1.0f,
    beta: Float = 0.01f,
    dCutoff: Float = 1.0f
) {
    private val filterX = OneEuroFilter(minCutoff, beta, dCutoff)
    private val filterY = OneEuroFilter(minCutoff, beta, dCutoff)

    fun filter(
        landmark: NormalizedLandmark,
        timestampMs: Long = SystemClock.uptimeMillis()
    ): Point2D = Point2D(
        filterX.filter(landmark.x(), timestampMs),
        filterY.filter(landmark.y(), timestampMs)
    )

    fun reset() {
        filterX.reset()
        filterY.reset()
    }

    data class Point2D(val x: Float, val y: Float)
}
