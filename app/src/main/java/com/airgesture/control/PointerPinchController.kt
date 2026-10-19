package com.airgesture.control

/** UI clients may observe phases without controlling gesture injection. */
enum class PointerInteractionPhase { IDLE, AIMING, PINCHING, PRESSED, HOLDING, DRAGGING }

/** Final screen-space stage, after the selected pointer filter and calibration. */
internal class PointerPinchController(
    private val releaseBlendMs: Long = 80L
) {
    data class Result(val point: PointerCoordinateMapper.Point, val phase: PointerInteractionPhase)

    private var lastPoint: PointerCoordinateMapper.Point? = null
    private var anchor: PointerCoordinateMapper.Point? = null
    private var releaseAt: Long? = null
    private var releasePoint: PointerCoordinateMapper.Point? = null

    fun update(candidate: PointerCoordinateMapper.Point, approaching: Boolean, pressed: Boolean,
        timestampMs: Long): Result {
        var phase = PointerInteractionPhase.AIMING
        val point = if (approaching || pressed) {
            releaseAt = null
            releasePoint = null
            val target = anchor ?: (lastPoint ?: candidate).also { anchor = it }
            // A consumed pinch holds the click target. Duration or travel never turns
            // it into a different action; long press is a separate mapped command.
            phase = if (pressed) PointerInteractionPhase.PRESSED else PointerInteractionPhase.PINCHING
            target
        } else {
            if (anchor != null) {
                releasePoint = lastPoint
                releaseAt = timestampMs
                anchor = null
            }
            val start = releaseAt
            val from = releasePoint
            if (start != null && from != null) {
                val fraction = ((timestampMs - start).toFloat() / releaseBlendMs).coerceIn(0f, 1f)
                if (fraction >= 1f) { releaseAt = null; releasePoint = null }
                PointerCoordinateMapper.Point(from.x + (candidate.x - from.x) * fraction,
                    from.y + (candidate.y - from.y) * fraction)
            } else candidate
        }
        lastPoint = point
        return Result(point, phase)
    }

    fun reset() {
        lastPoint = null
        anchor = null
        releaseAt = null
        releasePoint = null
    }
}
