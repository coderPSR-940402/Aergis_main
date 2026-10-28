package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class HandInferencePolicyTest {
    @Test fun enabledCommandsAlwaysRetainFullClassifierForEveryMode() {
        for (mode in ControlMode.entries) assertFalse(HandInferencePolicy.landmarksOnly(true, mode))
    }
    @Test fun disabledCommandsUseLighterInferenceOnlyDuringArmedPointerOperation() {
        assertTrue(HandInferencePolicy.landmarksOnly(false, ControlMode.ARMED))
        for (mode in listOf(ControlMode.READY, ControlMode.OFF, ControlMode.PAUSED))
            assertFalse("Rehearsal must retain gesture feedback", HandInferencePolicy.landmarksOnly(false, mode))
    }
}
