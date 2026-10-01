package com.airgesture.control.pointer

import com.airgesture.control.filtering.Point3D
import kotlin.math.atan2
import kotlin.math.sqrt

enum class SwipeDirection {
    NONE, UP, DOWN, LEFT, RIGHT
}

/** Detects directional motion from a bounded, time-ordered sample window. */
class SwipeGestureEngine(
    windowSize: Int = 5,
    private val minVelocityScreensPerSec: Float = 0.8f,
    private val returnCooldownMs: Long = 300L,
    private val returnVelocityThreshold: Float = 0.15f,
    private val minDisplacement: Float = 0.08f
) {
    private val capacity = windowSize.coerceAtLeast(2)
    private val xRing = FloatArray(capacity)
    private val yRing = FloatArray(capacity)
    private val timeRing = LongArray(capacity)

    private var head = 0
    private var count = 0
    private var cooldownUntilMs = 0L
    private var lastTimestampMs = Long.MIN_VALUE

    fun processFrame(handPosition: Point3D, timestampMs: Long): SwipeDirection {
        if (timestampMs <= lastTimestampMs) {
            resetRing()
            lastTimestampMs = timestampMs
            return SwipeDirection.NONE
        }
        lastTimestampMs = timestampMs

        xRing[head] = handPosition.x
        yRing[head] = handPosition.y
        timeRing[head] = timestampMs
        head = (head + 1) % capacity
        if (count < capacity) count++

        if (timestampMs < cooldownUntilMs) {
            if (isStationarySinceOldest(handPosition, timestampMs)) {
                cooldownUntilMs = 0L
                resetRing()
            }
            return SwipeDirection.NONE
        }

        if (count < capacity) return SwipeDirection.NONE

        val oldestIndex = head
        val latestIndex = (head + capacity - 1) % capacity
        val dtSeconds = (timeRing[latestIndex] - timeRing[oldestIndex]) / 1000f
        if (dtSeconds <= 0.001f) return SwipeDirection.NONE

        val dx = xRing[latestIndex] - xRing[oldestIndex]
        val dy = yRing[latestIndex] - yRing[oldestIndex]
        val displacement = sqrt(dx * dx + dy * dy)
        if (displacement < minDisplacement || displacement / dtSeconds < minVelocityScreensPerSec) {
            return SwipeDirection.NONE
        }

        val angle = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
        val direction = when {
            angle in -22.5..22.5 -> SwipeDirection.RIGHT
            angle in 67.5..112.5 -> SwipeDirection.UP
            angle >= 157.5 || angle <= -157.5 -> SwipeDirection.LEFT
            angle in -112.5..-67.5 -> SwipeDirection.DOWN
            else -> SwipeDirection.NONE
        }

        if (direction != SwipeDirection.NONE) {
            cooldownUntilMs = timestampMs + returnCooldownMs.coerceAtLeast(0L)
            resetRing()
        }
        return direction
    }

    private fun isStationarySinceOldest(position: Point3D, timestampMs: Long): Boolean {
        if (count == 0) return true
        val oldestIndex = if (count < capacity) 0 else head
        val dt = (timestampMs - timeRing[oldestIndex]) / 1000f
        if (dt <= 0.001f) return true
        val dx = position.x - xRing[oldestIndex]
        val dy = position.y - yRing[oldestIndex]
        return sqrt(dx * dx + dy * dy) / dt < returnVelocityThreshold
    }

    private fun resetRing() {
        head = 0
        count = 0
    }

    fun reset() {
        resetRing()
        cooldownUntilMs = 0L
        lastTimestampMs = Long.MIN_VALUE
    }
}
