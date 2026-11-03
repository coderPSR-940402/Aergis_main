package com.airgesture.control

/** Serializes promoted diagnostics into a compact, redacted line for the phone-side collector. */
internal class LiveDiagnosticRelay(
    private val maxLineChars: Int = 2_048
) {
    init {
        require(maxLineChars >= PREFIX.length + 2) { "maxLineChars too small" }
    }

    fun serialize(snapshot: LiveDiagnosticSnapshot): String {
        val trigger = snapshot.trigger
        val fields = ArrayList<String>(14)
        fields += "\"schema\":1"
        fields += "\"kind\":${quote(trigger.kind.name)}"
        fields += "\"severity\":${quote(trigger.severity.name)}"
        fields += "\"event_uptime_ms\":${trigger.uptimeMs}"
        fields += "\"wall_clock_ms\":${trigger.wallClockMs}"
        trigger.requestId?.let { fields += "\"request_id\":$it" }
        trigger.action?.let { fields += "\"action\":${quote(it)}" }
        trigger.reasonCode?.let { fields += "\"reason_code\":${quote(it)}" }
        trigger.sessionEpoch?.let { fields += "\"session_epoch\":$it" }
        trigger.inputEpoch?.let { fields += "\"input_epoch\":$it" }
        trigger.frozenTargetX?.let { if (it.isFinite()) fields += "\"target_x\":$it" }
        trigger.frozenTargetY?.let { if (it.isFinite()) fields += "\"target_y\":$it" }
        fields += "\"before_count\":${snapshot.before.size}"
        fields += "\"after_count\":${snapshot.after.size}"

        val full = PREFIX + "{" + fields.joinToString(",") + "}"
        if (full.length <= maxLineChars) return full

        val minimal = PREFIX + "{" + listOf(
            "\"schema\":1",
            "\"kind\":${quote(trigger.kind.name)}",
            "\"severity\":${quote(trigger.severity.name)}",
            trigger.requestId?.let { "\"request_id\":$it" },
            trigger.action?.let { "\"action\":${quote(it.take(32))}" },
            trigger.reasonCode?.let { "\"reason_code\":${quote(it.take(48))}" }
        ).filterNotNull().joinToString(",") + "}"
        if (minimal.length <= maxLineChars) return minimal

        val bare = PREFIX + "{\"schema\":1,\"kind\":${quote(trigger.kind.name)}}"
        return if (bare.length <= maxLineChars) bare else PREFIX + "{}"
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) {
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    companion object {
        const val PREFIX = "AERGISTEL|"
    }
}
