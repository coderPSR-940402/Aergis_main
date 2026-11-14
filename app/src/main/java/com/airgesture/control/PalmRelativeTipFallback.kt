package com.airgesture.control

import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Causal, palm-relative index-tip estimate for the *visible cursor only*.
 *
 * Inspired by articulated-hand reconstruction's use of a stable hand frame, but
 * deliberately uses no mesh, model inference, temporal look-ahead or IMU input.
 * The last observed tip is stored relative to MCP joints 5 and 17. When the tip
 * briefly refits or becomes invalid but both palm anchors remain observed, the
 * cursor can follow measured palm translation, scale and in-plane rotation.
 *
 * This estimate is never a valid landmark, tap, gesture, drag or calibration
 * observation. Callers must revoke all action authority before displaying it.
 */
internal class PalmRelativeTipFallback(private val maxAgeMs: Long = 300L) {
    private data class PalmFrame(
        val x: Float,
        val y: Float,
        val axisX: Float,
        val axisY: Float,
        val squaredWidth: Float
    )

    private var observedAtMs = Long.MIN_VALUE
    private var ownerId: String? = null
    private var inputEpoch = Long.MIN_VALUE
    private var actionEpoch = Long.MIN_VALUE
    private var reference: PalmFrame? = null
    private var along = 0f
    private var across = 0f
    private var observedTipX = 0f
    private var observedTipY = 0f

    fun observe(
        landmarks: List<Point3D>,
        aspectRatio: Float,
        owner: String?,
        sourceEpoch: Long,
        authorityEpoch: Long,
        timestampMs: Long
    ): Boolean {
        val palm = palmFrame(landmarks, aspectRatio) ?: return false
        val tip = landmarks.getOrNull(KinematicValidator.INDEX_TIP) ?: return false
        if (owner.isNullOrBlank() || timestampMs < 0L || !inFrame(tip)) return false
        val dx = (tip.x - palm.x) * aspectRatio
        val dy = tip.y - palm.y
        val a = (dx * palm.axisX + dy * palm.axisY) / palm.squaredWidth
        val b = (palm.axisX * dy - palm.axisY * dx) / palm.squaredWidth
        if (!a.isFinite() || !b.isFinite() || abs(a) > 4f || abs(b) > 4f) return false

        reference = palm
        along = a
        across = b
        observedTipX = tip.x
        observedTipY = tip.y
        observedAtMs = timestampMs
        ownerId = owner
        inputEpoch = sourceEpoch
        actionEpoch = authorityEpoch
        return true
    }

    fun predict(
        landmarks: List<Point3D>,
        aspectRatio: Float,
        owner: String?,
        sourceEpoch: Long,
        authorityEpoch: Long,
        timestampMs: Long
    ): Point3D? {
        val origin = reference ?: return null
        if (owner == null || owner != ownerId || sourceEpoch != inputEpoch ||
            authorityEpoch != actionEpoch || timestampMs <= observedAtMs) return null
        val ageMs = timestampMs - observedAtMs
        if (ageMs > maxAgeMs) {
            reset()
            return null
        }
        val palm = palmFrame(landmarks, aspectRatio) ?: return null
        val widthRatio = sqrt(palm.squaredWidth / origin.squaredWidth)
        if (widthRatio !in .6f..1.7f) return null

        // A large apparent roll in a single missing-tip interval is ambiguous.
        val cosine = (palm.axisX * origin.axisX + palm.axisY * origin.axisY) /
            sqrt(palm.squaredWidth * origin.squaredWidth)
        if (!cosine.isFinite() || cosine < -.05f) return null

        val x = palm.x + (along * palm.axisX - across * palm.axisY) / aspectRatio
        val y = palm.y + along * palm.axisY + across * palm.axisX
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f) return null
        // An untrusted image-space estimate cannot teleport the cursor. This is
        // intentionally looser than the measured-tip velocity gate; it also
        // admits real palm rotation, not just translation.
        val displacement = hypot(x - observedTipX, y - observedTipY)
        val maximum = (.10f + ageMs * .003f).coerceAtMost(.45f)
        if (displacement > maximum) return null
        return Point3D(x, y)
    }

    fun reset() {
        reference = null
        ownerId = null
        observedAtMs = Long.MIN_VALUE
        inputEpoch = Long.MIN_VALUE
        actionEpoch = Long.MIN_VALUE
    }

    private fun palmFrame(points: List<Point3D>, aspect: Float): PalmFrame? {
        if (!aspect.isFinite() || aspect <= 0f) return null
        val index = points.getOrNull(KinematicValidator.INDEX_MCP) ?: return null
        val pinky = points.getOrNull(KinematicValidator.PINKY_MCP) ?: return null
        if (!inFrame(index) || !inFrame(pinky)) return null
        val vx = (pinky.x - index.x) * aspect
        val vy = pinky.y - index.y
        val widthSq = vx * vx + vy * vy
        if (!widthSq.isFinite() || widthSq < .015f * .015f) return null
        return PalmFrame(index.x, index.y, vx, vy, widthSq)
    }

    private fun inFrame(point: Point3D): Boolean =
        point.x.isFinite() && point.y.isFinite() && point.z.isFinite() &&
            point.x in 0f..1f && point.y in 0f..1f
}
