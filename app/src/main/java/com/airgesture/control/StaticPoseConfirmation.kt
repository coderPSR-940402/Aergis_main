package com.airgesture.control

/**
 * Compatibility gate for the gesture-first pipeline.
 *
 * Occurrence safety already lives in [GestureTransactionStateMachine]: confidence,
 * ownership, action epoch, freshness, neutral rearm and exactly-once consumption.
 * Legacy holdMs values are retained for rollback/persistence only and must never delay
 * a recognized mapped gesture.
 */
internal class StaticPoseConfirmation {
    @Suppress("UNUSED_PARAMETER")
    fun accept(
        id: String?,
        owner: String?,
        epoch: Long,
        timestamp: Long,
        valid: Boolean,
        holdMs: Long
    ): Boolean = valid && !owner.isNullOrBlank() && timestamp >= 0L

    fun reset() = Unit
}
