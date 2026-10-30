package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class HandInferencePolicyTest {
    @Test fun customOnlyMappingsCanUseLandmarksWhileCannedMappingsKeepClassifier() {
        assertTrue(HandInferencePolicy.landmarksOnly(true, ControlMode.ARMED, classifierRequired = false))
        assertFalse(HandInferencePolicy.landmarksOnly(true, ControlMode.ARMED, classifierRequired = true))
        assertFalse(HandInferencePolicy.landmarksOnly(true, ControlMode.READY, classifierRequired = false))
    }
    @Test fun enabledCommandsAlwaysRetainFullClassifierForEveryMode() {
        for (mode in ControlMode.entries) assertFalse(HandInferencePolicy.landmarksOnly(true, mode))
    }
    @Test fun disabledCommandsUseLighterInferenceOnlyDuringArmedPointerOperation() {
        assertTrue(HandInferencePolicy.landmarksOnly(false, ControlMode.ARMED))
        for (mode in listOf(ControlMode.READY, ControlMode.OFF, ControlMode.PAUSED))
            assertFalse("Rehearsal must retain gesture feedback", HandInferencePolicy.landmarksOnly(false, mode))
    }
}
