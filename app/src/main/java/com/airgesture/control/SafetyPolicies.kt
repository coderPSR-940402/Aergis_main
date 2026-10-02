package com.airgesture.control

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class ControlMode {
    OFF,
    READY,
    ARMED,
    PAUSED
}

data class MotionState(
    val active: Boolean,
    val magnitude: Float,
    val reason: String?
)

internal class DeviceMotionCancellation(
    private val gyroActivateThreshold: Float = 1.2f,
    private val gyroReleaseThreshold: Float = 0.7f,
    private val accelerationActivateThreshold: Float = 2.5f,
    private val accelerationReleaseThreshold: Float = 1.2f,
    private val releaseHoldMs: Long = 250L
) {
    private var active = false
    private var lastUnsafeAtMs = Long.MIN_VALUE

    fun update(gyroMagnitude: Float, accelerationDeviation: Float, timestampMs: Long): MotionState {
        val safeGyro = gyroMagnitude.coerceAtLeast(0f)
        val safeAcceleration = accelerationDeviation.coerceAtLeast(0f)
        val activating = safeGyro >= gyroActivateThreshold ||
            safeAcceleration >= accelerationActivateThreshold
        val releasing = safeGyro <= gyroReleaseThreshold &&
            safeAcceleration <= accelerationReleaseThreshold

        if (activating) {
            active = true
            lastUnsafeAtMs = timestampMs
        } else if (active && releasing &&
            timestampMs - lastUnsafeAtMs >= releaseHoldMs
        ) {
            active = false
        }

        val magnitude = max(safeGyro / gyroActivateThreshold, safeAcceleration / accelerationActivateThreshold)
        return MotionState(
            active = active,
            magnitude = magnitude,
            reason = if (active) "Device motion detected; control paused" else null
        )
    }

    fun reset(): MotionState {
        active = false
        lastUnsafeAtMs = Long.MIN_VALUE
        return MotionState(false, 0f, null)
    }
}

enum class ForegroundSafety {
    UNKNOWN,
    SAFE,
    PROTECTED
}

data class ForegroundContextState(
    val packageName: String? = null,
    val className: String? = null,
    val safety: ForegroundSafety = ForegroundSafety.UNKNOWN,
    val reason: String = "Foreground context unavailable"
)

internal object ForegroundContextPolicy {
    fun evaluate(packageName: CharSequence?, className: CharSequence?): ForegroundContextState {
        val packageValue = packageName?.toString()?.trim().orEmpty()
        val classValue = className?.toString()?.trim().orEmpty()
        if (packageValue.isBlank()) {
            return ForegroundContextState(
                packageName = null,
                className = classValue.ifBlank { null },
                safety = ForegroundSafety.UNKNOWN,
                reason = "Foreground context unavailable"
            )
        }

        val searchable = "$packageValue $classValue".lowercase()
        val protectedToken = PROTECTED_TOKENS.firstOrNull { searchable.contains(it) }
        return if (protectedToken != null) {
            ForegroundContextState(
                packageName = packageValue,
                className = classValue.ifBlank { null },
                safety = ForegroundSafety.PROTECTED,
                reason = "Protected screen detected ($protectedToken)"
            )
        } else {
            ForegroundContextState(
                packageName = packageValue,
                className = classValue.ifBlank { null },
                safety = ForegroundSafety.SAFE,
                reason = "Foreground context allowed"
            )
        }
    }

    private val PROTECTED_TOKENS = listOf(
        "keyguard",
        "lockscreen",
        "permissioncontroller",
        "packageinstaller",
        "installer",
        "resolver",
        "biometric",
        "password",
        "pinentry",
        "payment",
        "wallet",
        "credential",
        "emergency"
    )
}

internal data class ActionSafetyDecision(val allowed: Boolean, val reason: String)

internal object ActionSafetyPolicy {
    fun evaluate(
        action: AirAction,
        gesturesEnabled: Boolean,
        controlMode: ControlMode,
        motionActive: Boolean,
        foregroundSafety: ForegroundSafety
    ): ActionSafetyDecision {
        if (action == AirAction.NONE) return ActionSafetyDecision(false, "No action")
        if (!gesturesEnabled) return ActionSafetyDecision(false, "Gesture actions disabled")
        if (controlMode != ControlMode.ARMED) return ActionSafetyDecision(false, "Control is not armed")
        if (motionActive) return ActionSafetyDecision(false, "Device motion detected; control paused")
        return when (foregroundSafety) {
            ForegroundSafety.SAFE -> ActionSafetyDecision(true, "Allowed")
            ForegroundSafety.PROTECTED -> ActionSafetyDecision(false, "Protected screen detected")
            ForegroundSafety.UNKNOWN -> ActionSafetyDecision(false, "Foreground context unavailable")
        }
    }
}

internal fun accelerationDeviation(x: Float, y: Float, z: Float): Float =
    abs(sqrt(x * x + y * y + z * z) - GRAVITY_MPS2)

private const val GRAVITY_MPS2 = 9.80665f
