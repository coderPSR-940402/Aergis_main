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
    @Test fun actionAndTuningPersistTogetherWithoutChangingAnotherGesture() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val tuning = GestureTuning(400, 1200, 85, 3, 75, 900)
        ActionMappingStore(context).saveBinding(RegisteredGesture.SHAKA, AirAction.VOLUME_UP, tuning)
        val reopened = ActionMappingStore(context)
        assertEquals(AirAction.VOLUME_UP, reopened.mapping(RegisteredGesture.SHAKA))
        assertEquals(tuning, reopened.tuning(RegisteredGesture.SHAKA))
        assertEquals(GestureTuning(), reopened.tuning(RegisteredGesture.OK))
        prefs.edit().clear().commit()
    }

    @Test fun recognitionMigrationPreservesAllSavedIdsAndLegacyTimingForRollback() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = ActionMappingStore(context)
        val legacy = GestureTuning(1500, 3000, 85, 3, 75, 900)
        val assignments = RegisteredGesture.entries.associateWith {
            if (it == RegisteredGesture.THUMB_DOWN) AirAction.NONE else AirAction.BACK
        }
        assignments.forEach { (gesture, action) -> store.saveBinding(gesture, action, legacy) }
        val snapshot = prefs.all.toMap()
        val reopened = ActionMappingStore(context)
        assignments.forEach { (gesture, action) ->
            assertEquals(action, reopened.mapping(gesture))
            val tuning = reopened.tuning(gesture)
            assertEquals(legacy, tuning)
            // Same copy operation used by the picker: active controls may change;
            // ignored legacy timing must survive a save for rollback.
            reopened.saveBinding(gesture, action, tuning.copy(confidencePercent = 90))
            assertEquals(legacy.holdMs, reopened.tuning(gesture).holdMs)
            assertEquals(legacy.repeatDelayMs, reopened.tuning(gesture).repeatDelayMs)
            assertEquals(snapshot["gesture_${gesture.name}"], prefs.all["gesture_${gesture.name}"])
        }
        prefs.edit().clear().commit()
    }

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
