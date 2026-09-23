package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AirAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun dispatch(action: AirAction): ActionResult = when (action) {
        AirAction.BACK -> { performGlobalAction(GLOBAL_ACTION_BACK); ActionResult(action, true) }
        AirAction.HOME -> { performGlobalAction(GLOBAL_ACTION_HOME); ActionResult(action, true) }
        AirAction.RECENTS -> { performGlobalAction(GLOBAL_ACTION_RECENTS); ActionResult(action, true) }
        AirAction.TAP, AirAction.DOUBLE_TAP, AirAction.LONG_PRESS -> dispatchPointerTap(action == AirAction.DOUBLE_TAP, action == AirAction.LONG_PRESS)
        AirAction.SCROLL_UP -> dispatchSwipe(0.5f, 0.75f, 0.5f, 0.25f)
        AirAction.SCROLL_DOWN -> dispatchSwipe(0.5f, 0.25f, 0.5f, 0.75f)
        AirAction.NONE -> ActionResult(action, false, "No action")
    }

    private fun dispatchPointerTap(doubleTap: Boolean, longPress: Boolean): ActionResult {
        val path = Path().apply { moveTo(540f, 960f) }
        val duration = if (longPress) 650L else 1L
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val accepted = dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        if (doubleTap && accepted) dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 1)).build(), null, null)
        return ActionResult(if (doubleTap) AirAction.DOUBLE_TAP else if (longPress) AirAction.LONG_PRESS else AirAction.TAP, accepted)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float): ActionResult {
        val path = Path().apply { moveTo(x1 * 1080f, y1 * 1920f); lineTo(x2 * 1080f, y2 * 1920f) }
        val accepted = dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build(), null, null)
        return ActionResult(AirAction.SCROLL_UP, accepted)
    }

    companion object {
        @Volatile var instance: AirAccessibilityService? = null
        fun enabled(): Boolean = instance != null
    }
}
