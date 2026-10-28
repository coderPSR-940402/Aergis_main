package com.airgesture.control

import android.content.Context
import com.airgesture.control.ui.actionLabelResource

internal fun ActionFeedback.message(context: Context): String = context.getString(R.string.action_feedback_message,
    context.getString(actionLabelResource(action)), context.getString(when (outcome) {
        null -> R.string.feedback_requested
        ActionDispatchOutcome.SUBMITTED -> R.string.feedback_submitted
        ActionDispatchOutcome.COMPLETED -> if (action.usesPointerTouch) R.string.feedback_input_finished else R.string.feedback_executed
        ActionDispatchOutcome.CANCELLED -> R.string.feedback_cancelled
        ActionDispatchOutcome.POLICY_DENIED -> R.string.feedback_blocked
        ActionDispatchOutcome.PLATFORM_REJECTED -> R.string.feedback_rejected
        ActionDispatchOutcome.PLATFORM_UNAVAILABLE -> R.string.feedback_unavailable
        ActionDispatchOutcome.EXECUTION_FAILED -> R.string.feedback_failed
    }))
