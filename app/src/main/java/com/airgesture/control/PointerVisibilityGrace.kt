package com.airgesture.control

/** Cursor-only retention; a held coordinate is never fresh action or calibration evidence. */
internal class PointerVisibilityGrace(private val graceMs: Long = 130L) {
    private var lastPoint: PointerCoordinateMapper.Point? = null
    private var lastSeenMs: Long? = null
    private var cadenceMs = 0L

    fun record(point: PointerCoordinateMapper.Point, timestampMs: Long) {
        if (!point.x.isFinite() || !point.y.isFinite()) {
            reset()
            return
        }
        lastSeenMs?.let { previous ->
            val interval = timestampMs - previous
            if (interval in 1L..350L) cadenceMs = interval
        }
        lastPoint = point
        lastSeenMs = timestampMs
    }

    fun heldAt(timestampMs: Long): PointerCoordinateMapper.Point? {
        val lastSeen = lastSeenMs ?: return null
        // At 4–5 fps one missing result already exceeds the old fixed 130 ms grace.
        // Hold only the last visible position, for at most 500 ms. Actions are cancelled
        // separately by the engine on the first missing/rejected result.
        val allowedGap = maxOf(graceMs, (cadenceMs * 2 + 50).coerceAtMost(500L))
        return lastPoint.takeIf { timestampMs >= lastSeen && timestampMs - lastSeen <= allowedGap }
    }

    fun reset() {
        lastPoint = null
        lastSeenMs = null
        cadenceMs = 0L
    }
}
