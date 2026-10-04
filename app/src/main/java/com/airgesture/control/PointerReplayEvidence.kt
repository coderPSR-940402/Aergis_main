package com.airgesture.control

/**
 * Metadata and aggregate metrics for a replay benchmark result.
 *
 * This intentionally contains no camera frames, landmarks, package names, or
 * user content. It is suitable for attaching to a CI artifact or support log.
 */
data class PointerReplayEvidence(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val commitSha: String,
    val traceId: String,
    val mapperProfile: String,
    val evidenceType: EvidenceType,
    val deviceModel: String? = null,
    val frameCount: Int,
    val durationMs: Long,
    val meanAbsoluteError: Float? = null,
    val p95AbsoluteError: Float? = null,
    val maxAbsoluteError: Float? = null,
    val meanOutputStep: Float? = null,
    val maxOutputStep: Float? = null
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Unsupported evidence schema" }
        require(commitSha.isNotBlank()) { "commitSha must not be blank" }
        require(traceId.isNotBlank()) { "traceId must not be blank" }
        require(mapperProfile.isNotBlank()) { "mapperProfile must not be blank" }
        require(frameCount > 0) { "frameCount must be positive" }
        require(durationMs >= 0L) { "durationMs must be non-negative" }
        listOf(meanAbsoluteError, p95AbsoluteError, maxAbsoluteError, meanOutputStep, maxOutputStep)
            .filterNotNull()
            .forEach { require(it.isFinite() && it >= 0f) { "metrics must be finite and non-negative" } }
    }

    enum class EvidenceType { REPOSITORY_COMPLETE, DEVICE_DEPENDENT }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Stable line-oriented encoding for logs and CI artifacts. */
object PointerReplayEvidenceCodec {
    private const val MAX_ENCODED_CHARS = 8_192

    fun fromReport(
        report: PointerReplayReport,
        commitSha: String,
        mapperProfile: String,
        evidenceType: PointerReplayEvidence.EvidenceType,
        deviceModel: String? = null
    ): PointerReplayEvidence? {
        val metrics = report.metrics ?: return null
        return PointerReplayEvidence(
            commitSha = commitSha,
            traceId = metrics.traceId,
            mapperProfile = mapperProfile,
            evidenceType = evidenceType,
            deviceModel = deviceModel,
            frameCount = metrics.frameCount,
            durationMs = metrics.durationMs,
            meanAbsoluteError = metrics.meanAbsoluteError,
            p95AbsoluteError = metrics.p95AbsoluteError,
            maxAbsoluteError = metrics.maxAbsoluteError,
            meanOutputStep = metrics.meanOutputStep,
            maxOutputStep = metrics.maxOutputStep
        )
    }

    fun encode(evidence: PointerReplayEvidence): String {
        val values = linkedMapOf(
            "schemaVersion" to evidence.schemaVersion.toString(),
            "commitSha" to evidence.commitSha,
            "traceId" to evidence.traceId,
            "mapperProfile" to evidence.mapperProfile,
            "evidenceType" to evidence.evidenceType.name,
            "deviceModel" to evidence.deviceModel,
            "frameCount" to evidence.frameCount.toString(),
            "durationMs" to evidence.durationMs.toString(),
            "meanAbsoluteError" to evidence.meanAbsoluteError?.toString(),
            "p95AbsoluteError" to evidence.p95AbsoluteError?.toString(),
            "maxAbsoluteError" to evidence.maxAbsoluteError?.toString(),
            "meanOutputStep" to evidence.meanOutputStep?.toString(),
            "maxOutputStep" to evidence.maxOutputStep?.toString()
        )
        val encoded = values.entries.joinToString("\n") { (key, value) ->
            "$key=${escape(value.orEmpty())}"
        } + "\n"
        require(encoded.length <= MAX_ENCODED_CHARS) { "Evidence payload exceeds size limit" }
        return encoded
    }

    fun decode(encoded: String): PointerReplayEvidence {
        require(encoded.length <= MAX_ENCODED_CHARS) { "Evidence payload exceeds size limit" }
        val values = encoded.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) invalid("Malformed evidence line")
                line.substring(0, separator) to unescape(line.substring(separator + 1))
            }
            .toMap()
        fun required(key: String): String = values[key]?.takeIf { it.isNotBlank() }
            ?: invalid("Missing evidence field: $key")
        fun optionalFloat(key: String): Float? {
            val raw = values[key]?.takeIf { it.isNotBlank() } ?: return null
            return raw.toFloatOrNull() ?: invalid("Invalid $key")
        }
        return PointerReplayEvidence(
            schemaVersion = required("schemaVersion").toIntOrNull() ?: invalid("Invalid schemaVersion"),
            commitSha = required("commitSha"),
            traceId = required("traceId"),
            mapperProfile = required("mapperProfile"),
            evidenceType = runCatching {
                PointerReplayEvidence.EvidenceType.valueOf(required("evidenceType"))
            }.getOrElse { invalid("Invalid evidenceType") },
            deviceModel = values["deviceModel"]?.takeIf { it.isNotBlank() },
            frameCount = required("frameCount").toIntOrNull() ?: invalid("Invalid frameCount"),
            durationMs = required("durationMs").toLongOrNull() ?: invalid("Invalid durationMs"),
            meanAbsoluteError = optionalFloat("meanAbsoluteError"),
            p95AbsoluteError = optionalFloat("p95AbsoluteError"),
            maxAbsoluteError = optionalFloat("maxAbsoluteError"),
            meanOutputStep = optionalFloat("meanOutputStep"),
            maxOutputStep = optionalFloat("maxOutputStep")
        )
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("=", "\\=")

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var escaped = false
        value.forEach { character ->
            if (escaped) {
                result.append(if (character == 'n') '\n' else character)
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else {
                result.append(character)
            }
        }
        if (escaped) invalid("Malformed escape sequence")
        return result.toString()
    }
}
