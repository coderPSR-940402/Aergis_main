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
    private var lastTimestampMs = 0L

    fun processFrame(handPosition: Point3D, timestampMs: Long): SwipeDirection {
        if (lastTimestampMs > 0L && timestampMs <= lastTimestampMs) {
            sampleWindow.clear()
            lastTimestampMs = timestampMs
            return SwipeDirection.NONE
        }
        lastTimestampMs = timestampMs

        sampleWindow.addLast(FrameSample(handPosition, timestampMs))
        val safeWindowSize = windowSize.coerceAtLeast(2)
        while (sampleWindow.size > safeWindowSize) {
            sampleWindow.removeFirst()
        }

        if (timestampMs < cooldownUntilMs) {
            val first = sampleWindow.firstOrNull()
            if (first != null) {
                val dx = handPosition.x - first.point.x
                val dy = handPosition.y - first.point.y
                val dt = (timestampMs - first.timestampMs) / 1000.0f
                val velocity = if (dt > 0.001f) sqrt(dx * dx + dy * dy) / dt else 0f
                if (velocity < returnVelocityThreshold) cooldownUntilMs = 0L
            }
            return SwipeDirection.NONE
        }

        if (sampleWindow.size < safeWindowSize) return SwipeDirection.NONE

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
            cooldownUntilMs = timestampMs + returnCooldownMs
            sampleWindow.clear()
        }
        return direction
    }

    fun reset() {
        sampleWindow.clear()
        cooldownUntilMs = 0L
        lastTimestampMs = 0L
    }
}
