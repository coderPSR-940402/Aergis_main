package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class AergisVoiceCommandPolicyTest {
    @Test fun allowsExplicitNonCoordinateCommands() {
        assertEquals(AirAction.HOME, AergisVoiceCommandPolicy.parse("HOME"))
        assertEquals(AirAction.SCROLL_DOWN, AergisVoiceCommandPolicy.parse("SCROLL_DOWN"))
        assertEquals(AirAction.TOGGLE_POINTER, AergisVoiceCommandPolicy.parse("TOGGLE_POINTER"))
    }
    @Test fun rejectsTouchAndHighRiskCommands() {
        for (name in listOf("TAP", "DOUBLE_TAP", "LONG_PRESS", "LOCK_SCREEN", "SCREENSHOT", "CALIBRATE_POINTER", "WIFI_CONTROLS", "UNKNOWN", "home", "")) {
            assertNull(name, AergisVoiceCommandPolicy.parse(name))
        }
    }
    @Test fun sessionControlsAreNotNormalActions() {
        for (name in listOf("PAUSE", "DISARM", "STOP")) {
            assertTrue(AergisVoiceCommandPolicy.isSessionControl(name))
            assertNull(AergisVoiceCommandPolicy.parse(name))
        }
    }
    @Test fun voiceOriginNeedsExplicitConsent() {
        assertFalse(InputChannelPolicy.enabled(ActionOrigin.VOICE, AirAction.HOME,
            pointerEnabled = false, pointerClickingEnabled = false, gesturesEnabled = false, voiceEnabled = false))
        assertTrue(InputChannelPolicy.enabled(ActionOrigin.VOICE, AirAction.HOME,
            pointerEnabled = false, pointerClickingEnabled = false, gesturesEnabled = false, voiceEnabled = true))
        assertFalse(ActionDispatchGate.evaluate(AirAction.HOME, 2L, 2L, gesturesEnabled = false,
            controlMode = ControlMode.READY, foregroundSafety = ForegroundSafety.SAFE,
            origin = ActionOrigin.VOICE, voiceEnabled = true).allowed)
        assertFalse(ActionDispatchGate.evaluate(AirAction.HOME, 1L, 2L, gesturesEnabled = false,
            controlMode = ControlMode.ARMED, foregroundSafety = ForegroundSafety.SAFE,
            origin = ActionOrigin.VOICE, voiceEnabled = true).allowed)
    }
}
