package com.airgesture.control.pointer

import com.airgesture.control.filtering.Point3D
import kotlin.math.atan2
import kotlin.math.sqrt

enum class SwipeDirection {
    NONE, UP, DOWN, LEFT, RIGHT
}

class SwipeGestureEngine(
    private val windowSize: Int = 5,
    private val minVelocityScreensPerSec: Float = 0.8f,
    private val returnCooldownMs: Long = 300L,
    private val returnVelocityThreshold: Float = 0.15f
) {
    private data class FrameSample(val point: Point3D, val timestampMs: Long)

    private val sampleWindow = ArrayDeque<FrameSample>()
    private var cooldownUntilMs = 0L

    fun processFrame(handPosition: Point3D, timestampMs: Long): SwipeDirection {
        sampleWindow.addLast(FrameSample(handPosition, timestampMs))
        while (sampleWindow.size > windowSize.coerceAtLeast(2)) {
            sampleWindow.removeFirst()
        }

        if (timestampMs < cooldownUntilMs) {
            val oldest = sampleWindow.firstOrNull()
            if (oldest != null) {
                val dx = handPosition.x - oldest.point.x
                val dy = handPosition.y - oldest.point.y
                val dt = (timestampMs - oldest.timestampMs) / 1000.0f
                val velocity = if (dt > 0.001f) sqrt(dx * dx + dy * dy) / dt else 0.0f
                if (velocity < returnVelocityThreshold) {
                    cooldownUntilMs = 0L
                }
            }
            return SwipeDirection.NONE
        }

        if (sampleWindow.size < windowSize.coerceAtLeast(2)) return SwipeDirection.NONE

        val oldest = sampleWindow.first()
        val latest = sampleWindow.last()
        val dt = (latest.timestampMs - oldest.timestampMs) / 1000.0f
        if (dt <= 0.001f) return SwipeDirection.NONE

        val dx = latest.point.x - oldest.point.x
        val dy = latest.point.y - oldest.point.y
        val displacement = sqrt(dx * dx + dy * dy)
        val velocity = displacement / dt
        if (velocity < minVelocityScreensPerSec) return SwipeDirection.NONE

        val angleDeg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
        val direction = when {
            angleDeg >= -22.5 && angleDeg <= 22.5 -> SwipeDirection.RIGHT
            angleDeg >= 67.5 && angleDeg <= 112.5 -> SwipeDirection.UP
            angleDeg >= 157.5 || angleDeg <= -157.5 -> SwipeDirection.LEFT
            angleDeg >= -112.5 && angleDeg <= -67.5 -> SwipeDirection.DOWN
            else -> SwipeDirection.NONE
        }

        if (direction != SwipeDirection.NONE) {
            cooldownUntilMs = timestampMs + returnCooldownMs.coerceAtLeast(0L)
            sampleWindow.clear()
        }

        return direction
    }

    fun reset() {
        sampleWindow.clear()
        cooldownUntilMs = 0L
    }
}
