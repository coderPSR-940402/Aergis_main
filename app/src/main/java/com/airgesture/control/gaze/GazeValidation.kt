package com.airgesture.control.gaze

import kotlin.math.hypot

/**
 * Research-only accuracy measurement. All estimates and targets use normalized full-display
 * coordinates, but the Euclidean error must be weighted by the actual screen pixel dimensions.
 * This code never authorizes an Android action.
 */
internal object GazeValidation {
    data class Target(val label: String, val x: Float, val y: Float)

    // Held out from the nine calibration pairs (.15, .50, .85); positions are within
    // the Gaze Lab canvas, not the entire Android display's reachable corners.
    val targets: List<Target> = listOf(
        Target("Top-left", .10f, .10f),
        Target("Top edge", .52f, .10f),
        Target("Top-right", .90f, .10f),
        Target("Right edge", .90f, .52f),
        Target("Bottom-right", .90f, .90f),
        Target("Bottom edge", .48f, .90f),
        Target("Bottom-left", .10f, .90f),
        Target("Left edge", .10f, .48f),
        Target("Center", .54f, .54f)
    )

    /** Signed prediction minus target: positive x is right, positive y is down. */
    data class Error(
        val diagonalPercent: Double,
        val horizontalPx: Double,
        val verticalPx: Double,
        val distancePx: Double
    )
    data class Summary(val medianPercent: Double, val worstPercent: Double, val worstIndex: Int)

    fun measure(predictedX: Float, predictedY: Float, targetX: Float, targetY: Float,
        widthPx: Float, heightPx: Float): Error? {
        if (listOf(predictedX, predictedY, targetX, targetY, widthPx, heightPx).any { !it.isFinite() } ||
            widthPx <= 0f || heightPx <= 0f ||
            listOf(predictedX, predictedY, targetX, targetY).any { it !in 0f..1f }) return null
        val dx = (predictedX.toDouble() - targetX.toDouble()) * widthPx.toDouble()
        val dy = (predictedY.toDouble() - targetY.toDouble()) * heightPx.toDouble()
        val distance = hypot(dx, dy)
        val diagonal = hypot(widthPx.toDouble(), heightPx.toDouble())
        return Error(distance / diagonal * 100.0, dx, dy, distance)
    }

    fun summarize(errors: List<Error>): Summary? {
        if (errors.isEmpty() || errors.any { !it.diagonalPercent.isFinite() }) return null
        val ordered = errors.map { it.diagonalPercent }.sorted()
        val middle = ordered.size / 2
        val median = if (ordered.size % 2 == 1) ordered[middle]
            else (ordered[middle - 1] + ordered[middle]) / 2.0
        val worstIndex = errors.indices.maxByOrNull { errors[it].diagonalPercent } ?: return null
        return Summary(median, errors[worstIndex].diagonalPercent, worstIndex)
    }
}
