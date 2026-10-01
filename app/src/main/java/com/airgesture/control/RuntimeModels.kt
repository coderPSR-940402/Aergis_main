package com.airgesture.control

import com.airgesture.control.filtering.AdaptiveKalmanFilter

data class PointerDecision(val x: Float, val y: Float, val active: Boolean)
data class PointerFrame(val timestampMs: Long, val x: Float, val y: Float)
data class RuntimeStatus(val running: Boolean, val pointerEnabled: Boolean, val cameraReady: Boolean, val message: String)
data class GestureTelemetry(val timestampMs: Long, val gesture: String, val confidence: Float)
data class VisionPerformanceSnapshot(val timestampMs: Long, val fps: Float, val inferenceMs: Long)

class VisionPerformanceMonitor {
    private var lastTimestamp = 0L
    private var frames = 0
    private var firstTimestamp = 0L

    fun recordFrame(timestampMs: Long) {
        if (firstTimestamp == 0L) firstTimestamp = timestampMs
        frames++
        lastTimestamp = timestampMs
    }

    fun snapshot(): VisionPerformanceSnapshot {
        val elapsedMs = if (lastTimestamp > 0L && firstTimestamp > 0L) {
            (lastTimestamp - firstTimestamp).coerceAtLeast(1L)
        } else {
            1L
        }
        val fps = if (elapsedMs > 0L) frames * 1000f / elapsedMs.toFloat() else frames.toFloat()
        return VisionPerformanceSnapshot(lastTimestamp, fps, 0L)
    }
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
