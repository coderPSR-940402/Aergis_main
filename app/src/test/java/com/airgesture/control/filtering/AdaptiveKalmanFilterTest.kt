package com.airgesture.control.filtering

import kotlin.math.sqrt

/**
 * Adaptive Kalman filter for 2D pointer tracking with motion-responsive state estimation.
 *
 * Uses:
 * - Adaptive process and measurement noise covariance matrices
 * - Velocity-aware motion model
 * - Outlier detection and automatic covariance reset
 * - Low-latency response to rapid user movements
 */
class AdaptiveKalmanFilter(
    private val initialProcessNoise: Float = 0.008f,
    private val initialMeasurementNoise: Float = 0.002f,
    private val velocitySensitivity: Float = 0.15f
) {
    // State: [x, y, vx, vy]
    private var stateX = 0f
    private var stateY = 0f
    private var velocityX = 0f
    private var velocityY = 0f

    private var pXX = 1f
    private var pXVx = 0f
    private var pYVy = 0f
    private var pVxVx = 0.1f
    private var pVyVy = 0.1f
    private var pYY = 1f
    private var pXVy = 0f
    private var pYVx = 0f
    private var pVxVy = 0f

    private var processNoise = initialProcessNoise
    private var measurementNoise = initialMeasurementNoise
    private var lastTimestampMs = -1L
    private var outlierCount = 0

    fun filter(measuredX: Float, measuredY: Float, timestampMs: Long): Point3D {
        if (lastTimestampMs < 0) {
            lastTimestampMs = timestampMs
            stateX = measuredX
            stateY = measuredY
            return Point3D(measuredX, measuredY, 0f)
        }

        val dtMs = timestampMs - lastTimestampMs
        if (dtMs < 0 || dtMs > 500) {
            reset(measuredX, measuredY, timestampMs)
            return Point3D(measuredX, measuredY, 0f)
        }

        lastTimestampMs = timestampMs
        val dt = dtMs / 1000f

        predict(dt)

        val innovation = detectOutlier(measuredX, measuredY)
        if (innovation > 3.0f) {
            outlierCount++
            if (outlierCount > 2) {
                reset(measuredX, measuredY, timestampMs)
                return Point3D(measuredX, measuredY, 0f)
            }
            return Point3D(stateX, stateY, 0f)
        }
        outlierCount = 0

        update(measuredX, measuredY)
        adaptNoiseParameters()

        return Point3D(stateX, stateY, 0f)
    }

    private fun predict(dt: Float) {
        stateX += velocityX * dt
        stateY += velocityY * dt

        val pXXNew = pXX + 2f * pXVx * dt + pVxVx * dt * dt + processNoise
        val pYYNew = pYY + 2f * pYVy * dt + pVyVy * dt * dt + processNoise
        val pXVxNew = pXVx + pVxVx * dt
        val pYVyNew = pYVy + pVyVy * dt

        pXX = pXXNew
        pYY = pYYNew
        pXVx = pXVxNew
        pYVy = pYVyNew
        pVxVx += processNoise
        pVyVy += processNoise
    }

    private fun detectOutlier(measuredX: Float, measuredY: Float): Float {
        val innovationX = measuredX - stateX
        val innovationY = measuredY - stateY
        val sx = pXX + measurementNoise
        val sy = pYY + measurementNoise
        if (sx < 0.0001f || sy < 0.0001f) return 0f
        return ((innovationX * innovationX) / sx) + ((innovationY * innovationY) / sy)
    }

    private fun update(measuredX: Float, measuredY: Float) {
        val innovationX = measuredX - stateX
        val innovationY = measuredY - stateY
        val sx = pXX + measurementNoise
        val sy = pYY + measurementNoise
        if (sx < 0.0001f || sy < 0.0001f) return

        val kx = pXX / sx
        val ky = pYY / sy
        val kvx = pXVx / sx
        val kvy = pYVy / sy

        stateX += kx * innovationX
        stateY += ky * innovationY
        velocityX += kvx * innovationX
        velocityY += kvy * innovationY

        pXX = (1f - kx) * pXX
        pYY = (1f - ky) * pYY
        pXVx = (1f - kx) * pXVx
        pYVy = (1f - ky) * pYVy
    }

    private fun adaptNoiseParameters() {
        val currentVelocity = sqrt(velocityX * velocityX + velocityY * velocityY)
        processNoise = initialProcessNoise + (currentVelocity * velocitySensitivity).coerceAtMost(0.05f)
        measurementNoise = if (currentVelocity < 0.1f) initialMeasurementNoise * 0.5f else initialMeasurementNoise
    }

    fun reset(x: Float = 0f, y: Float = 0f, timestampMs: Long = 0L) {
        stateX = x
        stateY = y
        velocityX = 0f
        velocityY = 0f
        pXX = 1f
        pYY = 1f
        pXVx = 0f
        pYVy = 0f
        pVxVx = 0.1f
        pVyVy = 0.1f
        lastTimestampMs = timestampMs
        outlierCount = 0
        processNoise = initialProcessNoise
        measurementNoise = initialMeasurementNoise
    }
}
