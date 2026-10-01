package com.airgesture.control.filtering

import kotlin.math.hypot

/**
 * Robust, low-latency constant-velocity tracker for normalized pointer coordinates.
 *
 * The public name is retained for compatibility with the existing runtime model. The
 * previous implementation used an incomplete 4x4 covariance update and a fixed
 * Mahalanobis gate, which could classify legitimate fast motion as an outlier and
 * then freeze the pointer. This implementation uses an alpha-beta update with a
 * speed-adaptive gain and a bounded innovation gate: stable motion is smooth, while
 * a genuine fast move is followed on the very next frame.
 */
class AdaptiveKalmanFilter(
    private val positionGain: Float = 0.82f,
    private val velocityGain: Float = 0.34f,
    private val maxInnovation: Float = 0.24f
) {
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var lastTimestampMs = -1L
    private var initialized = false

    fun filter(measuredX: Float, measuredY: Float, timestampMs: Long): Point3D {
        val mx = measuredX.coerceIn(0f, 1f)
        val my = measuredY.coerceIn(0f, 1f)
        if (!initialized || timestampMs <= lastTimestampMs || timestampMs - lastTimestampMs > MAX_GAP_MS) {
            reset(mx, my, timestampMs)
            return Point3D(mx, my, 0f)
        }

        val dt = ((timestampMs - lastTimestampMs) / 1000f).coerceIn(MIN_DT, MAX_DT)
        lastTimestampMs = timestampMs
        val predictedX = (x + vx * dt).coerceIn(0f, 1f)
        val predictedY = (y + vy * dt).coerceIn(0f, 1f)
        val errorX = mx - predictedX
        val errorY = my - predictedY
        val errorMagnitude = hypot(errorX.toDouble(), errorY.toDouble()).toFloat()

        // A single impossible landmark jump is down-weighted, never allowed to freeze
        // the tracker. The gate expands slightly for an actual high-speed move.
        val measuredSpeed = errorMagnitude / dt
        val gate = (maxInnovation + measuredSpeed * dt * 0.20f).coerceAtMost(0.46f)
        val isOutlier = errorMagnitude > gate
        val robustScale = if (isOutlier) gate / errorMagnitude else 1f
        val correctionX = errorX * robustScale
        val correctionY = errorY * robustScale
        val speedFactor = (measuredSpeed / FAST_MOTION_SPEED).coerceIn(0f, 1f)
        val alpha = (positionGain + speedFactor * (1f - positionGain)).coerceIn(0.55f, 0.98f)
        val beta = (velocityGain + speedFactor * 0.30f).coerceIn(0.20f, 0.72f)

        x = (predictedX + alpha * correctionX).coerceIn(0f, 1f)
        y = (predictedY + alpha * correctionY).coerceIn(0f, 1f)
        if (isOutlier) {
            // Do not learn velocity from a rejected landmark jump; otherwise the next
            // valid frame would be overshot by the spike's artificial momentum.
            vx *= OUTLIER_VELOCITY_DAMPING
            vy *= OUTLIER_VELOCITY_DAMPING
        } else {
            vx = ((vx + beta * correctionX / dt) * VELOCITY_DAMPING).coerceIn(-MAX_SPEED, MAX_SPEED)
            vy = ((vy + beta * correctionY / dt) * VELOCITY_DAMPING).coerceIn(-MAX_SPEED, MAX_SPEED)
        }
        return Point3D(x, y, 0f)
    }

    fun reset(x: Float = 0f, y: Float = 0f, timestampMs: Long = 0L) {
        this.x = x.coerceIn(0f, 1f)
        this.y = y.coerceIn(0f, 1f)
        vx = 0f
        vy = 0f
        lastTimestampMs = timestampMs
        initialized = timestampMs >= 0L
    }

    companion object {
        private const val MAX_GAP_MS = 250L
        private const val MIN_DT = 1f / 120f
        private const val MAX_DT = 1f / 15f
        private const val FAST_MOTION_SPEED = 1.8f
        private const val MAX_SPEED = 8f
        private const val VELOCITY_DAMPING = 0.92f
        private const val OUTLIER_VELOCITY_DAMPING = 0.50f
    }
}
