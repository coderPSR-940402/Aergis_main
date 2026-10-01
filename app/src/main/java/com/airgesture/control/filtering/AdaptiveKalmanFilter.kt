package com.airgesture.control.filtering

import kotlin.math.sqrt

/**
 * Adaptive Kalman filter for 2D pointer tracking with motion-responsive state estimation.
 *
 * Uses:
 * - Adaptive process and measurement noise covariance matrices
 * - Velocity-aware motion model
 * - Outlier detection and automatic covariance reset
 * - Zero-latency response to rapid user movements
 */
class AdaptiveKalmanFilter(
    private val initialProcessNoise: Float = 0.008f,  // Motion model uncertainty
    private val initialMeasurementNoise: Float = 0.002f, // Sensor uncertainty
    private val velocitySensitivity: Float = 0.15f  // How responsive to user motion
) {
    // State: [x, y, vx, vy]
    private var stateX = 0f
    private var stateY = 0f
    private var velocityX = 0f
    private var velocityY = 0f

    // Covariance matrix (4x4, stored as symmetric)
    private var pXX = 1f
    private var pXVx = 0f
    private var pXVy = 0f
    private var pYY = 1f
    private var pYVx = 0f
    private var pYVy = 0f
    private var pVxVx = 0.1f
    private var pVxVy = 0f
    private var pVyVy = 0.1f

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

        // Prediction step: update state with velocity model
        predict(dt)

        // Detect outliers using Mahalanobis distance
        val innovation = detectOutlier(measuredX, measuredY)
        if (innovation > 3.0f) {
            outlierCount++
            if (outlierCount > 2) {
                // Reset on persistent outliers
                reset(measuredX, measuredY, timestampMs)
                return Point3D(measuredX, measuredY, 0f)
            }
            // Skip measurement update
            return Point3D(stateX, stateY, 0f)
        }
        outlierCount = 0

        // Update step: fuse measurement
        update(measuredX, measuredY, dt)

        // Adapt covariance based on motion velocity
        adaptNoiseParameters(dt)

        return Point3D(stateX, stateY, 0f)
    }

    private fun predict(dt: Float) {
        // State transition with velocity
        val newStateX = stateX + velocityX * dt
        val newStateY = stateY + velocityY * dt

        // Update covariance: P = F * P * F^T + Q
        val pXX_new = pXX + 2f * pXVx * dt + pVxVx * dt * dt + processNoise
        val pYY_new = pYY + 2f * pYVy * dt + pVyVy * dt * dt + processNoise
        val pXVx_new = pXVx + pVxVx * dt
        val pYVy_new = pYVy + pVyVy * dt
        val pVxVx_new = pVxVx + processNoise
        val pVyVy_new = pVyVy + processNoise

        stateX = newStateX
        stateY = newStateY
        pXX = pXX_new
        pYY = pYY_new
        pXVx = pXVx_new
        pYVy = pYVy_new
        pVxVx = pVxVx_new
        pVyVy = pVyVy_new
    }

    private fun detectOutlier(measuredX: Float, measuredY: Float): Float {
        val innovationX = measuredX - stateX
        val innovationY = measuredY - stateY

        // Mahalanobis distance: innovation^T * S^-1 * innovation
        val sx = pXX + measurementNoise
        val sy = pYY + measurementNoise

        if (sx < 0.0001f || sy < 0.0001f) return 0f

        return (innovationX * innovationX / sx) + (innovationY * innovationY / sy)
    }

    private fun update(measuredX: Float, measuredY: Float, dt: Float) {
        val innovationX = measuredX - stateX
        val innovationY = measuredY - stateY

        val sx = pXX + measurementNoise
        val sy = pYY + measurementNoise

        if (sx < 0.0001f || sy < 0.0001f) return

        // Kalman gain: K = P * H^T * (H * P * H^T + R)^-1
        val kx = pXX / sx
        val ky = pYY / sy
        val kvx_x = pXVx / sx
        val kvy_y = pYVy / sy

        // State update
        stateX += kx * innovationX
        stateY += ky * innovationY
        velocityX += kvx_x * innovationX
        velocityY += kvy_y * innovationY

        // Covariance update: P = (I - K*H)*P
        pXX = (1f - kx) * pXX
        pYY = (1f - ky) * pYY
        pXVx = (1f - kx) * pXVx
        pYVy = (1f - ky) * pYVy
    }

    private fun adaptNoiseParameters(dt: Float) {
        val currentVelocity = sqrt(velocityX * velocityX + velocityY * velocityY)
        
        // Increase process noise during rapid motion to allow velocity changes
        processNoise = initialProcessNoise + (currentVelocity * velocitySensitivity).coerceAtMost(0.05f)
        
        // Reduce measurement noise during smooth motion (more trust in sensor)
        val smoothness = if (currentVelocity < 0.1f) 0.5f else 1.0f
        measurementNoise = initialMeasurementNoise * smoothness
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
    }
}
