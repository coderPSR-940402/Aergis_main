package com.airgesture.control

/** Regression tolerances for comparing a candidate mapper with the legacy baseline. */
data class PointerMappingRegressionThresholds(
    val maxMeanErrorIncrease: Float = 0.01f,
    val maxP95ErrorIncrease: Float = 0.02f,
    val maxPeakErrorIncrease: Float = 0.03f,
    val maxMeanStepIncrease: Float = 0.02f,
    val maxPeakStepIncrease: Float = 0.04f
) {
    init {
        require(maxMeanErrorIncrease >= 0f)
        require(maxP95ErrorIncrease >= 0f)
        require(maxPeakErrorIncrease >= 0f)
        require(maxMeanStepIncrease >= 0f)
        require(maxPeakStepIncrease >= 0f)
    }
}

data class PointerMappingComparison(
    val traceId: String,
    val baseline: PointerReplayMetrics,
    val candidate: PointerReplayMetrics,
    val thresholds: PointerMappingRegressionThresholds,
    val failures: List<String>
) {
    val passed: Boolean get() = failures.isEmpty()
    val meanErrorDelta: Float get() = delta(candidate.meanAbsoluteError, baseline.meanAbsoluteError)
    val p95ErrorDelta: Float get() = delta(candidate.p95AbsoluteError, baseline.p95AbsoluteError)
    val peakErrorDelta: Float get() = delta(candidate.maxAbsoluteError, baseline.maxAbsoluteError)
    val meanStepDelta: Float get() = delta(candidate.meanOutputStep, baseline.meanOutputStep)
    val peakStepDelta: Float get() = delta(candidate.maxOutputStep, baseline.maxOutputStep)

    private fun delta(candidateValue: Float?, baselineValue: Float?): Float =
        if (candidateValue == null || baselineValue == null) 0f else candidateValue - baselineValue
}

/** Compares a candidate mapping against legacy behavior on the same validated trace. */
object PointerMappingComparisonBenchmark {
    fun compare(
        trace: PointerReplayTrace,
        candidateMapper: (x: Float, y: Float) -> PointerCoordinateMapper.Point,
        thresholds: PointerMappingRegressionThresholds = PointerMappingRegressionThresholds()
    ): PointerMappingComparison? {
        val baselineReport = PointerReplayBenchmark.run(trace) { x, y ->
            PointerCoordinateMapper.map(x, y)
        }
        val candidateReport = PointerReplayBenchmark.run(trace, candidateMapper)
        val baseline = baselineReport.metrics ?: return null
        val candidate = candidateReport.metrics ?: return null
        val failures = buildList {
            if (candidate.frameCount != baseline.frameCount) {
                add("candidate frame count differs from baseline")
            }
            if (candidate.durationMs != baseline.durationMs) {
                add("candidate duration differs from baseline")
            }
            if (candidate.meanAbsoluteError != null && baseline.meanAbsoluteError != null &&
                candidate.meanAbsoluteError - baseline.meanAbsoluteError > thresholds.maxMeanErrorIncrease
            ) add("mean error regression exceeds threshold")
            if (candidate.p95AbsoluteError != null && baseline.p95AbsoluteError != null &&
                candidate.p95AbsoluteError - baseline.p95AbsoluteError > thresholds.maxP95ErrorIncrease
            ) add("p95 error regression exceeds threshold")
            if (candidate.maxAbsoluteError != null && baseline.maxAbsoluteError != null &&
                candidate.maxAbsoluteError - baseline.maxAbsoluteError > thresholds.maxPeakErrorIncrease
            ) add("peak error regression exceeds threshold")
            if (candidate.meanOutputStep != null && baseline.meanOutputStep != null &&
                candidate.meanOutputStep - baseline.meanOutputStep > thresholds.maxMeanStepIncrease
            ) add("mean output-step regression exceeds threshold")
            if (candidate.maxOutputStep != null && baseline.maxOutputStep != null &&
                candidate.maxOutputStep - baseline.maxOutputStep > thresholds.maxPeakStepIncrease
            ) add("peak output-step regression exceeds threshold")
        }
        return PointerMappingComparison(trace.traceId, baseline, candidate, thresholds, failures)
    }
}
