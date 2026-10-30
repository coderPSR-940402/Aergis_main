package com.airgesture.control

/** Request IDs prevent delayed native callbacks relabelling a newer action. */
data class ActionFeedback(val requestId: Long, val action: AirAction,
    val outcome: ActionDispatchOutcome? = null, val updatedAtMs: Long) {
    fun result(id: Long, result: ActionDispatchOutcome, now: Long): ActionFeedback =
        if (id != requestId || now < updatedAtMs || (outcome != null && outcome != ActionDispatchOutcome.SUBMITTED)) this
        else copy(outcome = result, updatedAtMs = now)
    fun visibleAt(now: Long): Boolean = now - updatedAtMs in 0L..4000L
}

enum class ActionDispatchOutcome {
    POLICY_DENIED, PLATFORM_REJECTED, SUBMITTED, COMPLETED, CANCELLED, PLATFORM_UNAVAILABLE, EXECUTION_FAILED
}
