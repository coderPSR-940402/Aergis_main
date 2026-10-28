package com.airgesture.control

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Refresh nodes for every command; never click cached nodes or guess a coordinate. */
internal class ScreenTargetNavigator(private val highlight: ScreenTargetHighlight) {
    private val state = ScreenSelectionState()
    private data class Target(val key: String, val node: AccessibilityNodeInfo, val bounds: Rect,
        val containerPath: String, val path: String, val inCollection: Boolean)
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
            val identities = HashMap<String, String>()
            val semanticIdentities = HashMap<String, String>()
            val rowScopes = HashMap<String, String>()
            var visited = 0
            fun visit(node: AccessibilityNodeInfo, path: String, itemPath: String?, parentScope: String = ""): String {
                obtained.add(node)
                check(++visited <= 512) { "Window too large for bounded target scan" }
                if (!node.isVisibleToUser || !node.isEnabled || node.isPassword) return ""
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                val rowPath = if (node.collectionItemInfo != null) path else itemPath
                if (node.collectionItemInfo != null) rowScopes[path] = parentScope
                val ownScope = ScreenTargetKey.of(path, node.viewIdResourceName,
                    node.className?.toString().orEmpty(), node.text, node.contentDescription)
                val scope = "${parentScope.length}:$parentScope${ownScope.length}:$ownScope"
                // Reserve pre-order navigation before collecting the visible subtree.
                val targetIndex = if (node.isClickable && !bounds.isEmpty) {
                    targets.add(Target("", node, bounds, rowPath ?: "root", path, rowPath != null)); targets.lastIndex
                } else -1
                val ownIdentity = ScreenTargetKey.of(path, node.viewIdResourceName,
                    bounds.toString(), node.text, node.contentDescription)
                val semantic = StringBuilder(ScreenTargetKey.of("", node.viewIdResourceName,
                    "${node.className}:${node.isClickable}:${node.isFocusable}:${node.actionList.map { it.id }.sorted()}",
                    node.text, node.contentDescription))
                val identity = buildString {
                    append(ownIdentity.length).append(':').append(ownIdentity)
                    for (i in 0 until node.childCount) node.getChild(i)?.let {
                        val child = visit(it, "$path/$i", rowPath, scope)
                        append(child.length).append(':').append(child)
                        val childSemantic = semanticIdentities["$path/$i"].orEmpty()
                        semantic.append(childSemantic.length).append(':').append(childSemantic)
                    }
                }
                // Clickable rows often have no label themselves; a recycled row's
                // changed child label must revoke the old selection just as its own would.
                identities[path] = identity
                semanticIdentities[path] = semantic.toString()
                if (targetIndex >= 0) targets[targetIndex] = targets[targetIndex].copy(key = identity)
                return identity
            }
            visit(root, "root", null)
            // A button's label may stay "Delete" while a sibling identifies a new
            // item. A collection item supplies an explicit row boundary, allowing
            // unrelated rows to change. Without that metadata, an immediate parent
            // may only be an actions wrapper: bind to the whole visible window so
            // a changed identifying label at any ancestor depth revokes selection.
            for (i in targets.indices) {
                val target = targets[i]
                val row = target.containerPath
                val explicitRow = target.inCollection
                if (explicitRow) {
                    val container = checkNotNull(semanticIdentities[row])
                    val local = target.path.removePrefix(row)
                    val parent = checkNotNull(rowScopes[row])
                    // Row identity and within-row control path survive insertion/movement.
                    // A duplicate identity is rejected by retain/confirm; never guess a row.
                    targets[i] = target.copy(key = ScreenTargetKey.of(local, target.node.viewIdResourceName,
                        parent, semanticIdentities[target.path], container))
                } else {
                    val container = checkNotNull(identities[row])
                    targets[i] = target.copy(key = "${target.key.length}:${target.key}${container.length}:$container")
                }
            }
            val context = "${root.packageName}:${root.windowId}:$epoch"
            val keys = targets.map { it.key }
            if (action == null) {
                // Unrelated animations/content updates are not loss of this exact target.
                val key = state.retain(keys, context)
                val target = targets.singleOrNull { it.key == key }
                if (target != null && highlight.show(target.bounds)) true else { clear(); false }
            } else if (action == AirAction.SELECT_ITEM) {
                val visible = highlight.isVisible
                val key = state.confirm(keys, context)
                highlight.hide()
                visible && key != null && targets.single { it.key == key }.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
