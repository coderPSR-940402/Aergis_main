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
class TouchFreeNavigationPresetTest {
    @Test fun presetReportsMissingActionsWithoutOverwritingOccupiedSlots() {
        val context = RuntimeEnvironment.getApplication() as Context
        val prefs = context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val store = ActionMappingStore(context)
            store.saveBinding(RegisteredGesture.THREE_FINGERS, AirAction.VOLUME_UP,
                GestureTuning(volumeSteps = 3))
            assertEquals(listOf(AirAction.NEXT_ITEM), TouchFreeNavigationPreset.apply(store))
            assertEquals(AirAction.VOLUME_UP, store.mapping(RegisteredGesture.THREE_FINGERS))
            assertEquals(3, store.tuning(RegisteredGesture.THREE_FINGERS).volumeSteps)
            // An existing alternative assignment satisfies the action; don't report
            // the preferred slot as missing just because it is occupied.
            store.setMapping(RegisteredGesture.THUMB_UP, AirAction.NEXT_ITEM)
            assertEquals(emptyList<AirAction>(), TouchFreeNavigationPreset.apply(store))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun everyPreferenceObserverSeesTheWholePreset() {
        val context = RuntimeEnvironment.getApplication() as Context
        val prefs = context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = ActionMappingStore(context)
        val expected = TouchFreeNavigationPreset.bindings
        var notifications = 0
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            notifications++
            for ((gesture, action) in expected) assertEquals(action, store.mapping(gesture))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            TouchFreeNavigationPreset.apply(store)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(notifications > 0)
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            prefs.edit().clear().commit()
        }
    }

    @Test fun presetAddsPointerFreeNavigationWithoutReplacingAssignmentsOrTuning() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val store = ActionMappingStore(context)
            store.saveBinding(RegisteredGesture.SHAKA, AirAction.VOLUME_UP, GestureTuning(volumeSteps = 3))
            TouchFreeNavigationPreset.apply(store)
            assertEquals(AirAction.VOLUME_UP, store.mapping(RegisteredGesture.SHAKA))
            assertEquals(3, store.tuning(RegisteredGesture.SHAKA).volumeSteps)
            assertEquals(AirAction.TAP, store.mapping(RegisteredGesture.THUMB_UP))
            for (action in listOf(AirAction.NEXT_ITEM, AirAction.PREVIOUS_ITEM, AirAction.SELECT_ITEM,
                AirAction.SCROLL_UP, AirAction.SCROLL_DOWN, AirAction.TOGGLE_POINTER)) {
                val pose = RegisteredGesture.entries.first { store.mapping(it) == action }
                assertTrue(GestureActionPolicy.isClassifierActionEnabled(true, action, false, pose))
            }
            assertTrue(RegisteredGesture.entries.count { store.mapping(it) != AirAction.NONE } >= 12)
            val once = RegisteredGesture.entries.map(store::mapping)
            TouchFreeNavigationPreset.apply(store)
            assertEquals(once, RegisteredGesture.entries.map(store::mapping))
        } finally {
            context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
