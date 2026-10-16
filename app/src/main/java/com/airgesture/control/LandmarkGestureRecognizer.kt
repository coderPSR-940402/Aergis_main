package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import kotlin.math.sqrt

/** Five additional poses from the owned hand. Ambiguous bends produce no custom evidence. */
internal object LandmarkGestureRecognizer {
    fun recognize(points: List<Point3D>, aspect: Float): GestureSignal? {
        if (points.size != 21 || !aspect.isFinite() || aspect <= 0f ||
            points.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return null
        fun distance(a: Int, b: Int) = points[a].distance2DTo(points[b], aspect)
        val scale = maxOf(distance(5, 17), distance(0, 9))
        if (scale < .025f) return null
        fun straight(a: Int, b: Int, c: Int): Float {
            val ux = (points[a].x - points[b].x) * aspect
            val uy = points[a].y - points[b].y
            val vx = (points[c].x - points[b].x) * aspect
            val vy = points[c].y - points[b].y
            val denominator = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            return if (denominator < .000001f) 1f else (ux * vx + uy * vy) / denominator
        }
        // Joint angles and wrist-relative extension are invariant to in-plane rotation.
        fun extended(mcp: Int) = straight(mcp, mcp + 1, mcp + 3) < -.8f &&
            distance(0, mcp + 3) > distance(0, mcp + 1) + scale * .15f
        fun folded(mcp: Int) = straight(mcp, mcp + 1, mcp + 3) > -.35f &&
            distance(0, mcp + 3) < distance(0, mcp + 1) + scale * .1f
        val fingers = listOf(5, 9, 13, 17)
        val up = fingers.map(::extended)
        val down = fingers.map(::folded)
        val thumbOut = straight(1, 2, 4) < -.75f && distance(4, 5) > scale * .65f
        val thumbIn = distance(4, 9) < scale * .55f
        val pose = when {
            distance(4, 8) < scale * .18f && down[0] && up.drop(1).all { it } -> RegisteredGesture.OK
            up == listOf(true, true, true, false) && down[3] && thumbIn -> RegisteredGesture.THREE_FINGERS
            up.all { it } && thumbIn -> RegisteredGesture.FOUR_FINGERS
            down.take(3).all { it } && up[3] && thumbOut -> RegisteredGesture.SHAKA
            down.take(3).all { it } && up[3] && thumbIn -> RegisteredGesture.PINKY_ONLY
            else -> null
        }
        return pose?.let { GestureSignal(it.signalName, .85f) }
    }
}
