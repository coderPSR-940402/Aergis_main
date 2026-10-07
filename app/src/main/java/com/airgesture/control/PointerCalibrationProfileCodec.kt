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
            profile.mirrorX,
            profile.curveX,
            profile.curveY
        ).joinToString(separator = "|")
    }

    fun decode(encoded: String?): PointerCalibrationProfile? {
        if (encoded.isNullOrBlank()) return null
        val parts = encoded.split('|')
        val legacy = parts.size == 6 && parts[0] == "2"
        if (!legacy && (parts.size != 8 || parts[0] != "3")) return null
        return runCatching {
            PointerCalibrationProfile(
                schemaVersion = PointerCalibrationProfile.CURRENT_SCHEMA_VERSION,
                left = parts[1].toFloat(),
                right = parts[2].toFloat(),
                top = parts[3].toFloat(),
                bottom = parts[4].toFloat(),
                mirrorX = parts[5].toBooleanStrict(),
                curveX = if (legacy) 1f else parts[6].toFloat(),
                curveY = if (legacy) 1f else parts[7].toFloat()
            ).takeIf { it.isValid() }
        }.getOrNull()
    }
}
