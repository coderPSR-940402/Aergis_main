package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock

/** Main-thread native finger-down, continued only for movement or release. */
internal class PrecisionDragController(
    private val handler: Handler,
    private val dispatch: (GestureDescription, AccessibilityService.GestureResultCallback) -> Boolean,
    private val permitted: (Long) -> Boolean,
    private val record: (ActionDispatchOutcome) -> Unit,
    private val recordCancellationRejection: (ActionDispatchOutcome) -> Unit = record
) {
    val isBusy: Boolean get() = stroke != null
    private var cancelling = false
    @Volatile private var stroke: GestureDescription.StrokeDescription? = null
    private var endpoint = PointerCoordinateMapper.Point(0f, 0f)
    private var target = endpoint
    private var pressed = false
    private var releaseRequested = false
    private var releaseTarget: PointerCoordinateMapper.Point? = null
    private var blockedUntilOpen = false
    private var epoch = 0L
    private var generation = 0L
    private var dispatchInFlight = false
    private var lastSampleAt = 0L

    private val watchdog = object : Runnable {
        override fun run() {
            if (stroke == null) return
            if (!permitted(epoch) || SystemClock.uptimeMillis() - lastSampleAt > MAX_IDLE_MS) {
                cancel()
            } else handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    fun update(point: PointerCoordinateMapper.Point, isPressed: Boolean, requestEpoch: Long,
        sampledAt: Long) {
        if (cancelling) return
        if (blockedUntilOpen) {
            if (!isPressed) blockedUntilOpen = false
            return
        }
        if (!permitted(requestEpoch) || !point.x.isFinite() || !point.y.isFinite() ||
            SystemClock.uptimeMillis() - sampledAt > MAX_IDLE_MS) {
            cancel()
            if (isPressed) blockedUntilOpen = true
            return
        }
        if (stroke != null && requestEpoch != epoch) {
            cancel()
            return
        }
        lastSampleAt = sampledAt
        pressed = isPressed
        // Finger-up belongs to the current touch and cannot be undone by the next pinch.
        if (stroke != null && !isPressed && !releaseRequested) {
            releaseRequested = true
            // Preserve the last held target even if Android has not dispatched that
            // movement yet. Neither aiming after release nor a new pinch may replace it.
            releaseTarget = target
        }
        // Release at the last held point, not at an already-recovering aiming coordinate.
        // GestureDescription rounds sampled positions to physical pixels. Differences
        // below that precision produce no MOVE event and cannot form a continuation.
        val pixelPoint = PointerCoordinateMapper.Point(Math.round(point.x).toFloat(), Math.round(point.y).toFloat())
        if (isPressed) target = pixelPoint
        if (stroke == null && isPressed) {
            epoch = requestEpoch
            endpoint = pixelPoint
            send(ending = false)
            if (stroke != null) handler.postDelayed(watchdog, WATCHDOG_MS)
        } else if (stroke != null && !dispatchInFlight) {
            send(ending = releaseRequested || !pressed)
        }
    }

    private fun send(ending: Boolean) {
        if (!permitted(epoch)) { cancel(); return }
        val to = if (ending) releaseTarget ?: endpoint else target
        // Android rejects empty event lists: a stationary continuing stroke has no
        // DOWN, MOVE or UP. The previous willContinue stroke already keeps the finger
        // down; retain it until a new target or release arrives (watchdog still runs).
        if (stroke != null && !ending && to == endpoint) return
        val path = Path().apply {
            moveTo(endpoint.x, endpoint.y)
            if (to != endpoint) lineTo(to.x, to.y)
        }
        val duration = if (ending && to == endpoint) 1L else CHUNK_MS
        val starting = stroke == null
        val next = stroke?.continueStroke(path, 0L, duration, !ending)
            ?: GestureDescription.StrokeDescription(path, 0L, CHUNK_MS, true)
        stroke = next
        endpoint = to
        val token = ++generation
        dispatchInFlight = true
        val gesture = GestureDescription.Builder().addStroke(next).build()
        val accepted = safeDispatch(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (token != generation || !dispatchInFlight || cancelling) return
                dispatchInFlight = false
                if (ending) {
                    // Retain only a currently held, fresh next press; never replay a
                    // backlog of contacts that ended while Android was completing up.
                    clear()
                    record(ActionDispatchOutcome.COMPLETED)
                } else if (!permitted(epoch) || SystemClock.uptimeMillis() - lastSampleAt > MAX_IDLE_MS) {
                    cancel()
                } else send(ending = releaseRequested || !pressed)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (token != generation || !dispatchInFlight) return
                dispatchInFlight = false
                clear()
                blockedUntilOpen = true
                record(ActionDispatchOutcome.CANCELLED)
            }
        })
        if (accepted && starting) record(ActionDispatchOutcome.SUBMITTED)
        if (!accepted) {
            dispatchInFlight = false
            blockedUntilOpen = true
            record(ActionDispatchOutcome.PLATFORM_REJECTED)
            cancel()
        }
    }

    fun cancel() {
        if (stroke == null) return
        if (cancelling) return
        cancelling = true
        handler.removeCallbacks(watchdog)
        blockedUntilOpen = true
        // Android has no public cancelGesture API. A continuation of an undispatched
        // stroke is rejected by MotionEventInjector after cancelling the old gesture;
        // it produces ACTION_CANCEL, not a finger-up tap or a new touch-down.
        // AOSP MotionEventInjector.injectEventsMainThread / prepareToContinueOldGesture.
        val path = Path().apply { moveTo(endpoint.x, endpoint.y) }
        val undispatched = GestureDescription.StrokeDescription(path, 0L, 1L, true)
        val cancellation = undispatched.continueStroke(path, 0L, 1L, false)
        val gesture = GestureDescription.Builder().addStroke(cancellation).build()
        val cancelToken = generation
        val accepted = safeDispatch(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!cancelling || generation != cancelToken) return
                clear()
                record(ActionDispatchOutcome.CANCELLED)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (!cancelling || generation != cancelToken) return
                clear()
                record(ActionDispatchOutcome.CANCELLED)
            }
        })
        if (!accepted) recordCancellationRejection(ActionDispatchOutcome.PLATFORM_REJECTED)
    }

    private fun safeDispatch(gesture: GestureDescription, callback: AccessibilityService.GestureResultCallback): Boolean =
        try { dispatch(gesture, callback) } catch (_: RuntimeException) { false }

    private fun clear() {
        cancelling = false
        generation++
        stroke = null
        dispatchInFlight = false
        pressed = false
        releaseRequested = false
        releaseTarget = null
        handler.removeCallbacks(watchdog)
    }

    private companion object {
        // Short continuation chunks reduce worst-case cursor/touch latency without
        // flooding AccessibilityService with one dispatch per camera frame.
        const val CHUNK_MS = 25L
        const val WATCHDOG_MS = 15L
        const val MAX_IDLE_MS = 350L
    }
}
