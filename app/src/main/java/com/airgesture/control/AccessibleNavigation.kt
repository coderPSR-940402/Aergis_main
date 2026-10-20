package com.airgesture.control

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/** Native keyboard focus for exposed controls. Never guesses a coordinate or clicks a parent. */
internal object AccessibleNavigation {
    fun perform(root: AccessibilityNodeInfo?, action: AirAction): Boolean {
        if (root == null || action !in setOf(AirAction.FOCUS_NEXT, AirAction.FOCUS_PREVIOUS, AirAction.ACTIVATE_FOCUSED)) return false
        // Nodes can become stale between retrieval and use, including during window changes.
        return try { performOnNode(root, action) } catch (_: IllegalStateException) { false }
            catch (_: SecurityException) { false }
    }

    private fun performOnNode(root: AccessibilityNodeInfo, action: AirAction): Boolean {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (action == AirAction.ACTIVATE_FOCUSED) {
            return focused != null && focused.isVisibleToUser && focused.isEnabled &&
                !focused.isPassword && focused.isClickable && focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val direction = if (action == AirAction.FOCUS_PREVIOUS) View.FOCUS_BACKWARD else View.FOCUS_FORWARD
        val next = (focused ?: root).focusSearch(direction) ?: return false
        return next.isVisibleToUser && next.isEnabled && !next.isPassword && next.isFocusable &&
            next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }
}
