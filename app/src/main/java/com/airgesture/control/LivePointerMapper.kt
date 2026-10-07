package com.airgesture.control

/** Maps pointer frames through the currently selected calibration. */
internal class LivePointerMapper(private val readProfile: () -> PointerCalibrationProfile?) {
    private val profile = readProfile()

    fun map(x: Float, y: Float): PointerCoordinateMapper.Point =
        profile?.let { PointerCoordinateMapper.map(x, y, it) }
            ?: PointerCoordinateMapper.map(x, y)
}
