package com.airgesture.control

/** Rehearsal keeps classifier feedback even when action switches are disabled. */
internal object HandInferencePolicy {
    fun landmarksOnly(gesturesEnabled: Boolean, mode: ControlMode, classifierRequired: Boolean = true): Boolean =
        mode == ControlMode.ARMED && (!gesturesEnabled || !classifierRequired)
}
