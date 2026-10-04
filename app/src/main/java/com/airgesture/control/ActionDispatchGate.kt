package com.airgesture.control

/**
 * Pure safety gate for actions that may be queued across threads.
 *
 * The epoch check runs before the policy check so an action created under an
 * older session or safety state is rejected even if the current state happens
 * to look allowed again.
 */
internal object ActionDispatchGate {
    fun evaluate(
        action: AirAction,
        expectedEpoch: Long?,
        currentEpoch: Long,
        gesturesEnabled: Boolean,
        controlMode: ControlMode,
        motionActive: Boolean,
        foregroundSafety: ForegroundSafety
    ): ActionSafetyDecision {
        if (expectedEpoch != null && expectedEpoch != currentEpoch) {
            return ActionSafetyDecision(false, "Runtime safety state changed")
        }
        return ActionSafetyPolicy.evaluate(
            action = action,
            gesturesEnabled = gesturesEnabled,
            controlMode = controlMode,
            motionActive = motionActive,
            foregroundSafety = foregroundSafety
        )
    }
}
