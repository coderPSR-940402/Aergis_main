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
    private val capacity = windowSize.coerceAtLeast(2)
    private val xRing = FloatArray(capacity)
    private val yRing = FloatArray(capacity)
    private val zRing = FloatArray(capacity)
    private val timeRing = LongArray(capacity)

    private var head = 0
    private var count = 0
    private var cooldownUntilMs = 0L
    private var lastTimestampMs = 0L

    fun processFrame(handPosition: Point3D, timestampMs: Long): SwipeDirection {
        if (lastTimestampMs > 0L && timestampMs <= lastTimestampMs) {
            resetRing()
            lastTimestampMs = timestampMs
            return SwipeDirection.NONE
        }
        lastTimestampMs = timestampMs

        // Store sample in ring buffer
        xRing[head] = handPosition.x
        yRing[head] = handPosition.y
        zRing[head] = handPosition.z
        timeRing[head] = timestampMs

        head = (head + 1) % capacity
        if (count < capacity) {
            count++
        }

        if (timestampMs < cooldownUntilMs) {
            if (count > 0) {
                val oldestIdx = if (count < capacity) 0 else head
                val dx = handPosition.x - xRing[oldestIdx]
                val dy = handPosition.y - yRing[oldestIdx]
                val dt = (timestampMs - timeRing[oldestIdx]) / 1000.0f
                val velocity = if (dt > 0.001f) sqrt(dx * dx + dy * dy) / dt else 0f
                if (velocity < returnVelocityThreshold) cooldownUntilMs = 0L
            }
            return SwipeDirection.NONE
        }

        if (count < capacity) return SwipeDirection.NONE

        // oldest index is `head` when buffer is full
        val oldestIdx = head
        val latestIdx = (head + capacity - 1) % capacity

        val dt = (timeRing[latestIdx] - timeRing[oldestIdx]) / 1000.0f
        if (dt <= 0.001f) return SwipeDirection.NONE

        val dx = xRing[latestIdx] - xRing[oldestIdx]
        val dy = yRing[latestIdx] - yRing[oldestIdx]
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
            resetRing()
        }
        return direction
    }

    private fun resetRing() {
        head = 0
        count = 0
    }

    fun reset() {
        resetRing()
        cooldownUntilMs = 0L
        lastTimestampMs = 0L
    }
}
