package com.airgesture.control

/**
 * User-calibrated normalized pointer bounds.
 *
 * The profile is intentionally opt-in: the legacy mapper remains the default until
 * replay and device evidence support enabling calibrated mapping in production.
 */
data class PointerCalibrationProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val left: Float = DEFAULT_LEFT,
    val right: Float = DEFAULT_RIGHT,
    val top: Float = DEFAULT_TOP,
    val bottom: Float = DEFAULT_BOTTOM,
    val mirrorX: Boolean = true,
    val curveX: Float = 1f,
    val curveY: Float = 1f
) {
    fun isValid(): Boolean =
        schemaVersion == CURRENT_SCHEMA_VERSION &&
            left in 0f..1f &&
            right in 0f..1f &&
            top in 0f..1f &&
            bottom in 0f..1f &&
            right - left >= MIN_ACTIVE_SPAN &&
            bottom - top >= MIN_ACTIVE_SPAN

    fun validatedOrDefault(): PointerCalibrationProfile =
        if (isValid()) this else DEFAULT

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val DEFAULT_LEFT = 0.10f
        const val DEFAULT_RIGHT = 0.90f
        const val DEFAULT_TOP = 0.08f
        const val DEFAULT_BOTTOM = 0.92f
        private const val MIN_ACTIVE_SPAN = 0.10f
        val DEFAULT = PointerCalibrationProfile()
        val COMFORTABLE_REACH = DEFAULT
    }
}

