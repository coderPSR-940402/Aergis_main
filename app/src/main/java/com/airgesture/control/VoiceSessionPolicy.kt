package com.airgesture.control

import java.net.URI
import kotlinx.coroutines.flow.first

/** Local generation makes late provider callbacks incapable of reviving revoked authority. */
internal class VoiceSessionLifecycle {
    private var generation = 0L
    private var active = false
    @Synchronized fun start(): Long { active = true; return ++generation }
    @Synchronized fun stop() { active = false; generation++ }
    @Synchronized fun accepts(session: Long): Boolean = active && generation == session
    @Synchronized fun ifCurrent(session: Long, block: () -> Unit) { if (accepts(session)) block() }
}
internal object VoiceFeedbackPolicy {
    fun maySpeak(microphoneActive: Boolean) = !microphoneActive
}
internal object VoiceSelectionAuthority {
    fun valid(origin: ActionOrigin, selectedSession: Long, currentSession: Long, connected: Boolean): Boolean =
        origin != ActionOrigin.VOICE || (connected && selectedSession == currentSession)
}
internal object VoiceBrokerPolicy {
    fun configured(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.fragment == null
    }.getOrDefault(false)
}

internal class VoiceSpeechGate(private val stopSpeech: () -> Unit) {
    private var microphoneActive = false
    fun microphoneChanged(active: Boolean) {
        microphoneActive = active
        if (active) stopSpeech()
    }
    fun maySpeak() = VoiceFeedbackPolicy.maySpeak(microphoneActive)
}

/** SUBMITTED only acknowledges dispatch; wait for completion/failure or a bounded unknown. */
internal suspend fun awaitVoiceActionTerminal(
    result: kotlinx.coroutines.flow.StateFlow<ActionDispatchOutcome?>, timeoutMs: Long
): ActionDispatchOutcome? = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
    result.first { it != null && it != ActionDispatchOutcome.SUBMITTED }
}

/** Timing out a join does not cancel pending start: its late-session cleanup must still run. */
internal suspend fun finishVoiceSession(
    pendingStart: kotlinx.coroutines.Job?, timeoutMs: Long, releaseSession: suspend () -> Boolean
): Boolean {
    val finished = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { pendingStart?.join(); true } == true
    return finished && releaseSession()
}
