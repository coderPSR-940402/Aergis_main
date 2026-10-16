package com.airgesture.control


enum class ControlMode {
    OFF,
    READY,
    ARMED,
    PAUSED
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
        val protectedToken = packageValue.takeIf { it in PROTECTED_PACKAGES }
            ?: PROTECTED_TOKENS.firstOrNull { searchable.contains(it) }
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

    // Exact matches avoid blocking unrelated packages. Do not blanket-block SystemUI:
    // dismissing its shade may not emit a new foreground-window event.
    private val PROTECTED_PACKAGES = setOf("com.android.settings")

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
        foregroundSafety: ForegroundSafety
    ): ActionSafetyDecision {
        if (action == AirAction.NONE) return ActionSafetyDecision(false, "No action")
        if (!gesturesEnabled) return ActionSafetyDecision(false, "Gesture actions disabled")
        if (controlMode != ControlMode.ARMED) return ActionSafetyDecision(false, "Control is not armed")
        return when (foregroundSafety) {
            ForegroundSafety.SAFE -> ActionSafetyDecision(true, "Allowed")
            ForegroundSafety.PROTECTED -> ActionSafetyDecision(false, "Protected screen detected")
            ForegroundSafety.UNKNOWN -> ActionSafetyDecision(false, "Foreground context unavailable")
        }
    }
}
