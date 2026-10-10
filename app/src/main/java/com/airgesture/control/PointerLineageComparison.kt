package com.airgesture.control

import kotlin.math.hypot
import kotlin.math.sqrt

internal enum class PointerFilterMode { PRECISION, CURRENT, VC49 }

internal data class PointerLineageSample(
    val mappedTip: PointerCoordinateMapper.Point,
    val current: PointerCoordinateMapper.Point,
    val vc49: PointerCoordinateMapper.Point,
    val precision: PointerCoordinateMapper.Point = current,
    val precisionTrusted: Boolean = true,
    val precisionRejection: PrecisionRejection = PrecisionRejection.NONE,
    val precisionJumpDistance: Float = 0f,
    val precisionJumpLimit: Float = 0f
) {
    fun selected(mode: PointerFilterMode) = when (mode) {
        PointerFilterMode.PRECISION -> precision
        PointerFilterMode.CURRENT -> current
        PointerFilterMode.VC49 -> vc49
    }
}

/** All candidates consume the same measured tip and calibration. Never auto-selects. */
internal class PointerLineageComparison {
    private val recovered = Vc49PointerMotionFilter()
    private val precision = PrecisionPointerFilter()

    fun update(mappedTip: PointerCoordinateMapper.Point, current: PointerCoordinateMapper.Point,
        timestampMs: Long): PointerLineageSample {
        val point = recovered.update(mappedTip.x, mappedTip.y, timestampMs, 1f)
        val precise = precision.update(mappedTip.x, mappedTip.y, timestampMs)
        return PointerLineageSample(mappedTip, current, PointerCoordinateMapper.Point(point.first, point.second),
            precise, precision.measurementTrusted, precision.lastRejection,
            precision.lastJumpDistance, precision.lastJumpLimit)
    }

    fun reset() { recovered.reset(); precision.reset() }
}

/** O(1) statistics. Stationary labels must be supplied by a test segment, not guessed from jitter. */
internal class PointerLineageMetrics {
    private var n = 0
    private var stationaryN = 0
    private var meanX = 0.0
    private var meanY = 0.0
    private var stationarySquared = 0.0
    private var errorSum = 0.0
    private var maxError = 0.0
    private var path = 0.0
    private var accelerationSquared = 0.0
    private var stepN = 0
    private var previous: PointerCoordinateMapper.Point? = null
    private var previousDx: Double? = null
    private var previousDy: Double? = null
    private var minX = 1f
    private var maxX = 0f
    private var minY = 1f
    private var maxY = 0f

    fun sample(measured: PointerCoordinateMapper.Point, output: PointerCoordinateMapper.Point, stationary: Boolean) {
        n++
        val error = hypot((output.x - measured.x).toDouble(), (output.y - measured.y).toDouble())
        errorSum += error; maxError = maxOf(maxError, error)
        minX = minOf(minX, output.x); maxX = maxOf(maxX, output.x)
        minY = minOf(minY, output.y); maxY = maxOf(maxY, output.y)
        if (stationary) {
            stationaryN++
            val dx = output.x - meanX; val dy = output.y - meanY
            meanX += dx / stationaryN; meanY += dy / stationaryN
            stationarySquared += dx * (output.x - meanX) + dy * (output.y - meanY)
        }
        previous?.let { old ->
            val dx = (output.x - old.x).toDouble(); val dy = (output.y - old.y).toDouble()
            path += hypot(dx, dy)
            previousDx?.let { vx ->
                val ax = dx - vx; val ay = dy - previousDy!!
                accelerationSquared += ax * ax + ay * ay; stepN++
            }
            previousDx = dx; previousDy = dy
        }
        previous = output
    }

    fun snapshot() = linkedMapOf(
        "samples" to n.toDouble(), "stationarySamples" to stationaryN.toDouble(),
        "stationaryJitterRms" to if (stationaryN == 0) 0.0 else sqrt(stationarySquared / stationaryN),
        "meanDistanceToMeasurement" to if (n == 0) 0.0 else errorSum / n,
        "maxDistanceToMeasurement" to maxError, "pathLength" to path,
        "secondDifferenceRms" to if (stepN == 0) 0.0 else sqrt(accelerationSquared / stepN),
        "minX" to minX.toDouble(), "maxX" to maxX.toDouble(),
        "minY" to minY.toDouble(), "maxY" to maxY.toDouble()
    )
}
