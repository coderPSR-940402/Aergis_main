package com.airgesture.control

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Recovery source: mrpsrabe-coder/AIR-GESTURE-CONTROL, 744d8372a6127fe0f065a5cbb4b4d946ca19844b.
 * Original smoothing retained; malformed and non-increasing samples are ignored.
 *
 * Low-latency pointer motion filter.
 *
 * Design goals:
 * - suppress tiny stationary landmark jitter
 * - keep deliberate travel close to the newest measurement
 * - compensate a small, bounded portion of measured inference age
 * - disable prediction on reversals, weak confidence or implausible jumps
 * - never let prediction run away from the latest measured hand position
 */
internal class ClassicPointerMotionFilter(
    private val predictionAgeFraction: Float = 0.28f,
    private val maxPredictionMs: Long = 38L,
    private val maxPredictionDistance: Float = 0.035f
) {
    private var trackedX: Float? = null
    private var trackedY: Float? = null
    private var lastTimestampMs: Long? = null
    private var previousRawX: Float? = null
    private var previousRawY: Float? = null
    private var velocityX = 0f
    private var velocityY = 0f

    fun update(
        rawX: Float,
        rawY: Float,
        timestampMs: Long,
        confidence: Float,
        resultAgeMs: Long = 0L
    ): Pair<Float, Float> {
        val last = lastTimestampMs
        if (!rawX.isFinite() || !rawY.isFinite() || !confidence.isFinite() ||
            timestampMs < 0L || (last != null && timestampMs <= last)) {
            return current() ?: (.5f to .5f)
        }
        val previousX = trackedX
        val previousY = trackedY

        val safeX =
            rawX
                .takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: previousX
                ?: 0.5f
        val safeY =
            rawY
                .takeIf { it.isFinite() }
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
            lastTimestampMs = timestampMs
            previousRawX = safeX
            previousRawY = safeY
            velocityX = 0f
            velocityY = 0f
            return safeX to safeY
        }

        val dtSeconds =
            (
                (timestampMs - previousTime)
                    .coerceIn(8L, 140L) /
                    1000f
                )

        val rawPreviousX =
            previousRawX ?: safeX
        val rawPreviousY =
            previousRawY ?: safeY
        val rawDx =
            safeX - rawPreviousX
        val rawDy =
            safeY - rawPreviousY
        val rawDelta =
            hypot(rawDx, rawDy)
        val sampleVelocityX =
            rawDx / dtSeconds
        val sampleVelocityY =
            rawDy / dtSeconds
        val sampleSpeed =
            hypot(
                sampleVelocityX,
                sampleVelocityY
            )

        val previousVelocityX = velocityX
        val previousVelocityY = velocityY
        val previousSpeed =
            hypot(
                previousVelocityX,
                previousVelocityY
            )
        val directionDot =
            sampleVelocityX * previousVelocityX +
                sampleVelocityY * previousVelocityY
        val reversed =
            previousSpeed > 0.12f &&
                sampleSpeed > 0.12f &&
                directionDot < 0f

        if (
            rawDelta <= 0.36f &&
            sampleSpeed <= 8.0f
        ) {
            val velocityAlpha =
                if (reversed) {
                    1f
                } else {
                    0.55f
                }
            velocityX +=
                (sampleVelocityX - velocityX) *
                    velocityAlpha
            velocityY +=
                (sampleVelocityY - velocityY) *
                    velocityAlpha
        } else {
            velocityX = 0f
            velocityY = 0f
        }

        previousRawX = safeX
        previousRawY = safeY

        val safeConfidence =
            confidence.coerceIn(0f, 1f)

        val predictionAllowed =
            !reversed &&
                rawDelta >= 0.006f &&
                rawDelta <= 0.28f &&
                sampleSpeed in 0.12f..5.0f &&
                safeConfidence >= 0.72f &&
                resultAgeMs > 0L

        val horizonMs =
            if (predictionAllowed) {
                min(
                    maxPredictionMs,
                    (
                        resultAgeMs *
                            predictionAgeFraction
                        ).toLong()
                ).coerceAtLeast(0L)
            } else {
                0L
            }

        val horizonSeconds =
            horizonMs / 1000f

        val predictionDx =
            (velocityX * horizonSeconds)
                .coerceIn(
                    -maxPredictionDistance,
                    maxPredictionDistance
                )
        val predictionDy =
            (velocityY * horizonSeconds)
                .coerceIn(
                    -maxPredictionDistance,
                    maxPredictionDistance
                )

        val targetX =
            (safeX + predictionDx)
                .coerceIn(0f, 1f)
        val targetY =
            (safeY + predictionDy)
                .coerceIn(0f, 1f)

        val dx = targetX - previousX
        val dy = targetY - previousY
        val delta = hypot(dx, dy)
        val speed = delta / dtSeconds

        val cutoffHz =
            (
                7.5f +
                    min(speed * 11f, 18f)
                ) *
                (
                    0.90f +
                        safeConfidence * 0.10f
                    )

        var alpha =
            (
                1.0 -
                    exp(
                        -2.0 *
                            PI *
                            cutoffHz.toDouble() *
                            dtSeconds.toDouble()
                    )
                ).toFloat()
                .coerceIn(0.10f, 0.985f)

        when {
            delta < 0.0022f -> {
                alpha *= 0.16f
            }
            delta < 0.0055f -> {
                alpha *= 0.68f
            }
            delta >= 0.080f -> {
                alpha = max(alpha, 0.95f)
            }
            delta >= 0.025f -> {
                alpha = max(alpha, 0.84f)
            }
        }

        val nextX =
            previousX + dx * alpha
        val nextY =
            previousY + dy * alpha

        trackedX = nextX.coerceIn(0f, 1f)
        trackedY = nextY.coerceIn(0f, 1f)
        lastTimestampMs = timestampMs

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
        lastTimestampMs = null
        previousRawX = null
        previousRawY = null
        velocityX = 0f
        velocityY = 0f
    }
}
