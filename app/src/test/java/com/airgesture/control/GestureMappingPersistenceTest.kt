package com.airgesture.control

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GestureMappingPersistenceTest {
    @Test fun legacyMappingsSurviveAndEveryGestureCanBeSavedIndependently() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = ActionMappingStore(context)
        store.setMapping(AirAction.TAP, AirAction.BACK)
        assertEquals(AirAction.BACK, store.mapping(RegisteredGesture.THUMB_UP))
        val assigned = RegisteredGesture.entries.associateWith { AirAction.entries[(it.ordinal + 1) % AirAction.entries.size] }
        assigned.forEach { (gesture, action) -> store.setMapping(gesture, action) }
        val reopened = ActionMappingStore(context)
        assigned.forEach { (gesture, action) ->
            assertEquals(action, reopened.mapping(gesture))
            assertEquals(action, GestureInterpreter(reopened).interpret(GestureSignal(gesture.signalName, .9f)).action)
        }
        store.setMapping(RegisteredGesture.OK, AirAction.HOME)
        assertEquals(assigned[RegisteredGesture.SHAKA], store.mapping(RegisteredGesture.SHAKA))
        prefs.edit().putString("gesture_OK", "unsupported").commit()
        assertEquals(AirAction.NONE, store.mapping(RegisteredGesture.OK))
        prefs.edit().clear().commit()
    }
}
