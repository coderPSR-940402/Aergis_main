package com.airgesture.control

/** Compact, versioned representation for app-private calibration persistence. */
internal object PointerCalibrationProfileCodec {
    fun encode(profile: PointerCalibrationProfile): String {
        require(profile.isValid()) { "Cannot encode an invalid calibration profile" }
        return listOf(
            profile.schemaVersion,
            profile.left,
            profile.right,
            profile.top,
            profile.bottom,
            profile.mirrorX
        ).joinToString(separator = "|")
    }

    fun decode(encoded: String?): PointerCalibrationProfile? {
        if (encoded.isNullOrBlank()) return null
        val parts = encoded.split('|')
        if (parts.size != 6) return null
        return runCatching {
            PointerCalibrationProfile(
                schemaVersion = parts[0].toInt(),
                left = parts[1].toFloat(),
                right = parts[2].toFloat(),
                top = parts[3].toFloat(),
                bottom = parts[4].toFloat(),
                mirrorX = parts[5].toBooleanStrict()
            ).takeIf { it.isValid() }
        }.getOrNull()
    }
}
