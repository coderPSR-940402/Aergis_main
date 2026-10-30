package com.airgesture.control

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.os.SystemClock

internal data class ScreenSelectionEvent(val kind: String, val selectionId: Long?, val bounds: Rect?,
    val reason: String, val requestId: Long?)

/** Refresh nodes for every command; never click cached nodes or guess a coordinate. */
internal class ScreenTargetNavigator(private val highlight: ScreenTargetHighlight,
    private val record: (ScreenSelectionEvent) -> Unit = {}) {
    private val state = ScreenSelectionState()
    private var sequence = 0L
    private var selectedKey: String? = null
    private var selectedBounds: Rect? = null
    private data class Activation(val node: AccessibilityNodeInfo, val event: ScreenSelectionEvent,
        val epoch: Long, val windowId: Int, val deadline: Long)
    private var activation: Activation? = null
    fun expireActivation(epoch: Long, now: Long = SystemClock.uptimeMillis()) {
        val pending = activation ?: return
        if (pending.epoch != epoch || now > pending.deadline)
            endActivation(if (pending.epoch != epoch) "selection_authority_changed" else "no_matching_click_event")
    }
    fun observe(event: AccessibilityEvent, epoch: Long) {
        expireActivation(epoch)
        val pending = activation ?: return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED || event.windowId != pending.windowId) return
        val source = runCatching { event.source }.getOrNull() ?: return
        try {
            if (source == pending.node && source.packageName == pending.node.packageName) {
                record(pending.event.copy(kind = "activation_confirmed", reason = "android_view_clicked"))
                releaseActivation()
            }
        } catch (_: RuntimeException) { /* Stale event nodes cannot confirm activation. */ }
        finally { recycle(source) }
    }
    fun close() { clear(); endActivation("observation_ended") }
    private fun endActivation(reason: String) {
        activation?.let { record(it.event.copy(kind = "activation_unconfirmed", reason = reason)) }
        releaseActivation()
    }
    private fun releaseActivation() { activation?.node?.let(::recycle); activation = null }
    private fun recycle(node: AccessibilityNodeInfo) {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            @Suppress("DEPRECATION")
            runCatching { node.recycle() }
        }
    }
    val hasSelection: Boolean get() = selectedKey != null
    private fun selected(target: Target, requestId: Long?) {
        val changed = selectedKey != target.key
        if (changed) sequence++
        if (changed || selectedBounds != target.bounds || requestId != null) record(ScreenSelectionEvent(
            when { changed -> "selected"; selectedBounds != target.bounds -> "moved"; else -> "unchanged" },
            sequence, Rect(target.bounds), if (!changed && selectedBounds == target.bounds) "navigation_boundary" else "target_retained", requestId))
        selectedKey = target.key
        selectedBounds = Rect(target.bounds)
    }
    private data class Target(val key: String, val node: AccessibilityNodeInfo, val bounds: Rect,
        val containerPath: String, val path: String, val inCollection: Boolean)
    fun clear(reason: String = "selection_invalidated", requestId: Long? = null) {
        if (selectedKey != null) record(ScreenSelectionEvent("cleared", sequence, selectedBounds, reason, requestId))
        selectedKey = null; selectedBounds = null
        state.clear(); highlight.hide()
    }
    fun refresh(root: AccessibilityNodeInfo?, epoch: Long): Boolean {
        if (selectedKey == null) return false
        return update(root, null, epoch)
    }
    fun perform(root: AccessibilityNodeInfo?, action: AirAction, epoch: Long, requestId: Long? = null,
        canActivate: () -> Boolean = { true }): Boolean {
        if (action !in setOf(AirAction.NEXT_ITEM, AirAction.PREVIOUS_ITEM, AirAction.SELECT_ITEM)) { clear(); return false }
        return update(root, action, epoch, requestId, canActivate)
    }
    private fun update(root: AccessibilityNodeInfo?, action: AirAction?, epoch: Long, requestId: Long? = null,
        canActivate: () -> Boolean = { true }): Boolean {
        if (root == null) {
            if (action == AirAction.SELECT_ITEM) record(ScreenSelectionEvent("activation_rejected", selectedKey?.let { sequence },
                selectedBounds, "no_active_window", requestId))
            clear("no_active_window", requestId); return false
        }
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
                    "${node.className}:${node.isClickable}:${node.isFocusable}:${node.actionList.map { it.id }.sorted()}",
                    node.text, node.contentDescription)
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
                if (target != null && highlight.show(target.bounds)) { selected(target, requestId); true }
                else { clear("target_missing_or_changed", requestId); false }
            } else if (action == AirAction.SELECT_ITEM) {
                val visible = highlight.isVisible
                val key = state.retain(keys, context)
                val target = targets.singleOrNull { it.key == key }
                if (target != null && selectedBounds != target.bounds) {
                    // A new location must be drawn before it can be confirmed.
                    if (highlight.show(target.bounds)) selected(target, null) else clear("highlight_unavailable", requestId)
                    record(ScreenSelectionEvent("activation_rejected", selectedKey?.let { sequence }, target.bounds,
                        "target_moved_needs_confirmation", requestId))
                    return false
                }
                state.confirm(keys, context)
                val authorized = canActivate()
                val accepted = if (visible && target != null && authorized) {
                    endActivation("superseded_by_new_activation")
                    @Suppress("DEPRECATION")
                    val copy = AccessibilityNodeInfo.obtain(target.node)
                    activation = Activation(copy, ScreenSelectionEvent("activation_accepted", sequence,
                        Rect(target.bounds), "android_click_accepted", requestId), epoch, root.windowId,
                        SystemClock.uptimeMillis() + 1000L)
                    try { target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                    catch (_: RuntimeException) { false }
                } else false
                if (!accepted) releaseActivation()
                record(ScreenSelectionEvent(if (accepted) "activation_accepted" else "activation_rejected",
                    selectedKey?.let { sequence }, target?.bounds ?: selectedBounds,
                    when { !authorized -> "action_authority_changed"; !visible -> "highlight_not_drawn"; target == null -> "target_missing_or_changed"
                        accepted -> "android_click_accepted"; else -> "android_click_rejected" }, requestId))
                selectedKey = null; selectedBounds = null
                highlight.hide()
                accepted
            } else {
                val key = state.move(keys, context, action == AirAction.NEXT_ITEM)
                val target = targets.firstOrNull { it.key == key }
                if (target != null && highlight.show(target.bounds)) { selected(target, requestId); true }
                else { clear("target_missing_or_changed", requestId); false }
            }
        } catch (_: RuntimeException) {
            if (action == AirAction.SELECT_ITEM) record(ScreenSelectionEvent("activation_rejected", selectedKey?.let { sequence },
                selectedBounds, "window_scan_or_click_failed", requestId))
            clear("window_scan_or_click_failed", requestId); false
        }
        finally {
            if (android.os.Build.VERSION.SDK_INT < 33) obtained.forEach {
                @Suppress("DEPRECATION")
                runCatching { it.recycle() }
            }
        }
    }
}
