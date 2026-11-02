package com.airgesture.control

data class RuntimeStatus(
    val running: Boolean,
    val pointerEnabled: Boolean,
    val cameraReady: Boolean,
    val message: String
)

object SessionStartPolicy {
    fun evaluate(cameraPermissionGranted: Boolean, accessibilityEnabled: Boolean): SessionStartDecision =
        if (!cameraPermissionGranted) SessionStartDecision(false, "Camera permission is required")
        else if (!accessibilityEnabled) SessionStartDecision(false, "Enable Aergis Accessibility Service")
        else SessionStartDecision(true, "Ready")
}

data class SessionStartDecision(val allowed: Boolean, val reason: String)
