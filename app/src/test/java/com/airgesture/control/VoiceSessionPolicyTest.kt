package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.launch

class VoiceSessionPolicyTest {
    @Test fun oldSessionCallbacksCannotReacquireAuthorityAfterStopAndRestart() {
        val lifecycle = VoiceSessionLifecycle()
        val old = lifecycle.start()
        assertTrue(lifecycle.accepts(old))
        lifecycle.stop()
        val fresh = lifecycle.start()
        assertFalse(lifecycle.accepts(old))
        assertTrue(lifecycle.accepts(fresh))
    }
    @Test fun microphoneSuppressesSpeechDuringConnectionAndReconnect() {
        assertFalse(VoiceFeedbackPolicy.maySpeak(microphoneActive = true))
        assertTrue(VoiceFeedbackPolicy.maySpeak(microphoneActive = false))
    }
    @Test fun voiceSelectionSurvivesGesturesOffButRequiresSameVoiceSession() {
        assertTrue(VoiceSelectionAuthority.valid(ActionOrigin.VOICE, 7, 7, true))
        assertFalse(VoiceSelectionAuthority.valid(ActionOrigin.VOICE, 7, 8, true))
        assertFalse(VoiceSelectionAuthority.valid(ActionOrigin.VOICE, 7, 7, false))
    }
    @Test fun micStartStopsAlreadyQueuedSpeechAndReconnectKeepsItSuppressed() {
        var stops = 0
        val speech = VoiceSpeechGate { stops++ }
        speech.microphoneChanged(true)
        assertEquals(1, stops)
        assertFalse(speech.maySpeak())
        speech.microphoneChanged(true)
        assertFalse(speech.maySpeak())
        speech.microphoneChanged(false)
        assertTrue(speech.maySpeak())
    }
    @Test fun revokedCallbacksCannotPublishEvenBeforeNextSessionStarts() {
        val lifecycle = VoiceSessionLifecycle()
        val old = lifecycle.start()
        lifecycle.stop()
        var published = false
        lifecycle.ifCurrent(old) { published = true }
        assertFalse(published)
    }
    @Test fun voiceActionsDenyProtectedUnknownAndUnarmedScreens() {
        for (safety in listOf(ForegroundSafety.PROTECTED, ForegroundSafety.UNKNOWN)) {
            assertFalse(ActionDispatchGate.evaluate(AirAction.SELECT_ITEM, 1L, 1L, false,
                ControlMode.ARMED, safety, origin = ActionOrigin.VOICE, voiceEnabled = true).allowed)
        }
        assertFalse(ActionDispatchGate.evaluate(AirAction.SELECT_ITEM, 1L, 1L, false,
            ControlMode.PAUSED, ForegroundSafety.SAFE, origin = ActionOrigin.VOICE, voiceEnabled = true).allowed)
    }
    @Test fun tokenBrokerRequiresHttpsAndNoUrlCredentials() {
        assertTrue(VoiceBrokerPolicy.configured("https://voice.example/token"))
        assertFalse(VoiceBrokerPolicy.configured("http://voice.example/token"))
        assertFalse(VoiceBrokerPolicy.configured("https://secret@voice.example/token"))
        assertFalse(VoiceBrokerPolicy.configured("https://voice.example/token?api_key=secret"))
        assertFalse(VoiceBrokerPolicy.configured(""))
    }

    @Test fun hungStartTimeoutKeepsOwnershipAndLateSessionStillCloses() = kotlinx.coroutines.runBlocking {
        val lifecycle = VoiceSessionLifecycle()
        val generation = lifecycle.start()
        val connect = kotlinx.coroutines.CompletableDeferred<Unit>()
        var released = false
        val pending = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            connect.await()
            if (!lifecycle.accepts(generation)) released = true
        }
        lifecycle.stop()
        assertFalse(finishVoiceSession(pending, 10L) { error("No session exists yet") })
        assertTrue("Timeout must not cancel pending start before late cleanup", pending.isActive)
        assertFalse(lifecycle.accepts(generation))
        connect.complete(Unit)
        pending.join()
        assertTrue("Late successful session must still release its resources", released)
    }
}
