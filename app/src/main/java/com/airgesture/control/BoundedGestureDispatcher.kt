package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.SystemClock

/** Main-thread bounded output; no continuing touch, backlog or automatic retry. */
internal class BoundedGestureDispatcher(
    private val handler: Handler,
    private val dispatch: (GestureDescription, AccessibilityService.GestureResultCallback) -> Boolean,
    private val permitted: (Long) -> Boolean,
    private val record: (ActionDispatchOutcome) -> Unit
) {
    private var generation = 0L
    @Volatile private var active: Long? = null
    val isBusy: Boolean get() = active != null
    private var epoch = 0L
    private var deadline = 0L
    private var cancelling = false
    private val watchdog = object : Runnable {
        override fun run() {
            if (active == null || cancelling) return
            if (!permitted(epoch) || SystemClock.uptimeMillis() > deadline) cancel()
            else handler.postDelayed(this, 15L)
        }
    }

    fun submit(gesture: GestureDescription, requestEpoch: Long) {
        if (!permitted(requestEpoch)) { record(ActionDispatchOutcome.POLICY_DENIED); return }
        if (active != null) { record(ActionDispatchOutcome.PLATFORM_REJECTED); return }
        val token = ++generation
        active = token
        epoch = requestEpoch
        cancelling = false
        deadline = SystemClock.uptimeMillis() + (0 until gesture.strokeCount).maxOf {
            gesture.getStroke(it).let { stroke -> stroke.startTime + stroke.duration }
        } + 250L
        val accepted = submitToPlatform(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = finish(token, ActionDispatchOutcome.COMPLETED)
            override fun onCancelled(gestureDescription: GestureDescription?) = finish(token, ActionDispatchOutcome.CANCELLED)
        })
        if (!accepted) finish(token, ActionDispatchOutcome.PLATFORM_REJECTED)
        else if (active == token) handler.postDelayed(watchdog, 15L)
    }

    fun cancel() {
        val token = active ?: return
        if (cancelling) return
        cancelling = true
        handler.removeCallbacks(watchdog)
        // Continuing an undispatched stroke fails continuation validation and cancels pending input
        // without injecting a new DOWN/UP. AOSP MotionEventInjector; device gate remains.
        val path = Path().apply { moveTo(0f, 0f) }
        val unknown = GestureDescription.StrokeDescription(path, 0L, 1L, true)
        val request = GestureDescription.Builder().addStroke(unknown.continueStroke(path, 0L, 1L, false)).build()
        val accepted = submitToPlatform(request, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = finish(token, ActionDispatchOutcome.CANCELLED)
            override fun onCancelled(gestureDescription: GestureDescription?) = finish(token, ActionDispatchOutcome.CANCELLED)
        })
        // Rejection is not cancellation evidence. Keep the original command
        // occupied until its own callback; never retry or permit overlapping input.
        if (!accepted) record(ActionDispatchOutcome.PLATFORM_REJECTED)
    }

    private fun submitToPlatform(gesture: GestureDescription,
        callback: AccessibilityService.GestureResultCallback): Boolean = try {
        dispatch(gesture, callback)
    } catch (_: RuntimeException) {
        // Permission/service loss can race a successful eligibility check.
        false
    }

    private fun finish(token: Long, outcome: ActionDispatchOutcome) {
        if (active != token) return
        active = null
        cancelling = false
        handler.removeCallbacks(watchdog)
        record(outcome)
    }
}
