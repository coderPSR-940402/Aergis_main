package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock

/** Main-thread single-finger touch, maintained in short, callback-chained strokes. */
internal class HeldPointerTouch(
    private val handler: Handler,
    private val dispatch: (GestureDescription, AccessibilityService.GestureResultCallback) -> Boolean,
    private val permitted: (Long) -> Boolean,
    private val record: (ActionDispatchOutcome) -> Unit
) {
    private var stroke: GestureDescription.StrokeDescription? = null
    private var endpoint = PointerCoordinateMapper.Point(0f, 0f)
    private var target = endpoint
    private var pressed = false
    private var blockedUntilOpen = false
    private var epoch = 0L
    private var generation = 0L
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
        // Release at the last held point, not at an already-recovering aiming coordinate.
        if (isPressed) target = point
        if (stroke == null && isPressed) {
            epoch = requestEpoch
            endpoint = point
            generation++
            send(ending = false)
            if (stroke != null) handler.postDelayed(watchdog, WATCHDOG_MS)
        }
    }

    private fun send(ending: Boolean) {
        if (!permitted(epoch)) { cancel(); return }
        val to = if (ending) endpoint else target
        val path = Path().apply {
            moveTo(endpoint.x, endpoint.y)
            if (to != endpoint) lineTo(to.x, to.y)
        }
        val next = stroke?.continueStroke(path, 0L, if (ending) 1L else CHUNK_MS, !ending)
            ?: GestureDescription.StrokeDescription(path, 0L, CHUNK_MS, true)
        stroke = next
        endpoint = to
        val token = generation
        val gesture = GestureDescription.Builder().addStroke(next).build()
        val accepted = dispatch(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (token != generation) return
                if (ending) {
                    clear()
                    record(ActionDispatchOutcome.COMPLETED)
                } else if (!permitted(epoch) || SystemClock.uptimeMillis() - lastSampleAt > MAX_IDLE_MS) {
                    cancel()
                } else send(ending = !pressed)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (token != generation) return
                clear()
                blockedUntilOpen = true
                record(ActionDispatchOutcome.CANCELLED)
            }
        })
        if (!accepted) {
            clear()
            blockedUntilOpen = true
            record(ActionDispatchOutcome.PLATFORM_REJECTED)
        }
    }

    fun cancel() {
        if (stroke == null) return
        clear()
        blockedUntilOpen = true
        // Android has no public cancelGesture API. A continuation of an undispatched
        // stroke is rejected by MotionEventInjector after cancelling the old gesture;
        // it produces ACTION_CANCEL, not a finger-up tap or a new touch-down.
        // AOSP MotionEventInjector.injectEventsMainThread / prepareToContinueOldGesture.
        val path = Path().apply { moveTo(endpoint.x, endpoint.y) }
        val undispatched = GestureDescription.StrokeDescription(path, 0L, 1L, true)
        val cancellation = undispatched.continueStroke(path, 0L, 1L, false)
        val gesture = GestureDescription.Builder().addStroke(cancellation).build()
        val accepted = dispatch(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                record(ActionDispatchOutcome.CANCELLED)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                record(ActionDispatchOutcome.CANCELLED)
            }
        })
        if (!accepted) record(ActionDispatchOutcome.PLATFORM_REJECTED)
    }

    private fun clear() {
        generation++
        stroke = null
        pressed = false
        handler.removeCallbacks(watchdog)
    }

    private companion object {
        const val CHUNK_MS = 50L
        const val WATCHDOG_MS = 25L
        const val MAX_IDLE_MS = 250L
    }
}
