package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import kotlin.math.abs
import kotlin.math.hypot

/** Explicit one-shot clutch. Unarmed travel has no path to a command. */
internal class DirectMotionController {
    private var owner: String? = null
    private var epoch = -1L
    private var armedAt = 0L
    private var lastAt = -1L
    private var anchor: Point3D? = null
    private var stationarySince = 0L
    private var ready = false
    private var scale = 0f
    val active: Boolean get() = owner != null

    fun arm(ownerId: String, actionEpoch: Long, timestamp: Long) {
        reset()
        owner = ownerId; epoch = actionEpoch; armedAt = timestamp
    }

    fun process(point: Point3D?, palmScale: Float, aspect: Float, timestamp: Long,
        ownerId: String?, actionEpoch: Long, valid: Boolean): AirAction? {
        if (!active) return null
        if (!valid || ownerId != owner || actionEpoch != epoch || point == null ||
            !aspect.isFinite() || aspect <= 0f || !palmScale.isFinite() || palmScale < .025f ||
            !point.x.isFinite() || !point.y.isFinite() || timestamp < armedAt ||
            timestamp - armedAt > 3000L || (lastAt >= 0 && (timestamp <= lastAt || timestamp - lastAt > 250L))) {
            reset(); return null
        }
        lastAt = timestamp
        val origin = anchor
        if (origin == null) {
            anchor = point; scale = palmScale; stationarySince = timestamp; return null
        }
        if (palmScale / scale !in .65f..1.5f) { reset(); return null }
        val dx = (point.x - origin.x) * aspect / scale
        val dy = (point.y - origin.y) / scale
        if (!ready) {
            if (hypot(dx, dy) > .15f) { anchor = point; stationarySince = timestamp }
            else if (timestamp - stationarySince >= 150L) ready = true
            return null
        }
        val elapsed = timestamp - stationarySince - 150L
        if (elapsed > 1200L) { reset(); return null }
        val major = maxOf(abs(dx), abs(dy))
        val minor = minOf(abs(dx), abs(dy))
        if (major < .85f || major < minor * 1.8f || elapsed < 80L) return null
        if (major / (elapsed / 1000f) < .8f) return null
        val action = if (abs(dx) > abs(dy)) {
            if (dx < 0) AirAction.SWIPE_LEFT else AirAction.SWIPE_RIGHT
        } else if (dy < 0) AirAction.SCROLL_UP else AirAction.SCROLL_DOWN
        reset()
        return action
    }

    fun reset() { owner = null; anchor = null; ready = false; lastAt = -1L }
}
