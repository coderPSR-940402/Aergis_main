package com.airgesture.control.gaze

import kotlin.math.abs

/** Experimental local fit; an estimate never carries click/gesture authority. */
internal class GazeCalibration {
    data class Sample(val features: DoubleArray, val x: Double, val y: Double)
    private var weights: Array<DoubleArray>? = null
    private var limits: List<Pair<Double, Double>> = emptyList()
    fun fit(samples: List<Sample>): Boolean {
        weights = null
        if (samples.size < 9 || samples.any { !valid(it.features) || !it.x.isFinite() || !it.y.isFinite() ||
                it.x !in 0.0..1.0 || it.y !in 0.0..1.0 }) return false
        limits = (0..3).map { i -> samples.minOf { it.features[i] } to samples.maxOf { it.features[i] } }
        if ((0..1).any { limits[it].second - limits[it].first < .01 }) return false
        val matrix = Array(5) { DoubleArray(7) }
        for (sample in samples) {
            val f = doubleArrayOf(1.0, *sample.features)
            for (i in 0..4) {
                for (j in 0..4) matrix[i][j] += f[i] * f[j]
                matrix[i][5] += f[i] * sample.x; matrix[i][6] += f[i] * sample.y
            }
        }
        for (i in 1..4) matrix[i][i] += .0001
        for (column in 0..4) {
            val pivot = (column..4).maxBy { abs(matrix[it][column]) }
            if (abs(matrix[pivot][column]) < 1e-9) return false
            val swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap
            val divisor = matrix[column][column]
            for (j in column..6) matrix[column][j] /= divisor
            for (i in 0..4) if (i != column) {
                val factor = matrix[i][column]
                for (j in column..6) matrix[i][j] -= factor * matrix[column][j]
            }
        }
        val candidate = Array(2) { output -> DoubleArray(5) { matrix[it][5 + output] } }
        if (candidate.any { row -> row.any { !it.isFinite() } }) return false
        // Training residual rejects a broken fit; it is not held-out accuracy evidence.
        val error = samples.maxOf { s ->
            val f = doubleArrayOf(1.0, *s.features)
            maxOf(abs(candidate[0].indices.sumOf { candidate[0][it] * f[it] } - s.x),
                abs(candidate[1].indices.sumOf { candidate[1][it] * f[it] } - s.y))
        }
        if (error > .15) return false
        weights = candidate; return true
    }
    fun estimate(features: DoubleArray): Pair<Float, Float>? {
        val w = weights ?: return null
        if (!valid(features) || features.indices.any { features[it] !in (limits[it].first - .15)..(limits[it].second + .15) }) return null
        val f = doubleArrayOf(1.0, *features)
        val x = w[0].indices.sumOf { w[0][it] * f[it] }; val y = w[1].indices.sumOf { w[1][it] * f[it] }
        if (!x.isFinite() || !y.isFinite() || x !in -.1..1.1 || y !in -.1..1.1) return null
        return x.coerceIn(0.0, 1.0).toFloat() to y.coerceIn(0.0, 1.0).toFloat()
    }
    private fun valid(features: DoubleArray) = features.size == 4 && features.all { it.isFinite() }
}
