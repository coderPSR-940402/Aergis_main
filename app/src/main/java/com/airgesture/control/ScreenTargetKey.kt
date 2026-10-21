package com.airgesture.control

/** Transient exact content identity; never persisted or emitted to diagnostics. */
internal object ScreenTargetKey {
    fun of(path: String, viewId: String?, bounds: String, text: CharSequence?, description: CharSequence?): String =
        listOf(path, viewId, bounds, text?.toString(), description?.toString()).joinToString("") {
            if (it == null) "-1:" else "${it.length}:$it"
        }
}
