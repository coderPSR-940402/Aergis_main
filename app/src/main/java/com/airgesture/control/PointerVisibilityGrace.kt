package com.airgesture.control

/** Cursor-only retention; a held coordinate is never fresh action or calibration evidence. */
internal class PointerVisibilityGrace(private val graceMs: Long = 130L) {
    private var lastPoint: PointerCoordinateMapper.Point? = null
    private var lastSeenMs: Long? = null

    fun record(point: PointerCoordinateMapper.Point, timestampMs: Long) {
        if (!point.x.isFinite() || !point.y.isFinite()) {
            reset()
            return
        }
        lastPoint = point
        lastSeenMs = timestampMs
    }

    fun heldAt(timestampMs: Long): PointerCoordinateMapper.Point? {
        val lastSeen = lastSeenMs ?: return null
        return lastPoint.takeIf { timestampMs >= lastSeen && timestampMs - lastSeen <= graceMs }
    }

    fun reset() {
        lastPoint = null
        lastSeenMs = null
    }
}
