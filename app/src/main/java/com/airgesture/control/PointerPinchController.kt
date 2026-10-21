package com.airgesture.control

import kotlin.math.hypot

/** UI clients may observe phases without controlling gesture injection. */
enum class PointerInteractionPhase { IDLE, AIMING, PINCHING, PRESSED, HOLDING, DRAGGING }

/** Final screen-space stage, after the selected pointer filter and calibration. */
internal class PointerPinchController(
    private val releaseBlendMs: Long = 80L,
    private val suspiciousReleaseJump: Float = .30f,
    private val suspiciousReleaseHoldMs: Long = 120L,
    private val suspiciousReleaseStep: Float = .04f
) {
    data class Result(val point: PointerCoordinateMapper.Point, val phase: PointerInteractionPhase)

    private var lastPoint: PointerCoordinateMapper.Point? = null
    private var anchor: PointerCoordinateMapper.Point? = null
    private var releaseAt: Long? = null
    private var releasePoint: PointerCoordinateMapper.Point? = null
    private var guardedRelease = false
    private var releaseGuardUntil = 0L
    private var releaseGuardPoint: PointerCoordinateMapper.Point? = null

    fun update(candidate: PointerCoordinateMapper.Point, approaching: Boolean, pressed: Boolean,
        timestampMs: Long): Result {
        var phase = PointerInteractionPhase.AIMING
        val point = if (approaching || pressed) {
            clearRelease()
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
                guardedRelease = false
                releaseGuardUntil = 0L
                releaseGuardPoint = null
            }
            val start = releaseAt
            val from = releasePoint
            if (start != null && from != null) {
                // MediaPipe may refit the hand as the pinch opens. If that refit puts the
                // index estimate far from the frozen click target, do not expose the
                // discontinuity. Brief false-open bursts retain the exact target; a real
                // sustained release catches up in bounded steps instead of one huge jump.
                if (!guardedRelease && distance(from, candidate) >= suspiciousReleaseJump) {
                    guardedRelease = true
                    releaseGuardUntil = timestampMs + suspiciousReleaseHoldMs
                    releaseGuardPoint = lastPoint ?: from
                }
                if (guardedRelease) {
                    val held = releaseGuardPoint ?: from
                    if (timestampMs < releaseGuardUntil) held else {
                        val recovered = stepToward(lastPoint ?: held, candidate, suspiciousReleaseStep)
                        if (recovered == candidate) clearRelease()
                        recovered
                    }
                } else {
                    val fraction = ((timestampMs - start).toFloat() / releaseBlendMs).coerceIn(0f, 1f)
                    if (fraction >= 1f) clearRelease()
                    PointerCoordinateMapper.Point(from.x + (candidate.x - from.x) * fraction,
                        from.y + (candidate.y - from.y) * fraction)
                }
            } else candidate
        }
        lastPoint = point
        return Result(point, phase)
    }

    fun reset() {
        lastPoint = null
        anchor = null
        clearRelease()
    }

    private fun clearRelease() {
        releaseAt = null
        releasePoint = null
        guardedRelease = false
        releaseGuardUntil = 0L
        releaseGuardPoint = null
    }

    private fun distance(a: PointerCoordinateMapper.Point, b: PointerCoordinateMapper.Point): Float =
        hypot(a.x - b.x, a.y - b.y)

    private fun stepToward(from: PointerCoordinateMapper.Point, to: PointerCoordinateMapper.Point,
        maxStep: Float): PointerCoordinateMapper.Point {
        val distance = distance(from, to)
        if (distance <= maxStep || distance == 0f) return to
        val fraction = maxStep / distance
        return PointerCoordinateMapper.Point(from.x + (to.x - from.x) * fraction,
            from.y + (to.y - from.y) * fraction)
    }
}
