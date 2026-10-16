package com.airgesture.control

import android.content.Context

class ActionMappingStore(context: Context) {
    private val prefs = context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE)

    fun pointerEnabled(): Boolean = prefs.getBoolean(KEY_POINTER, true)
    fun setPointerEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_POINTER, enabled).apply()

    fun gesturesEnabled(): Boolean = prefs.getBoolean(KEY_GESTURES, true)
    fun setGesturesEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_GESTURES, enabled).apply()

    fun mapping(action: AirAction): AirAction = runCatching {
        AirAction.valueOf(prefs.getString("map_${action.name}", action.name) ?: action.name)
    }.getOrDefault(action)

    fun setMapping(source: AirAction, target: AirAction) =
        prefs.edit().putString("map_${source.name}", target.name).apply()

    fun mapping(gesture: RegisteredGesture): AirAction {
        val fallback = gesture.legacySource?.let(::mapping) ?: gesture.defaultAction
        return runCatching {
            AirAction.valueOf(prefs.getString("gesture_${gesture.name}", fallback.name) ?: fallback.name)
        }.getOrDefault(fallback)
    }

    fun setMapping(gesture: RegisteredGesture, target: AirAction) =
        prefs.edit().putString("gesture_${gesture.name}", target.name).apply()

    fun tuning(gesture: RegisteredGesture): GestureTuning {
        val key = "tune_${gesture.name}_"
        return runCatching { GestureTuning(
            prefs.getLong(key + "hold", 100L), prefs.getLong(key + "repeat", 700L),
            prefs.getInt(key + "confidence", 65), prefs.getInt(key + "volume", 1),
            prefs.getInt(key + "scroll", 50), prefs.getLong(key + "longPress", 650L)
        ).bounded() }.getOrDefault(GestureTuning())
    }

    fun saveBinding(gesture: RegisteredGesture, target: AirAction, tuning: GestureTuning) {
        val value = tuning.bounded()
        val key = "tune_${gesture.name}_"
        prefs.edit().putString("gesture_${gesture.name}", target.name)
            .putLong(key + "hold", value.holdMs).putLong(key + "repeat", value.repeatDelayMs)
            .putInt(key + "confidence", value.confidencePercent).putInt(key + "volume", value.volumeSteps)
            .putInt(key + "scroll", value.scrollPercent).putLong(key + "longPress", value.longPressMs).apply()
    }

    fun handPreference(): ControlHandPreference = runCatching {
        ControlHandPreference.valueOf(prefs.getString(KEY_HAND_PREF, ControlHandPreference.EITHER.name) ?: ControlHandPreference.EITHER.name)
    }.getOrDefault(ControlHandPreference.EITHER)

    fun setHandPreference(preference: ControlHandPreference) =
        prefs.edit().putString(KEY_HAND_PREF, preference.name).apply()

    companion object {
        private const val KEY_POINTER = "pointer_enabled"
        private const val KEY_GESTURES = "gestures_enabled"
        private const val KEY_HAND_PREF = "hand_preference"
    }
}
