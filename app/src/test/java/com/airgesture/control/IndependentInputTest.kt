package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class IndependentInputTest {
    @Test fun pointerAndMappedCommandsAreIndependentForEverySwitchCombination() {
        for (pointer in listOf(false, true)) for (click in listOf(false, true)) for (commands in listOf(false, true)) {
            assertEquals("pointer=$pointer click=$click commands=$commands", click,
                InputChannelPolicy.enabled(ActionOrigin.POINTER, if (pointer) AirAction.TAP else AirAction.SELECT_ITEM, pointer, click, commands))
            assertEquals(commands,
                InputChannelPolicy.enabled(ActionOrigin.GESTURE, AirAction.BACK, pointer, click, commands))
        }
    }
    @Test fun dispatchGatePreservesOriginAndSafetyAcrossQueue() {
        fun decision(origin: ActionOrigin, enabled: Boolean, epoch: Long = 5L, mode: ControlMode = ControlMode.ARMED,
            safety: ForegroundSafety = ForegroundSafety.SAFE) = ActionDispatchGate.evaluate(
                AirAction.TAP, 5L, epoch, false, mode, safety, origin, true, enabled).allowed
        assertTrue(decision(ActionOrigin.POINTER, true))
        assertFalse(decision(ActionOrigin.GESTURE, true))
        assertFalse(decision(ActionOrigin.POINTER, false))
        assertFalse(decision(ActionOrigin.POINTER, true, epoch = 6L))
        assertFalse(decision(ActionOrigin.POINTER, true, mode = ControlMode.PAUSED))
        assertFalse(decision(ActionOrigin.POINTER, true, safety = ForegroundSafety.PROTECTED))
    }
    @Test fun hiddenPointerPinchMayActivateSelectionWithoutMappedCommands() {
        assertTrue(InputChannelPolicy.enabled(ActionOrigin.POINTER, AirAction.SELECT_ITEM, false, true, false))
        assertFalse(InputChannelPolicy.enabled(ActionOrigin.POINTER, AirAction.SELECT_ITEM, false, false, true))
    }
    @Test fun pointerOriginCannotAuthorizeSystemCommands() {
        assertFalse(InputChannelPolicy.enabled(ActionOrigin.POINTER, AirAction.HOME, true, true, true))
    }
}
