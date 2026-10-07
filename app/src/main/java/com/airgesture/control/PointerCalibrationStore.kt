package com.airgesture.control

import android.content.Context

/** Persists validated calibration independently for each hand and display orientation. */
class PointerCalibrationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var cachedEncoded: String? = null
    private var cachedProfile: PointerCalibrationProfile? = null

    fun profile(hand: ControlHandPreference = ControlHandPreference.EITHER, landscape: Boolean = false): PointerCalibrationProfile =
        decodedProfile(hand, landscape) ?: PointerCalibrationProfile.DEFAULT

    fun hasValidProfile(hand: ControlHandPreference = ControlHandPreference.EITHER, landscape: Boolean = false): Boolean =
        decodedProfile(hand, landscape) != null

    fun enabled(hand: ControlHandPreference = ControlHandPreference.EITHER, landscape: Boolean = false): Boolean =
        preferences.getBoolean(enabledKey(hand, landscape), false) && hasValidProfile(hand, landscape)

    fun activeProfile(hand: ControlHandPreference, landscape: Boolean): PointerCalibrationProfile? =
        if (enabled(hand, landscape)) profile(hand, landscape) else null

    fun save(profile: PointerCalibrationProfile, hand: ControlHandPreference = ControlHandPreference.EITHER, landscape: Boolean = false): Boolean {
        if (!profile.isValid()) return false
        preferences.edit()
            .putString(profileKey(hand, landscape), PointerCalibrationProfileCodec.encode(profile))
            .putBoolean(enabledKey(hand, landscape), true)
            .apply()
        AirRuntime.invalidatePendingActions()
        return true
    }

    fun setEnabled(enabled: Boolean, hand: ControlHandPreference = ControlHandPreference.EITHER, landscape: Boolean = false): Boolean {
        if (enabled && !hasValidProfile(hand, landscape)) return false
        preferences.edit().putBoolean(enabledKey(hand, landscape), enabled).apply()
        AirRuntime.invalidatePendingActions()
        return true
    }

    fun reset(hand: ControlHandPreference = ControlHandPreference.EITHER, landscape: Boolean = false) {
        preferences.edit()
            .remove(profileKey(hand, landscape))
            .remove(enabledKey(hand, landscape))
            .apply()
        AirRuntime.invalidatePendingActions()
    }

    private fun decodedProfile(hand: ControlHandPreference, landscape: Boolean): PointerCalibrationProfile? {
        val encoded = preferences.getString(profileKey(hand, landscape), null)
        if (encoded != cachedEncoded) {
            cachedProfile = PointerCalibrationProfileCodec.decode(encoded)
            cachedEncoded = encoded
        }
        return cachedProfile
    }

    // Preserve the legacy profile only for Either/portrait; never copy it into another context.
    private fun profileKey(hand: ControlHandPreference, landscape: Boolean): String =
        if (hand == ControlHandPreference.EITHER && !landscape) "pointer_profile"
        else "pointer_profile_${hand.name.lowercase()}_${if (landscape) "landscape" else "portrait"}"

    private fun enabledKey(hand: ControlHandPreference, landscape: Boolean): String =
        profileKey(hand, landscape) + "_enabled"

    private companion object {
        const val PREFERENCES_NAME = "aergis_calibration"
    }
}
