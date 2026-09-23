package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AirAccessibilityService : AccessibilityService() {
    private var pointerOverlay: PointerOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        pointerOverlay = PointerOverlay(this)
        if (AirRuntime.pointerEnabled) pointerOverlay?.show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pointerOverlay?.hide()
        pointerOverlay = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun updatePointer(x: Float, y: Float, enabled: Boolean) {
        if (!enabled) {
            pointerOverlay?.hide()
            return
        }
        pointerOverlay?.update(1f - x, y)
    }

    fun dispatch(action: AirAction): ActionResult = when (action) {
        AirAction.BACK -> {
            ActionResult(action, performGlobalAction(GLOBAL_ACTION_BACK))
        }
        AirAction.HOME -> {
            ActionResult(action, performGlobalAction(GLOBAL_ACTION_HOME))
        }
        AirAction.RECENTS -> {
            ActionResult(action, performGlobalAction(GLOBAL_ACTION_RECENTS))
        }
        AirAction.TAP, AirAction.DOUBLE_TAP, AirAction.LONG_PRESS ->
            dispatchPointerTap(action == AirAction.DOUBLE_TAP, action == AirAction.LONG_PRESS)
        AirAction.SCROLL_UP -> dispatchSwipe(0.5f, 0.75f, 0.5f, 0.25f)
        AirAction.SCROLL_DOWN -> dispatchSwipe(0.5f, 0.25f, 0.5f, 0.75f)
        AirAction.NONE -> ActionResult(action, false, "No action")
    }

    private fun dispatchPointerTap(doubleTap: Boolean, longPress: Boolean): ActionResult {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
        }
        val duration = if (longPress) 650L else 1L
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val accepted = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(), null, null
        )
        if (doubleTap && accepted) {
            dispatchGesture(
                GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
                    .build(), null, null
            )
        }
        return ActionResult(
            if (doubleTap) AirAction.DOUBLE_TAP else if (longPress) AirAction.LONG_PRESS else AirAction.TAP,
            accepted
        )
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float): ActionResult {
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(x1 * metrics.widthPixels, y1 * metrics.heightPixels)
            lineTo(x2 * metrics.widthPixels, y2 * metrics.heightPixels)
        }
        val accepted = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
                .build(), null, null
        )
        return ActionResult(AirAction.SCROLL_UP, accepted)
    }

    companion object {
        @Volatile var instance: AirAccessibilityService? = null
        fun enabled(): Boolean = instance != null
    }
}
