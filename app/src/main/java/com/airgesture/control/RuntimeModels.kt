package com.airgesture.control

import com.airgesture.control.filtering.AdaptiveKalmanFilter

data class PointerDecision(val x: Float, val y: Float, val active: Boolean)
data class PointerFrame(val timestampMs: Long, val x: Float, val y: Float)
data class RuntimeStatus(val running: Boolean, val pointerEnabled: Boolean, val cameraReady: Boolean, val message: String)
data class GestureTelemetry(val timestampMs: Long, val gesture: String, val confidence: Float)
data class VisionPerformanceSnapshot(
    val timestampMs: Long,
    val fps: Float,
    val inferenceMs: Long,
    val submittedFrames: Long = 0L,
    val resultFrames: Long = 0L,
    val staleDroppedResults: Long = 0L,
    val throttlePercent: Float = 0f,
    val resultYieldPercent: Float = 0f,
    val staleDropPercent: Float = 0f,
    val lastResultAgeMs: Long = 0L,
    val inFlight: Int = 0
)

class VisionPerformanceMonitor {
    private var firstTimestamp = Long.MIN_VALUE
    private var lastTimestamp = 0L
    private var submittedFrames = 0L
    private var resultFrames = 0L
    private var staleDroppedResults = 0L
    private var throttledFrames = 0L
    private var inFlight = 0
    private var lastResultAgeMs = 0L
    private var inferenceEmaMs = 0f

    @Synchronized
    fun recordFrame(timestampMs: Long, throttled: Boolean = false) {
        if (lastTimestamp != 0L && timestampMs < lastTimestamp) return
        if (firstTimestamp == Long.MIN_VALUE) firstTimestamp = timestampMs
        lastTimestamp = timestampMs
        if (throttled) throttledFrames++
    }

    @Synchronized
    fun recordSubmitted(timestampMs: Long, throttled: Boolean = false) {
        recordFrame(timestampMs, throttled)
        submittedFrames++
        inFlight++
    }

    @Synchronized
    fun recordResult(submittedAtMs: Long, resultAtMs: Long) {
        if (resultAtMs < submittedAtMs) return
        resultFrames++
        inFlight = (inFlight - 1).coerceAtLeast(0)
        lastResultAgeMs = (resultAtMs - submittedAtMs).coerceAtLeast(0L)
        inferenceEmaMs = if (inferenceEmaMs == 0f) lastResultAgeMs.toFloat()
        else inferenceEmaMs * 0.8f + lastResultAgeMs * 0.2f
        if (resultAtMs > lastTimestamp) lastTimestamp = resultAtMs
    }

    @Synchronized
    fun recordStaleResultDropped() {
        staleDroppedResults++
        inFlight = (inFlight - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun reset() {
        firstTimestamp = Long.MIN_VALUE
        lastTimestamp = 0L
        submittedFrames = 0L
        resultFrames = 0L
        staleDroppedResults = 0L
        throttledFrames = 0L
        inFlight = 0
        lastResultAgeMs = 0L
        inferenceEmaMs = 0f
    }

    @Synchronized
    fun snapshot(): VisionPerformanceSnapshot {
        val elapsedMs = if (firstTimestamp != Long.MIN_VALUE) {
            (lastTimestamp - firstTimestamp).coerceAtLeast(1L)
        } else 1L
        val outcomes = resultFrames + staleDroppedResults
        return VisionPerformanceSnapshot(
            timestampMs = lastTimestamp,
            fps = resultFrames * 1000f / elapsedMs,
            inferenceMs = inferenceEmaMs.toLong(),
            submittedFrames = submittedFrames,
            resultFrames = resultFrames,
            staleDroppedResults = staleDroppedResults,
            throttlePercent = percentage(throttledFrames, submittedFrames + throttledFrames),
            resultYieldPercent = percentage(resultFrames, outcomes),
            staleDropPercent = percentage(staleDroppedResults, outcomes),
            lastResultAgeMs = lastResultAgeMs,
            inFlight = inFlight
        )
    }

    private fun percentage(part: Long, whole: Long): Float =
        if (whole <= 0L) 0f else part * 100f / whole
}

class PointerTracker(
    private val kalmanFilter: AdaptiveKalmanFilter = AdaptiveKalmanFilter()
) {
    fun update(frame: PointerFrame): PointerDecision {
        val filtered = kalmanFilter.filter(frame.x, frame.y, frame.timestampMs)
        return PointerDecision(filtered.x, filtered.y, true)
    }
}

class InteractionArbiter {
    fun accept(decision: GestureDecision): Boolean = decision.action != AirAction.NONE && decision.confidence >= 0.55f
}

object SessionStartPolicy {
    fun evaluate(cameraPermissionGranted: Boolean, accessibilityEnabled: Boolean): SessionStartDecision =
        if (!cameraPermissionGranted) SessionStartDecision(false, "Camera permission is required")
        else if (!accessibilityEnabled) SessionStartDecision(false, "Enable Aergis Accessibility Service")
        else SessionStartDecision(true, "Ready")
}

data class SessionStartDecision(val allowed: Boolean, val reason: String)
