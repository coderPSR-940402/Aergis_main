package com.airgesture.control

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.abs

internal enum class PrecisionRejection { NONE, INVALID_OR_OUT_OF_ORDER, JUMP_HELD }

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
    private var lastAcceptedTimestamp: Long? = null
    private data class Sample(val point: PointerCoordinateMapper.Point, val timestampMs: Long)
    private val recent = ArrayDeque<Sample>()
    private var velocityX = 0f
    private var velocityY = 0f
    var measurementTrusted: Boolean = false
        private set

    /** Why the last update was not trusted, with the inputs of the jump gate (screen-normalised units). */
    var lastRejection: PrecisionRejection = PrecisionRejection.NONE
        private set
    var lastJumpDistance: Float = 0f
        private set
    var lastJumpLimit: Float = 0f
        private set

    fun update(x: Float, y: Float, timestampMs: Long): PointerCoordinateMapper.Point {
        measurementTrusted = false
        lastRejection = PrecisionRejection.NONE
        val previous = output
        if (!x.isFinite() || !y.isFinite() || timestampMs < 0L ||
            (lastTimestamp != null && timestampMs <= lastTimestamp!!)) {
            lastRejection = PrecisionRejection.INVALID_OR_OUT_OF_ORDER
            return previous ?: PointerCoordinateMapper.Point(.5f, .5f)
        }
        val measured = PointerCoordinateMapper.Point(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val elapsed = lastAcceptedTimestamp?.let { timestampMs - it }
        lastTimestamp = timestampMs
        if (previous == null || elapsed == null || elapsed > 500L) {
            velocityX = 0f; velocityY = 0f; pendingJump = null
            recent.clear(); recent.addLast(Sample(measured, timestampMs))
            measurement = measured; output = measured; measurementTrusted = true
            lastAcceptedTimestamp = timestampMs
            return measured
        }
        // Rejected observations do not advance the measurement clock or distort velocity.
        val dt = elapsed / 1000f
        val raw = measurement ?: measured
        val dx = measured.x - raw.x
        val dy = measured.y - raw.y
        val speed = hypot(velocityX, velocityY)
        // The gate must be a physical-speed limit, not a per-frame distance: at a low or uneven
        // result cadence a normal fast finger travels far between results. Allow the larger of the
        // measured-speed extrapolation and a plausible maximum fingertip speed over the real
        // (camera-timestamp) interval.
        val jumpLimit = JUMP_BASE + speed * dt * 2f + MAX_PLAUSIBLE_SPEED * dt
        lastJumpDistance = hypot(dx, dy)
        lastJumpLimit = jumpLimit
        if (lastJumpDistance > jumpLimit) {
            val pending = pendingJump
            val closeToPending = pending != null && hypot(measured.x - pending.x, measured.y - pending.y) <= maxOf(.08f, 6f * dt)
            val consistentTravel = pending != null &&
                (pending.x - raw.x) * (measured.x - pending.x) + (pending.y - raw.y) * (measured.y - pending.y) > 0f &&
                hypot(measured.x - pending.x, measured.y - pending.y) <= hypot(pending.x - raw.x, pending.y - raw.y) * 2f
            if (!closeToPending && !consistentTravel) {
                pendingJump = measured
                lastRejection = PrecisionRejection.JUMP_HELD
                return previous
            }
        }
        pendingJump = null
        // Derivative is taken from consecutive measurements, never from the lagging output.
        val derivativeAlpha = alpha(dt, 2f)
        velocityX += derivativeAlpha * (dx / dt - velocityX)
        velocityY += derivativeAlpha * (dy / dt - velocityY)
        // Keep stationary aim quiet; coherent travel receives the higher cutoff below.
        // The jump gate remains responsible for rejecting isolated spikes.
        var cutoffX = .35f + 20f * (abs(velocityX) - .06f).coerceAtLeast(0f)
        var cutoffY = .35f + 20f * (abs(velocityY) - .06f).coerceAtLeast(0f)
        recent.addLast(Sample(measured, timestampMs))
        while (recent.size > 64 || (recent.size > 4 && timestampMs - recent.first().timestampMs > 900L)) recent.removeFirst()
        if (recent.size >= 3) {
            var pathX = 0f
            var pathY = 0f
            for (i in 1 until recent.size) {
                pathX += abs(recent[i].point.x - recent[i - 1].point.x)
                pathY += abs(recent[i].point.y - recent[i - 1].point.y)
            }
            val displacementX = abs(measured.x - recent.first().point.x)
            val displacementY = abs(measured.y - recent.first().point.y)
            // A consistent small movement is intentional fine aim, not stationary noise.
            // This opens the cutoff without adding prediction or a sticky dead zone.
            // Travel on one axis must not open the gain for noise on the other.
            if (displacementX >= .003f && displacementX >= pathX * .85f) cutoffX = maxOf(cutoffX, 3.5f)
            if (displacementY >= .003f && displacementY >= pathY * .85f) cutoffY = maxOf(cutoffY, 3.5f)
            // Camera jitter may reverse individual steps during deliberate fine aim.
            // A significant time-based trend can establish travel without requiring
            // nearly every sample to move in the same direction. Each axis owns its gain.
            if (hasDirectedTrend(xAxis = true, timestampMs)) cutoffX = maxOf(cutoffX, 3.5f)
            if (hasDirectedTrend(xAxis = false, timestampMs)) cutoffY = maxOf(cutoffY, 3.5f)
        }
        val result = PointerCoordinateMapper.Point(
            previous.x + alpha(dt, cutoffX) * (measured.x - previous.x),
            previous.y + alpha(dt, cutoffY) * (measured.y - previous.y)
        )
        measurement = measured; output = result; measurementTrusted = true
        lastAcceptedTimestamp = timestampMs
        return result
    }

    /** Conservative correlation-adjusted slope evidence from accepted measurements only. */
    private fun hasDirectedTrend(xAxis: Boolean, timestampMs: Long): Boolean {
        val n = recent.size
        val spanMs = timestampMs - recent.first().timestampMs
        if (n < 6 || spanMs < 200L) return false
        // Relative times avoid loss of precision after long device uptimes.
        var meanTime = 0.0
        var meanPosition = 0.0
        for (sample in recent) {
            meanTime += (sample.timestampMs - timestampMs) / 1000.0
            meanPosition += if (xAxis) sample.point.x.toDouble() else sample.point.y.toDouble()
        }
        meanTime /= n
        meanPosition /= n
        var timeEnergy = 0.0
        var covariance = 0.0
        for (sample in recent) {
            val time = (sample.timestampMs - timestampMs) / 1000.0 - meanTime
            val position = (if (xAxis) sample.point.x.toDouble() else sample.point.y.toDouble()) - meanPosition
            timeEnergy += time * time
            covariance += time * position
        }
        if (timeEnergy <= 0.0) return false
        val slope = covariance / timeEnergy
        if (abs(slope) * spanMs / 1000.0 < .003) return false
        var residualEnergy = 0.0
        var lagProduct = 0.0
        var lagEnergy = 0.0
        var previousResidual: Double? = null
        for (sample in recent) {
            val time = (sample.timestampMs - timestampMs) / 1000.0 - meanTime
            val position = (if (xAxis) sample.point.x.toDouble() else sample.point.y.toDouble()) - meanPosition
            val residual = position - slope * time
            residualEnergy += residual * residual
            previousResidual?.let {
                lagProduct += it * residual
                lagEnergy += it * it
            }
            previousResidual = residual
        }
        // Adjacent camera measurements need not be independent. Positive residual
        // correlation reduces effective evidence instead of being counted as more
        // confidence at higher frame rates. This is a conservative motion heuristic,
        // not a calibrated probability or permission to extrapolate the pointer.
        val correlation = if (lagEnergy > 1e-12) (lagProduct / lagEnergy).coerceIn(0.0, .95) else 0.0
        val effectiveSamples = n * (1.0 - correlation) / (1.0 + correlation)
        return effectiveSamples > 2.0 &&
            slope * slope * timeEnergy * (effectiveSamples - 2.0) >= 25.0 * maxOf(residualEnergy, 1e-12)
    }

    fun reset() {
        output = null; measurement = null; pendingJump = null; lastTimestamp = null; lastAcceptedTimestamp = null
        velocityX = 0f; velocityY = 0f; measurementTrusted = false; recent.clear()
        lastRejection = PrecisionRejection.NONE; lastJumpDistance = 0f; lastJumpLimit = 0f
    }

    private companion object {
        /** Screen-normalised distance always tolerated between consecutive results. */
        const val JUMP_BASE = .12f
        /** Screen widths per second a fingertip can plausibly travel (generous). */
        const val MAX_PLAUSIBLE_SPEED = 3f
    }

    private fun alpha(dt: Float, cutoff: Float): Float =
        (1.0 - exp(-2.0 * PI * cutoff * dt)).toFloat().coerceIn(0f, 1f)
}
