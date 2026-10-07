package com.airgesture.control

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Time-aware, non-predictive fingertip smoother.
 *
 * Pointer X/Y entering this filter is already the calibrated MediaPipe index
 * fingertip (landmark 8). This filter must never substitute palm/wrist data,
 * extrapolate beyond the measured fingertip, or introduce frame-rate-dependent
 * stick/slip.
 *
 * Design:
 * - low-speed motion gets stronger smoothing to suppress landmark shimmer;
 * - deliberate motion raises the cutoff continuously for low perceived lag;
 * - very small raw changes receive a continuous (not stepped) jitter damping;
 * - a single large landmark displacement is step-limited until it persists;
 * - sustained large fingertip travel converges aggressively on confirmation;
 * - output is always between the previous cursor and latest measured fingertip,
 *   so prediction overshoot is impossible.
 *
 * Recovered from AERMOTUS commit 92eb773ab516df8955f987b874c6041b4d6e8cb2.
 * Constants and motion math are preserved for an explicit device comparison.
 * Historical nested One Euro shadow/metrics are omitted to avoid duplicate work.
 */
internal class Vc49PointerMotionFilter(
    private val speedCutoffHz: Float = 6.0f,
    private val minPositionCutoffHz: Float = 3.6f,
    private val speedGain: Float = 2.8f,
    private val maxSpeedCutoffBoostHz: Float = 18.0f,
    private val jitterRadius: Float = 0.0045f,
    private val largeMoveThreshold: Float = 0.045f,
    private val largeMoveConfirmWindowMs: Long = 140L,
    private val unconfirmedLargeMoveMaxStep: Float = 0.028f
) {
    private var trackedX: Float? = null
    private var trackedY: Float? = null
    private var previousRawX: Float? = null
    private var previousRawY: Float? = null
    private var lastTimestampMs: Long? = null
    private var filteredSpeed = 0f

    private var pendingLargeMoveX: Float? = null
    private var pendingLargeMoveY: Float? = null
    private var pendingLargeMoveTimestampMs: Long? = null


    fun update(
        rawX: Float,
        rawY: Float,
        timestampMs: Long,
        confidence: Float,
        resultAgeMs: Long = 0L
    ): Pair<Float, Float> {
        val previousX = trackedX
        val previousY = trackedY
        if (!rawX.isFinite() || !rawY.isFinite() ||
            (lastTimestampMs != null && timestampMs <= lastTimestampMs!!)) {
            return (previousX ?: 0.5f) to (previousY ?: 0.5f)
        }

        val safeX =
            rawX.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: previousX
                ?: 0.5f
        val safeY =
            rawY.takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: previousY
                ?: 0.5f

        val previousTime = lastTimestampMs
        if (
            previousX == null ||
            previousY == null ||
            previousTime == null
        ) {
            trackedX = safeX
            trackedY = safeY
            previousRawX = safeX
            previousRawY = safeY
            lastTimestampMs = timestampMs
            filteredSpeed = 0f
            clearPendingLargeMove()

            return safeX to safeY
        }

        // MediaPipe result timestamps are normally monotonic. Clamp the delta
        // only to keep the filter numerically stable across stalls/restarts;
        // no result-age prediction is performed.
        val dtSeconds =
            (timestampMs - previousTime)
                .coerceIn(8L, 120L) /
                1000f

        val oldRawX = previousRawX ?: safeX
        val oldRawY = previousRawY ?: safeY
        val rawDistance =
            hypot(
                safeX - oldRawX,
                safeY - oldRawY
            )
        val sampleSpeed = rawDistance / dtSeconds

        val speedAlpha =
            alphaForCutoff(
                cutoffHz = speedCutoffHz,
                dtSeconds = dtSeconds
            )
        filteredSpeed +=
            (sampleSpeed - filteredSpeed) * speedAlpha

        val confidenceScale =
            confidence.coerceIn(0.35f, 1f)
        val positionCutoff =
            (
                minPositionCutoffHz +
                    min(
                        filteredSpeed * speedGain,
                        maxSpeedCutoffBoostHz
                    )
                ) *
                (0.92f + 0.08f * confidenceScale)

        var alpha =
            alphaForCutoff(
                cutoffHz = positionCutoff,
                dtSeconds = dtSeconds
            )

        // Continuous micro-jitter damping avoids hard alpha steps that make
        // slow fingertip motion stick and then jump.
        if (rawDistance < jitterRadius) {
            val t =
                (rawDistance / jitterRadius)
                    .coerceIn(0f, 1f)
            val smoothStep =
                t * t * (3f - 2f * t)
            val jitterScale =
                0.20f + 0.80f * smoothStep
            alpha *= jitterScale
        }

        val distanceToLatest =
            hypot(
                safeX - previousX,
                safeY - previousY
            )

        val largeMoveConfirmed =
            if (distanceToLatest >= largeMoveThreshold) {
                confirmOrStageLargeMove(
                    rawX = safeX,
                    rawY = safeY,
                    cursorX = previousX,
                    cursorY = previousY,
                    timestampMs = timestampMs
                )
            } else {
                clearPendingLargeMove()
                false
            }

        // A single large displacement can be camera shake, motion blur or a
        // one-frame MediaPipe landmark collapse. Do not let that teleport the
        // cursor. A persistent second sample in the same area/direction is
        // treated as deliberate motion and regains the fast path immediately.
        if (
            distanceToLatest >= largeMoveThreshold &&
            !largeMoveConfirmed
        ) {
            val maxAlphaForStep =
                (unconfirmedLargeMoveMaxStep /
                    distanceToLatest.coerceAtLeast(0.0001f))
                    .coerceIn(0.08f, 0.60f)
            alpha = min(alpha, maxAlphaForStep)
        } else if (largeMoveConfirmed) {
            alpha =
                when {
                    distanceToLatest >= 0.080f ->
                        maxOf(alpha, 0.96f)
                    distanceToLatest >= 0.035f ->
                        maxOf(alpha, 0.88f)
                    else -> alpha
                }
        } else {
            // Medium deliberate movement remains responsive without the old
            // unconditional 96% snap on the first large sample.
            alpha =
                when {
                    distanceToLatest >= 0.035f ->
                        maxOf(alpha, 0.82f)
                    distanceToLatest >= 0.015f ->
                        maxOf(alpha, 0.70f)
                    else -> alpha
                }
        }

        alpha = alpha.coerceIn(0.08f, 0.985f)

        val nextX =
            previousX + (safeX - previousX) * alpha
        val nextY =
            previousY + (safeY - previousY) * alpha

        trackedX = nextX.coerceIn(0f, 1f)
        trackedY = nextY.coerceIn(0f, 1f)
        previousRawX = safeX
        previousRawY = safeY
        lastTimestampMs = timestampMs

        // resultAgeMs is intentionally not used for forward prediction. It is
        // retained in the API because latency telemetry still supplies it.
        @Suppress("UNUSED_VARIABLE")
        val measuredResultAgeMs = resultAgeMs

        return trackedX!! to trackedY!!
    }

    fun current(): Pair<Float, Float>? {
        val x = trackedX ?: return null
        val y = trackedY ?: return null
        return x to y
    }

    fun reset() {
        trackedX = null
        trackedY = null
        previousRawX = null
        previousRawY = null
        lastTimestampMs = null
        filteredSpeed = 0f
        clearPendingLargeMove()
    }

    private fun confirmOrStageLargeMove(
        rawX: Float,
        rawY: Float,
        cursorX: Float,
        cursorY: Float,
        timestampMs: Long
    ): Boolean {
        val pendingX = pendingLargeMoveX
        val pendingY = pendingLargeMoveY
        val pendingTime = pendingLargeMoveTimestampMs

        val withinWindow =
            pendingX != null &&
                pendingY != null &&
                pendingTime != null &&
                timestampMs >= pendingTime &&
                timestampMs - pendingTime <=
                    largeMoveConfirmWindowMs

        var confirmed = false
        if (withinWindow) {
            val nearPending =
                hypot(
                    rawX - pendingX!!,
                    rawY - pendingY!!
                ) <= 0.060f

            val firstDx = pendingX - cursorX
            val firstDy = pendingY - cursorY
            val currentDx = rawX - cursorX
            val currentDy = rawY - cursorY
            val firstLength =
                sqrt(firstDx * firstDx + firstDy * firstDy)
            val currentLength =
                sqrt(currentDx * currentDx + currentDy * currentDy)
            val sameDirection =
                if (
                    firstLength > 0.0001f &&
                    currentLength > 0.0001f
                ) {
                    (
                        (firstDx * currentDx + firstDy * currentDy) /
                            (firstLength * currentLength)
                        ) >= 0.80f
                } else {
                    false
                }

            confirmed = nearPending || sameDirection
        }

        if (confirmed) {
            clearPendingLargeMove()
            return true
        }

        pendingLargeMoveX = rawX
        pendingLargeMoveY = rawY
        pendingLargeMoveTimestampMs = timestampMs
        return false
    }

    private fun clearPendingLargeMove() {
        pendingLargeMoveX = null
        pendingLargeMoveY = null
        pendingLargeMoveTimestampMs = null
    }

    private fun alphaForCutoff(
        cutoffHz: Float,
        dtSeconds: Float
    ): Float =
        (
            1.0 -
                exp(
                    -2.0 *
                        PI *
                        cutoffHz.toDouble() *
                        dtSeconds.toDouble()
                )
            ).toFloat()
            .coerceIn(0f, 1f)
}

