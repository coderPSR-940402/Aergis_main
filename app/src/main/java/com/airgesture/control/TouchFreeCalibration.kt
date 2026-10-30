package com.airgesture.control

import com.airgesture.control.pointer.ClickHysteresisStateMachine
import kotlin.math.abs
import kotlin.math.hypot

/** Ephemeral owned vision evidence. Sample timestamps use the pipeline clock;
 * [CalibrationGestureFrame.observedAtMs] uses uptime for UI delivery-age checks.
 * Never grants phone-action authority. */
data class CalibrationGestureFrame(val timestampMs: Long, val epoch: Long, val owner: String,
    val x: Float, val y: Float, val distance: Float, val depth: Float, val pose: String, val observedAtMs: Long = timestampMs)

/** Only active in the foreground, disarmed calibration screen. */
internal class TouchFreeCalibration {
    sealed interface Event {
        data class Capture(val x: Float, val y: Float) : Event
        data object Confirm : Event
        data object Cancel : Event
        data object WrongCorner : Event
    }
    private val contact = ClickHysteresisStateMachine()
    private var active = false
    private val corners = ArrayList<Pair<Float, Float>>()
    private var previous: CalibrationGestureFrame? = null
    private var aim: CalibrationGestureFrame? = null
    private var stableAt: Long? = null
    private var lastOpenAt: Long? = null
    private var heldPose: String? = null
    private var heldSince: Long? = null

    fun start() { active = true; corners.clear(); resetEvidence() }
    fun stop() { active = false; corners.clear(); resetEvidence() }
    fun resetEvidence() {
        previous = null; aim = null; stableAt = null; lastOpenAt = null
        heldPose = null; heldSince = null; contact.reset()
    }
    fun manualSample(x: Float, y: Float) {
        if (active) { corners.add(x to y); resetEvidence() }
    }
    fun update(frame: CalibrationGestureFrame?): Event? {
        if (!active) return null
        if (frame == null || frame.timestampMs < 0L || frame.owner.isBlank() || !frame.x.isFinite() || !frame.y.isFinite() || frame.x !in 0f..1f || frame.y !in 0f..1f ||
            !frame.distance.isFinite() || !frame.depth.isFinite() || frame.distance < 0f || frame.depth < 0f) {
            resetEvidence(); return null
        }
        val prior = previous
        if (prior != null && (frame.timestampMs <= prior.timestampMs || frame.timestampMs - prior.timestampMs > 350L ||
                frame.owner != prior.owner || frame.epoch != prior.epoch)) {
            resetEvidence()
        }
        // Never turn a stale/out-of-order sample into new evidence.
        if (prior != null && frame.timestampMs <= prior.timestampMs) return null
        previous = frame
        val pose = frame.pose.takeIf { it == "Closed_Fist" || (corners.size >= 4 && it == "Open_Palm" && frame.distance >= .32f) }
        if (pose != heldPose) { heldPose = pose; heldSince = pose?.let { frame.timestampMs } }
        if (pose != null && frame.timestampMs - (heldSince ?: frame.timestampMs) >= if (pose == "Open_Palm") 1000L else 1200L) {
            stop()
            return if (pose == "Open_Palm") Event.Confirm else Event.Cancel
        }
        if (pose == "Closed_Fist") {
            // Cancellation owns this pose. Its thumb/middle contact is not a corner.
            contact.reset(); aim = null; stableAt = null; lastOpenAt = null
            return null
        }
        if (corners.size >= 4) return null
        val capturedAim = aim
        val stable = stableAt?.let { frame.timestampMs - it >= 300L } == true
        val clicked = contact.processFrame(frame.distance, frame.timestampMs, frame.depth)
        if (clicked) {
            aim = null; stableAt = null
            val freshAim = lastOpenAt?.let { frame.timestampMs - it in 0L..350L } == true
            if (!stable || capturedAim == null || !freshAim) return null
            if (!ordered(capturedAim.x, capturedAim.y)) return Event.WrongCorner
            corners.add(capturedAim.x to capturedAim.y)
            return Event.Capture(capturedAim.x, capturedAim.y)
        }
        // Freeze pre-contact aim. Finger movement during the pinch cannot move the corner.
        if (frame.distance >= .32f) {
            lastOpenAt = frame.timestampMs
            if (capturedAim == null || hypot(frame.x - capturedAim.x, frame.y - capturedAim.y) > .035f) {
                aim = frame; stableAt = frame.timestampMs
            }
        }
        return null
    }
    private fun ordered(x: Float, y: Float): Boolean {
        val first = corners.firstOrNull() ?: return true
        val last = corners.last()
        return when (corners.size) {
            1 -> x <= first.first - .12f && abs(y - first.second) <= .08f
            2 -> y >= maxOf(first.second, last.second) + .12f && abs(x - last.first) <= .08f
            3 -> x >= last.first + .12f && abs(y - last.second) <= .08f && abs(x - first.first) <= .08f
            else -> false
        }
    }
}
