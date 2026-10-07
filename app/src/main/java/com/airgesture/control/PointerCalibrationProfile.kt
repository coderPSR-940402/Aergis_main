package com.airgesture.control

/**
 * User-calibrated normalized pointer bounds.
 *
 * Bounds describe comfortable reach in upright, mirrored user coordinates.
 * A curve above 1 reduces movement near the center while retaining screen edges.
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
            bottom - top >= MIN_ACTIVE_SPAN &&
            curveX in MIN_CURVE..MAX_CURVE && curveY in MIN_CURVE..MAX_CURVE

    fun validatedOrDefault(): PointerCalibrationProfile =
        if (isValid()) this else DEFAULT

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        const val DEFAULT_LEFT = 0.10f
        const val DEFAULT_RIGHT = 0.90f
        const val DEFAULT_TOP = 0.08f
        const val DEFAULT_BOTTOM = 0.92f
        const val MIN_ACTIVE_SPAN = 0.10f
        const val MIN_CURVE = 0.75f
        const val MAX_CURVE = 1.80f
        val DEFAULT = PointerCalibrationProfile()
        // Starting point for the reported lower-frame loss; users can tune every bound live.
        val COMFORTABLE_REACH = PointerCalibrationProfile(
            left = 0.15f, right = 0.85f, top = 0.12f, bottom = 0.65f
        )
    }
}
