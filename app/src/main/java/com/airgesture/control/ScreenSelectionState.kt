package com.airgesture.control

/** Only a still-present target in the same window/epoch can be confirmed. */
internal class ScreenSelectionState {
    private var selected: String? = null
    private var context: String? = null
    fun clear() { selected = null; context = null }
    fun move(keys: List<String>, contextKey: String, forward: Boolean): String? {
        if (keys.isEmpty()) { clear(); return null }
        val index = if (context == contextKey) keys.indexOf(selected) else -1
        val next = if (index < 0) { if (forward) 0 else keys.lastIndex }
            else (index + if (forward) 1 else -1).coerceIn(0, keys.lastIndex)
        context = contextKey; selected = keys[next]
        return selected
    }
    fun retain(keys: List<String>, contextKey: String): String? {
        val result = selected?.takeIf { context == contextKey && keys.count { key -> key == it } == 1 }
        if (result == null) clear()
        return result
    }
    fun confirm(keys: List<String>, contextKey: String): String? {
        val result = retain(keys, contextKey)
        clear()
        return result
    }
}
