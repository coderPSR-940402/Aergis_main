package com.airgesture.control

/** Filters transitions before the occurrence gate; never manufactures neutral evidence. */
internal class StaticPoseConfirmation {
    private var key: String? = null
    private var since = 0L
    private var last = -1L
    fun accept(id: String?, owner: String?, epoch: Long, timestamp: Long, valid: Boolean, holdMs: Long): Boolean {
        if (!valid || owner == null || timestamp < 0 || (last >= 0 && (timestamp <= last || timestamp - last > 250))) {
            reset(); return false
        }
        last = timestamp
        if (id == null) { key = null; return true }
        val next = "$owner/$epoch/$id"
        if (key != next) { key = next; since = timestamp; return false }
        return timestamp - since >= holdMs.coerceAtLeast(100L)
    }
    fun reset() { key = null; last = -1L }
}
