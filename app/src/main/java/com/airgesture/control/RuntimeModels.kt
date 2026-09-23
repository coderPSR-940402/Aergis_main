package com.airgesture.control

data class PointerDecision(val x: Float, val y: Float, val active: Boolean)
data class PointerFrame(val timestampMs: Long, val x: Float, val y: Float)
data class RuntimeStatus(val running: Boolean, val pointerEnabled: Boolean, val cameraReady: Boolean, val message: String)
data class GestureTelemetry(val timestampMs: Long, val gesture: String, val confidence: Float)
data class VisionPerformanceSnapshot(val timestampMs: Long, val fps: Float, val inferenceMs: Long)

class VisionPerformanceMonitor {
    private var lastTimestamp = 0L
    private var frames = 0
    fun recordFrame(timestampMs: Long) { frames++; lastTimestamp = timestampMs }
    fun snapshot(): VisionPerformanceSnapshot {
        return VisionPerformanceSnapshot(lastTimestamp, frames.toFloat(), 0L)
    }
}

class PointerTracker {
    fun update(frame: PointerFrame): PointerDecision = PointerDecision(frame.x, frame.y, true)
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
