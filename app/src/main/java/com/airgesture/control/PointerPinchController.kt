package com.airgesture.control

import kotlin.math.hypot

/** UI clients may observe phases without controlling gesture injection. */
enum class PointerInteractionPhase { IDLE, AIMING, PINCHING, PRESSED, HOLDING, DRAGGING }

/** Final screen-space stage, after the selected pointer filter and calibration. */
internal class PointerPinchController(
    private val dragThreshold: Float = 0.025f,
    private val holdingMs: Long = 500L,
    private val releaseBlendMs: Long = 80L,
    private val dragConfirmationMs: Long = 50L
) {
    data class Result(val point: PointerCoordinateMapper.Point, val phase: PointerInteractionPhase)

    private var lastPoint: PointerCoordinateMapper.Point? = null
    private var anchor: PointerCoordinateMapper.Point? = null
    private var pressOrigin: PointerCoordinateMapper.Point? = null
    private var pressedAt: Long? = null
    private var dragging = false
    private var dragCandidateSince: Long? = null
    private var releaseAt: Long? = null
    private var releasePoint: PointerCoordinateMapper.Point? = null

    fun update(candidate: PointerCoordinateMapper.Point, approaching: Boolean, pressed: Boolean,
        timestampMs: Long, screenAspectRatio: Float = 1f): Result {
        var phase = PointerInteractionPhase.AIMING
        val point = if (approaching || pressed) {
            releaseAt = null
            releasePoint = null
            if (!pressed && pressedAt != null) {
                // Contact can end while fingers remain inside the approach threshold.
                // Preserve the landing point, but never carry a drag/hold into a new press.
                anchor = lastPoint
                pressOrigin = null
                pressedAt = null
                dragging = false
                dragCandidateSince = null
            }
            val target = anchor ?: (lastPoint ?: candidate).also { anchor = it }
            phase = PointerInteractionPhase.PINCHING
            if (pressed) {
                val origin = pressOrigin ?: candidate.also {
                    pressOrigin = it
                    pressedAt = timestampMs
                }
                val dx = candidate.x - origin.x
                val dy = candidate.y - origin.y
                val aspect = screenAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
                val distance = hypot(dx * maxOf(1f, aspect), dy * maxOf(1f, 1f / aspect))
                if (!dragging) {
                    if (distance >= dragThreshold) {
                        val since = dragCandidateSince ?: timestampMs.also { dragCandidateSince = it }
                        // Large intentional movement stays immediate; a near-threshold spike does not drag.
                        if (distance >= dragThreshold * 2.5f || timestampMs - since >= dragConfirmationMs) dragging = true
                    } else dragCandidateSince = null
                }
                phase = when {
                    dragging -> PointerInteractionPhase.DRAGGING
                    timestampMs - (pressedAt ?: timestampMs) >= holdingMs -> PointerInteractionPhase.HOLDING
                    else -> PointerInteractionPhase.PRESSED
                }
                if (dragging) PointerCoordinateMapper.Point(
                    (target.x + dx).coerceIn(0f, 1f), (target.y + dy).coerceIn(0f, 1f)
                ) else target
            } else target
        } else {
            if (anchor != null) {
                releasePoint = lastPoint
                releaseAt = timestampMs
                anchor = null
                pressOrigin = null
                pressedAt = null
                dragging = false
                dragCandidateSince = null
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
        pressOrigin = null
        pressedAt = null
        dragging = false
        dragCandidateSince = null
        releaseAt = null
        releasePoint = null
    }
}
