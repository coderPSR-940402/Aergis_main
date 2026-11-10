package com.airgesture.control

internal enum class LiveDiagnosticKind {
    MISSED_CLICK,
    ACTION_FAILED,
    SELECTION_FAILED,
    HAND_LOST,
    TRACKING_JUMP,
    CAMERA_DELAY,
    INFERENCE_STALL,
    PROCESS_RESTART
}

internal enum class LiveDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}

/** Compact monotonic telemetry sample retained only in memory by the live diagnostics timeline. */
internal data class LiveDiagnosticSample(
    val uptimeMs: Long,
    val wallClockMs: Long,
    val frameId: Long? = null,
    val sessionEpoch: Long? = null,
    val inputEpoch: Long? = null,
    val requestId: Long? = null,
    val handId: String? = null,
    val pointerX: Float? = null,
    val pointerY: Float? = null,
    val frozenTargetX: Float? = null,
    val frozenTargetY: Float? = null,
    val gesture: String? = null,
    val contactState: String? = null,
    val action: String? = null,
    val dispatchState: String? = null,
    val completionState: String? = null,
    val cameraAgeMs: Long? = null,
    val analyzedFrameGapMs: Long? = null,
    val inferenceMs: Long? = null,
    val rawPointerX: Float? = null,
    val rawPointerY: Float? = null,
    val rawTracking: Boolean? = null,
    val sensorDeliveryMs: Long? = null,
    val workerQueueMs: Long? = null
) {
    init {
        require(uptimeMs >= 0L) { "uptimeMs must be non-negative" }
        require(wallClockMs >= 0L) { "wallClockMs must be non-negative" }
    }
}

internal data class LiveDiagnosticTrigger(
    val kind: LiveDiagnosticKind,
    val severity: LiveDiagnosticSeverity,
    val uptimeMs: Long,
    val wallClockMs: Long,
    val requestId: Long? = null,
    val action: String? = null,
    val reasonCode: String? = null,
    val dedupKey: String? = null,
    val frozenTargetX: Float? = null,
    val frozenTargetY: Float? = null,
    val sessionEpoch: Long? = null,
    val inputEpoch: Long? = null
) {
    init {
        require(uptimeMs >= 0L) { "uptimeMs must be non-negative" }
        require(wallClockMs >= 0L) { "wallClockMs must be non-negative" }
    }
}

internal data class LiveDiagnosticSnapshot(
    val trigger: LiveDiagnosticTrigger,
    val before: List<LiveDiagnosticSample>,
    val after: List<LiveDiagnosticSample>
)

internal fun interface LiveDiagnosticEventSink {
    fun publish(snapshot: LiveDiagnosticSnapshot)
}
