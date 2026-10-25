package com.airgesture.control

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Refresh nodes for every command; never click cached nodes or guess a coordinate. */
internal class ScreenTargetNavigator(private val highlight: ScreenTargetHighlight) {
    private val state = ScreenSelectionState()
    private data class Target(val key: String, val node: AccessibilityNodeInfo, val bounds: Rect)
    fun clear() { state.clear(); highlight.hide() }
    fun refresh(root: AccessibilityNodeInfo?, epoch: Long): Boolean {
        if (!highlight.isVisible) { clear(); return false }
        return update(root, null, epoch)
    }
    fun perform(root: AccessibilityNodeInfo?, action: AirAction, epoch: Long): Boolean {
        if (action !in setOf(AirAction.NEXT_ITEM, AirAction.PREVIOUS_ITEM, AirAction.SELECT_ITEM)) { clear(); return false }
        return update(root, action, epoch)
    }
    private fun update(root: AccessibilityNodeInfo?, action: AirAction?, epoch: Long): Boolean {
        if (root == null) { clear(); return false }
        val obtained = ArrayList<AccessibilityNodeInfo>()
        return try {
            val targets = ArrayList<Target>()
            var visited = 0
            fun visit(node: AccessibilityNodeInfo, path: String) {
                obtained.add(node)
                check(++visited <= 512) { "Window too large for bounded target scan" }
                if (!node.isVisibleToUser || !node.isEnabled || node.isPassword) return
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                if (node.isClickable && !bounds.isEmpty) {
                    val key = ScreenTargetKey.of(path, node.viewIdResourceName, bounds.toString(), node.text, node.contentDescription)
                    targets.add(Target(key, node, bounds))
                }
                for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it, "$path/$i") }
            }
            visit(root, "root")
            val context = "${root.packageName}:${root.windowId}:$epoch"
            val keys = targets.map { it.key }
            if (action == null) {
                // Unrelated animations/content updates are not loss of this exact target.
                val key = state.retain(keys, context)
                if (key != null) true else { clear(); false }
            } else if (action == AirAction.SELECT_ITEM) {
                val visible = highlight.isVisible
                val key = state.confirm(keys, context)
                highlight.hide()
                visible && key != null && targets.first { it.key == key }.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                val key = state.move(keys, context, action == AirAction.NEXT_ITEM)
                val target = targets.firstOrNull { it.key == key }
                if (target != null && highlight.show(target.bounds)) true else { clear(); false }
            }
        } catch (_: RuntimeException) { clear(); false }
        finally {
            if (android.os.Build.VERSION.SDK_INT < 33) obtained.forEach {
                @Suppress("DEPRECATION")
                runCatching { it.recycle() }
            }
        }
    }
}
