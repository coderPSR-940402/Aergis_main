package com.airgesture.control

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/** Short opt-in offline speech for gesture outcomes when the conversational mic is not active. */
internal class AergisSpeechFeedback(private val context: Context, scope: CoroutineScope) {
    private var ready = false
    private val tts = TextToSpeech(context) { status -> ready = status == TextToSpeech.SUCCESS }
    private val feedbackJob: Job = scope.launch {
        AirRuntime.state.map { it.actionFeedback }.distinctUntilChanged().collectLatest { feedback ->
            val outcome = feedback?.outcome ?: return@collectLatest
            if (outcome == ActionDispatchOutcome.SUBMITTED) delay(700L)
            val current = AirRuntime.state.value
            if (current.actionFeedback?.requestId != feedback.requestId ||
                current.actionFeedback?.outcome != feedback.outcome ||
                !current.running || current.foregroundContext.safety != ForegroundSafety.SAFE ||
                !enabled(context) || AergisVoiceService.isConnected()) return@collectLatest
            val spoken = when (outcome) {
                ActionDispatchOutcome.COMPLETED -> "${feedback.action.name.replace('_', ' ').lowercase(Locale.ENGLISH)} complete"
                ActionDispatchOutcome.SUBMITTED -> "${feedback.action.name.replace('_', ' ').lowercase(Locale.ENGLISH)} submitted"
                ActionDispatchOutcome.POLICY_DENIED -> "Action blocked"
                ActionDispatchOutcome.PLATFORM_UNAVAILABLE -> "Action unavailable"
                ActionDispatchOutcome.PLATFORM_REJECTED, ActionDispatchOutcome.EXECUTION_FAILED -> "Action failed"
                ActionDispatchOutcome.CANCELLED -> "Action cancelled"
            }
            if (ready) tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "aergis-feedback-${feedback.requestId}")
        }
    }
    fun close() {
        feedbackJob.cancel()
        tts.stop()
        tts.shutdown()
    }
    companion object {
        private const val PREF_NAME = "aergis_voice"
        private const val KEY_FEEDBACK = "spoken_feedback"
        fun enabled(context: Context) = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FEEDBACK, false)
        fun setEnabled(context: Context, enabled: Boolean) =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_FEEDBACK, enabled).apply()
    }
}
