package com.airgesture.control

import android.content.Context

/** Persists only validated calibration profiles and keeps activation opt-in. */
class PointerCalibrationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun profile(): PointerCalibrationProfile =
        PointerCalibrationProfileCodec.decode(preferences.getString(KEY_PROFILE, null))
            ?: PointerCalibrationProfile.DEFAULT

    fun hasValidProfile(): Boolean =
        PointerCalibrationProfileCodec.decode(preferences.getString(KEY_PROFILE, null)) != null

    fun enabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false) && hasValidProfile()

    fun save(profile: PointerCalibrationProfile): Boolean {
        if (!profile.isValid()) return false
        preferences.edit()
            .putString(KEY_PROFILE, PointerCalibrationProfileCodec.encode(profile))
            .putBoolean(KEY_ENABLED, true)
            .apply()
        return true
    }

    fun setEnabled(enabled: Boolean): Boolean {
        if (enabled && !hasValidProfile()) return false
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        return true
    }

    fun reset() {
        preferences.edit()
            .remove(KEY_PROFILE)
            .remove(KEY_ENABLED)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "aergis_calibration"
        const val KEY_PROFILE = "pointer_profile"
        const val KEY_ENABLED = "pointer_profile_enabled"
    }
}
