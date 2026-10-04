package com.airgesture.control.pointer

import com.airgesture.control.filtering.Point3D
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Deliberate swipe detector: a stroke must begin outside the center gate, cross it,
 * remain axis-dominant, and satisfy speed/displacement limits. It is intentionally
 * independent from accessibility dispatch so it can be replayed and benchmarked.
 */
class CenterGateSwipeDetector(
    private val centerX: Float = 0.5f,
    private val centerY: Float = 0.5f,
    private val gateHalfWidth: Float = 0.10f,
    private val minDisplacement: Float = 0.16f,
    private val minSpeedPerSecond: Float = 0.65f,
    private val maxSpeedPerSecond: Float = 4.5f,
    private val axisDominance: Float = 1.35f,
    private val cooldownMs: Long = 350L,
    private val windowMs: Long = 900L
) {
    data class Telemetry(val state: State, val confidence: Float, val speedPerSecond: Float)
    enum class State { IDLE, TRACKING, COOLDOWN }

    private data class Sample(val point: Point3D, val timestampMs: Long)
    private val samples = ArrayDeque<Sample>()
    private var cooldownUntilMs = 0L
    private var lastTimestampMs = Long.MIN_VALUE
    var telemetry: Telemetry = Telemetry(State.IDLE, 0f, 0f)
        private set

    fun update(point: Point3D, timestampMs: Long): SwipeDirection {
        if (timestampMs <= lastTimestampMs) {
            reset()
            lastTimestampMs = timestampMs
            return SwipeDirection.NONE
        }
        lastTimestampMs = timestampMs
        if (timestampMs < cooldownUntilMs) {
            telemetry = Telemetry(State.COOLDOWN, 0f, 0f)
            return SwipeDirection.NONE
        }
        samples.addLast(Sample(point, timestampMs))
        while (samples.isNotEmpty() && timestampMs - samples.first().timestampMs > windowMs) samples.removeFirst()
        if (samples.size < 2) {
            telemetry = Telemetry(State.TRACKING, 0f, 0f)
            return SwipeDirection.NONE
        }
        val first = samples.first()
        val dx = point.x - first.point.x
        val dy = point.y - first.point.y
        val elapsed = (timestampMs - first.timestampMs) / 1000f
        val displacement = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val speed = if (elapsed > 0f) displacement / elapsed else 0f
        val crossedHorizontal = crossedGate(first.point.x, point.x, centerX)
        val crossedVertical = crossedGate(first.point.y, point.y, centerY)
        val horizontal = abs(dx) >= abs(dy) * axisDominance
        val vertical = abs(dy) >= abs(dx) * axisDominance
        val direction = when {
            displacement < minDisplacement || speed !in minSpeedPerSecond..maxSpeedPerSecond -> SwipeDirection.NONE
            horizontal && crossedHorizontal -> if (dx > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
            vertical && crossedVertical -> if (dy < 0f) SwipeDirection.UP else SwipeDirection.DOWN
            else -> SwipeDirection.NONE
        }
        val confidence = if (displacement == 0f) 0f else (displacement / minDisplacement).coerceIn(0f, 1f) *
            (if (horizontal || vertical) 1f else 0.4f)
        telemetry = Telemetry(if (direction == SwipeDirection.NONE) State.TRACKING else State.COOLDOWN, confidence, speed)
        if (direction != SwipeDirection.NONE) {
            cooldownUntilMs = timestampMs + cooldownMs.coerceAtLeast(0L)
            samples.clear()
        }
        return direction
    }

    fun reset() {
        samples.clear()
        cooldownUntilMs = 0L
        lastTimestampMs = Long.MIN_VALUE
        telemetry = Telemetry(State.IDLE, 0f, 0f)
    }

    private fun crossedGate(start: Float, end: Float, center: Float): Boolean =
        (start <= center - gateHalfWidth && end >= center + gateHalfWidth) ||
            (start >= center + gateHalfWidth && end <= center - gateHalfWidth)
}
