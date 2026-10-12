package com.airgesture.control

/** Maps pointer frames through the currently selected calibration. */
internal class LivePointerMapper(private val readProfile: () -> PointerCalibrationProfile?) {
    fun snapshot(): PointerCalibrationProfile? = readProfile()?.validatedOrDefault()?.let { profile ->
        // A tiny captured reach amplifies landmark noise into screen-wide jumps. Expand around
        // the user's center without overwriting their saved profile or changing the mirror.
        fun bounds(low: Float, high: Float): Pair<Float, Float> {
            val span = maxOf(high - low, MIN_CONTROL_SPAN)
            val start = ((low + high - span) / 2f).coerceIn(0f, 1f - span)
            return start to start + span
        }
        val (left, right) = bounds(profile.left, profile.right)
        val (top, bottom) = bounds(profile.top, profile.bottom)
        profile.copy(left = left, right = right, top = top, bottom = bottom)
    }

    private companion object { const val MIN_CONTROL_SPAN = .35f }

    fun map(x: Float, y: Float, profile: PointerCalibrationProfile? = snapshot()): PointerCoordinateMapper.Point =
        profile?.let { PointerCoordinateMapper.map(x, y, it) }
            ?: PointerCoordinateMapper.map(x, y)
}
