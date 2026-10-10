package com.airgesture.control

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Causal speed-adaptive low-pass filtering in calibrated screen coordinates.
 * Slow aim receives stronger smoothing; deliberate travel opens the cutoff.
 * A large isolated measurement needs a second consistent observation. No extrapolation.
 */
internal class PrecisionPointerFilter {
    private var output: PointerCoordinateMapper.Point? = null
    private var measurement: PointerCoordinateMapper.Point? = null
    private var pendingJump: PointerCoordinateMapper.Point? = null
    private var lastTimestamp: Long? = null
    private data class Sample(val point: PointerCoordinateMapper.Point, val timestampMs: Long)
    private val recent = ArrayDeque<Sample>()
    private var velocityX = 0f
    private var velocityY = 0f
    var measurementTrusted: Boolean = false
        private set

    fun update(x: Float, y: Float, timestampMs: Long): PointerCoordinateMapper.Point {
        measurementTrusted = false
        val previous = output
        if (!x.isFinite() || !y.isFinite() || timestampMs < 0L ||
            (lastTimestamp != null && timestampMs <= lastTimestamp!!)) {
            return previous ?: PointerCoordinateMapper.Point(.5f, .5f)
        }
        val measured = PointerCoordinateMapper.Point(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val elapsed = lastTimestamp?.let { timestampMs - it }
        lastTimestamp = timestampMs
        if (previous == null || elapsed == null || elapsed > 250L) {
            velocityX = 0f; velocityY = 0f; pendingJump = null
            recent.clear(); recent.addLast(Sample(measured, timestampMs))
            measurement = measured; output = measured; measurementTrusted = true
            return measured
        }
        val dt = elapsed / 1000f
        val raw = measurement ?: measured
        val dx = measured.x - raw.x
        val dy = measured.y - raw.y
        val speed = hypot(velocityX, velocityY)
        val jumpLimit = .12f + speed * dt * 2f
        if (hypot(dx, dy) > jumpLimit) {
            val pending = pendingJump
            val closeToPending = pending != null && hypot(measured.x - pending.x, measured.y - pending.y) <= maxOf(.08f, 6f * dt)
            val consistentTravel = pending != null &&
                (pending.x - raw.x) * (measured.x - pending.x) + (pending.y - raw.y) * (measured.y - pending.y) > 0f &&
                hypot(measured.x - pending.x, measured.y - pending.y) <= hypot(pending.x - raw.x, pending.y - raw.y) * 2f
            if (!closeToPending && !consistentTravel) {
                pendingJump = measured
                return previous
            }
        }
        pendingJump = null
        // Derivative is taken from consecutive measurements, never from the lagging output.
        val derivativeAlpha = alpha(dt, 2f)
        velocityX += derivativeAlpha * (dx / dt - velocityX)
        velocityY += derivativeAlpha * (dy / dt - velocityY)
        var cutoff = .65f + 20f * (hypot(velocityX, velocityY) - .06f).coerceAtLeast(0f)
        recent.addLast(Sample(measured, timestampMs))
        while (recent.size > 16 || (recent.size > 4 && timestampMs - recent.first().timestampMs > 200L)) recent.removeFirst()
        if (recent.size >= 4) {
            var path = 0f
            for (i in 1 until recent.size) path += hypot(recent[i].point.x - recent[i - 1].point.x, recent[i].point.y - recent[i - 1].point.y)
            val displacement = hypot(measured.x - recent.first().point.x, measured.y - recent.first().point.y)
            // A consistent small movement is intentional fine aim, not stationary noise.
            // This opens the cutoff without adding prediction or a sticky dead zone.
            if (displacement >= .003f && displacement >= path * .85f) cutoff = maxOf(cutoff, 2.5f)
        }
        val gain = alpha(dt, cutoff)
        val result = PointerCoordinateMapper.Point(
            previous.x + gain * (measured.x - previous.x),
            previous.y + gain * (measured.y - previous.y)
        )
        measurement = measured; output = result; measurementTrusted = true
        return result
    }

    fun reset() {
        output = null; measurement = null; pendingJump = null; lastTimestamp = null
        velocityX = 0f; velocityY = 0f; measurementTrusted = false; recent.clear()
    }

    private fun alpha(dt: Float, cutoff: Float): Float =
        (1.0 - exp(-2.0 * PI * cutoff * dt)).toFloat().coerceIn(0f, 1f)
}
