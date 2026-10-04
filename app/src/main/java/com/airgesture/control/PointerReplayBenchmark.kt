package com.airgesture.control

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** One normalized vision sample captured from a deterministic replay trace. */
data class PointerReplayFrame(
    val sequence: Int,
    val timestampMs: Long,
    val sourceX: Float,
    val sourceY: Float,
    val expectedX: Float? = null,
    val expectedY: Float? = null
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (sequence < 0) errors += "sequence must be non-negative"
        if (timestampMs < 0L) errors += "timestamp must be non-negative"
        if (!sourceX.isFinite() || sourceX !in 0f..1f) errors += "sourceX must be finite and normalized"
        if (!sourceY.isFinite() || sourceY !in 0f..1f) errors += "sourceY must be finite and normalized"
        if ((expectedX == null) != (expectedY == null)) {
            errors += "expected coordinates must be provided together"
        }
        if (expectedX != null && (!expectedX.isFinite() || expectedX !in 0f..1f)) {
            errors += "expectedX must be finite and normalized"
        }
        if (expectedY != null && (!expectedY.isFinite() || expectedY !in 0f..1f)) {
            errors += "expectedY must be finite and normalized"
        }
        return errors
    }
}

data class PointerReplayTrace(
    val traceId: String,
    val frames: List<PointerReplayFrame>
) {
    fun validate(): PointerReplayValidation {
        val errors = mutableListOf<String>()
        if (traceId.isBlank()) errors += "traceId must not be blank"
        if (frames.isEmpty()) errors += "trace must contain at least one frame"
        var previousSequence = -1
        var previousTimestamp = -1L
        frames.forEachIndexed { index, frame ->
            frame.validate().forEach { errors += "frame[$index]: $it" }
            if (frame.sequence != previousSequence + 1) {
                errors += "frame[$index]: sequence is not contiguous"
            }
            if (frame.timestampMs < previousTimestamp) {
                errors += "frame[$index]: timestamp is out of order"
            }
            previousSequence = frame.sequence
            previousTimestamp = frame.timestampMs
        }
        return PointerReplayValidation(errors)
    }
}

data class PointerReplayValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class PointerReplayMetrics(
    val traceId: String,
    val frameCount: Int,
    val durationMs: Long,
    val meanAbsoluteError: Float?,
    val p95AbsoluteError: Float?,
    val maxAbsoluteError: Float?,
    val meanOutputStep: Float?,
    val maxOutputStep: Float?
)

data class PointerReplayReport(
    val validation: PointerReplayValidation,
    val metrics: PointerReplayMetrics?
) {
    val isValid: Boolean get() = validation.isValid && metrics != null
}

/**
 * Replays normalized samples through a mapper without Android or camera dependencies.
 * This is intentionally deterministic so reports can be compared by commit and trace ID.
 */
object PointerReplayBenchmark {
    fun run(
        trace: PointerReplayTrace,
        mapper: (x: Float, y: Float) -> PointerCoordinateMapper.Point
    ): PointerReplayReport {
        val validation = trace.validate()
        if (!validation.isValid) return PointerReplayReport(validation, null)

        val outputs = trace.frames.map { frame ->
            frame to mapper(frame.sourceX, frame.sourceY)
        }
        val errors = outputs.mapNotNull { (frame, output) ->
            if (frame.expectedX == null || frame.expectedY == null) {
                null
            } else {
                hypot(
                    (output.x - frame.expectedX).toDouble(),
                    (output.y - frame.expectedY).toDouble()
                )
            }
        }.sorted()
        val outputSteps = outputs.zipWithNext { previous, current ->
            hypot(
                (current.second.x - previous.second.x).toDouble(),
                (current.second.y - previous.second.y).toDouble()
            )
        }
        val durationMs = max(0L, trace.frames.last().timestampMs - trace.frames.first().timestampMs)
        val metrics = PointerReplayMetrics(
            traceId = trace.traceId,
            frameCount = trace.frames.size,
            durationMs = durationMs,
            meanAbsoluteError = errors.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            p95AbsoluteError = errors.takeIf { it.isNotEmpty() }?.let {
                it[(ceil(it.size * 0.95).toInt() - 1).coerceIn(0, it.lastIndex)]
            },
            maxAbsoluteError = errors.maxOrNull()?.toFloat(),
            meanOutputStep = outputSteps.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            maxOutputStep = outputSteps.maxOrNull()?.toFloat()
        )
        return PointerReplayReport(validation, metrics)
    }
}
