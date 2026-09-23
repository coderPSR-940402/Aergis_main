package com.airgesture.control

import android.content.Context

class ActionMappingStore(context: Context) {
    private val prefs = context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE)

    fun pointerEnabled(): Boolean = prefs.getBoolean(KEY_POINTER, true)
    fun setPointerEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_POINTER, enabled).apply()

    fun mapping(action: AirAction): AirAction = runCatching {
        AirAction.valueOf(prefs.getString("map_${action.name}", action.name) ?: action.name)
    }.getOrDefault(action)

    fun setMapping(source: AirAction, target: AirAction) = prefs.edit().putString("map_${source.name}", target.name).apply()

    companion object {
        private const val KEY_POINTER = "pointer_enabled"
    }
}

object DefaultMappings {
    fun create(): Map<AirAction, AirAction> = AirAction.entries.associateWith { it }
}
