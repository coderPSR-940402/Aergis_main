package com.airgesture.control

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Use the focused scroll region, or the sole available one; never guess between panes. */
internal object AccessibleScroll {
    fun perform(root: AccessibilityNodeInfo?, forward: Boolean): Boolean {
        if (root == null) return false
        val obtained = ArrayList<AccessibilityNodeInfo>()
        data class Candidate(val node: AccessibilityNodeInfo, val bounds: Rect, val action: Int)
        val candidates = ArrayList<Candidate>()
        var focus: Rect? = null
        return try {
            fun visit(node: AccessibilityNodeInfo, depth: Int) {
                obtained.add(node)
                check(obtained.size <= 512 && depth <= 64)
                if (!node.isVisibleToUser || node.isPassword) return
                val bounds = Rect().also(node::getBoundsInScreen)
                if (node.isAccessibilityFocused && !bounds.isEmpty) focus = bounds
                val direction = if (forward) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN
                    else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
                val legacy = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                val supported = node.actionList.map { it.id }
                val horizontalOnly = (AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id in supported ||
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id in supported) &&
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id !in supported &&
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id !in supported
                val action = when {
                    direction.id in supported -> direction.id
                    !horizontalOnly && legacy in supported -> legacy
                    else -> null
                }
                if (node.isEnabled && !bounds.isEmpty && action != null)
                    candidates.add(Candidate(node, bounds, action))
                for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, depth + 1) }
            }
            visit(root, 0)
            val focused = focus?.let { bounds -> candidates.filter { it.bounds.contains(bounds) } }.orEmpty()
            val smallestArea = focused.minOfOrNull { it.bounds.width().toLong() * it.bounds.height() }
            val target = if (focused.isNotEmpty()) focused.singleOrNull {
                it.bounds.width().toLong() * it.bounds.height() == smallestArea
            } else candidates.singleOrNull()
            target?.let { it.node.performAction(it.action) } == true
        } catch (_: RuntimeException) {
            false
        } finally {
            if (Build.VERSION.SDK_INT < 33) obtained.forEach {
                @Suppress("DEPRECATION")
                runCatching { it.recycle() }
            }
        }
    }
}
