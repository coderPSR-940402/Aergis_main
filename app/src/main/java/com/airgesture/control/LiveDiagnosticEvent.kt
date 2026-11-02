package com.airgesture.control

/**
 * Compact monotonic telemetry sample retained only in memory by the live diagnostics timeline.
 * Additional event fields are introduced only when their promotion behavior is implemented.
 */
internal data class LiveDiagnosticSample(
    val uptimeMs: Long,
    val wallClockMs: Long
) {
    init {
        require(uptimeMs >= 0L) { "uptimeMs must be non-negative" }
        require(wallClockMs >= 0L) { "wallClockMs must be non-negative" }
    }
}
