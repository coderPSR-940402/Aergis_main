package com.airgesture.control

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import javax.net.ssl.HttpsURLConnection
import java.net.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class AergisVoiceState(
    val active: Boolean = false,
    val connected: Boolean = false,
    val message: String = "Voice assistant is off"
)

/**
 * User-started microphone foreground service. The assistant does not start on boot,
 * process resurrection, or simply because camera tracking is active.
 * A private-agent conversation token comes from an operator-configured authenticated broker.
 * No permanent provider API credential is accepted or included in the APK.
 */
class AergisVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversation: ConversationSession? = null
    private var started = false
    @Volatile private var closing = false
    private var generation = -1L
    private var connectionJob: Job? = null
    private var cleanupConfirmed = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            revokeVoiceAuthority()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || started) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            reportError("Microphone permission required")
            stopSelf()
            return START_NOT_STICKY
        }
        val accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        intent.removeExtra(EXTRA_ACCESS_TOKEN)
        if (!VoiceBrokerPolicy.configured(BuildConfig.VOICE_TOKEN_BROKER_URL) || accessToken.isBlank()) {
            reportError("Voice setup required. Contact your administrator.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (state.value.active) {
            // A prior service still owns provider cleanup. Never overlap microphone sessions.
            stopSelf()
            return START_NOT_STICKY
        }
        started = true
        generation = lifecycle.start()
        currentSessionId = nextSessionId.incrementAndGet()
        createChannel()
        // Android 14+ foreground-service microphone permission is declared in manifest.
        try {
            startForeground(NOTIFICATION_ID, notification())
        } catch (failure: RuntimeException) {
            reportError("Unable to start voice microphone service")
            stopSelf()
            return START_NOT_STICKY
        }
        publish(AergisVoiceState(active = true, message = "Connecting to Aergis voice"))
        connectionJob = scope.launch {
            try {
                val token = fetchConversationToken(accessToken)
                if (!lifecycle.accepts(generation)) return@launch
                val config = ConversationConfig(
                    conversationToken = token,
                    onStatusChange = { status ->
                        lifecycle.ifCurrent(generation) { when (status.toString()) {
                            "CONNECTED" -> publish(AergisVoiceState(true, true, "Aergis listening"))
                            "CONNECTING", "RECONNECTING" -> publish(AergisVoiceState(true, false, "Connecting to Aergis voice"))
                            "ERROR" -> publish(AergisVoiceState(true, false, "Voice connection failed"))
                            "DISCONNECTED" -> if (!closing) publish(AergisVoiceState(true, false, "Voice disconnected"))
                            else -> publish(AergisVoiceState(true, false, "Voice connection unavailable"))
                        } }
                    },
                    onDisconnect = { lifecycle.ifCurrent(generation) { revokeVoiceAuthority(); stopSelf() } },
                    clientTools = mapOf(
                        "get_aergis_status" to object : ClientTool {
                            override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? =
                                if (lifecycle.accepts(generation)) ClientToolResult.success(currentStatus())
                                else ClientToolResult.failure("Voice session ended")
                        },
                        "perform_aergis_action" to object : ClientTool {
                            override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? =
                                executeCommand(parameters["action"] as? String)
                        }
                    )
                )
                cleanupConfirmed = false // SDK may acquire audio before startSession returns.
                val session = ConversationClient.startSession(config, this@AergisVoiceService)
                cleanupConfirmed = true
                if (!lifecycle.accepts(generation)) {
                    cleanupConfirmed = false
                    cleanupConfirmed = withTimeoutOrNull(3000L) { session.endSession(); true } == true
                } else {
                    conversation = session
                }
            } catch (failure: Exception) {
                // Provider exceptions may contain sensitive URLs; do not log the payload.
                android.util.Log.w("AergisVoice", "Agent connection failed")
                if (lifecycle.accepts(generation)) {
                    publish(AergisVoiceState(active = true, message = "Voice service could not connect"))
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun fetchConversationToken(accessToken: String): String = withContext(Dispatchers.IO) {
        require(!accessToken.contains('\r') && !accessToken.contains('\n'))
        val connection = URL(BuildConfig.VOICE_TOKEN_BROKER_URL).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode == 200) { "Token broker rejected request" }
            // Bound response and never include tokens or response bodies in logs/UI.
            val body = connection.inputStream.use { input ->
                val deadline = android.os.SystemClock.elapsedRealtime() + 15_000L
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    check(android.os.SystemClock.elapsedRealtime() <= deadline) { "Broker response timed out" }
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 16_384) { "Broker response too large" }
                    output.write(buffer, 0, count)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
            JSONObject(body).getString("conversation_token").also { check(it.isNotBlank()) }
        } finally { connection.disconnect() }
    }

    private fun currentStatus(): String {
        val runtime = AirRuntime.state.value
        return JSONObject()
            .put("running", runtime.running)
            .put("camera_ready", runtime.cameraReady)
            .put("vision_ready", runtime.visionReady)
            .put("control_mode", runtime.controlMode.name)
            .put("foreground_safety", runtime.foregroundContext.safety.name)
            .put("pointer_enabled", runtime.pointerEnabled)
            .put("gestures_enabled", runtime.gesturesEnabled)
            .put("voice_connected", state.value.connected)
            .toString()
    }

    private suspend fun executeCommand(command: String?): ClientToolResult =
        withContext(Dispatchers.Main.immediate) {
            if (!lifecycle.accepts(generation) || !state.value.connected) return@withContext ClientToolResult.failure("Voice session disconnected")
            if (AergisVoiceCommandPolicy.isSessionControl(command)) {
                return@withContext when (command) {
                    "PAUSE" -> if (AirRuntime.running) {
                        AirRuntime.controlMode = ControlMode.PAUSED
                        AirAccessibilityService.instance?.cancelPointerTouch()
                        ClientToolResult.success("Controls paused")
                    } else ClientToolResult.failure("Capture is not running")
                    "DISARM" -> {
                        AirRuntime.controlMode = if (AirRuntime.running) ControlMode.READY else ControlMode.OFF
                        AirAccessibilityService.instance?.cancelPointerTouch()
                        ClientToolResult.success("Controls disarmed")
                    }
                    else -> {
                        AirRuntime.controlMode = ControlMode.OFF
                        AirAccessibilityService.instance?.cancelPointerTouch()
                        GestureCaptureService.stopCapture(this@AergisVoiceService)
                        ClientToolResult.success("Camera capture stop requested; voice remains available")
                    }
                }
            }
            val action = AergisVoiceCommandPolicy.parse(command)
                ?: return@withContext ClientToolResult.failure("Unsupported voice action")
            if (action == AirAction.TOGGLE_POINTER && AirRuntime.controlMode != ControlMode.ARMED)
                return@withContext ClientToolResult.failure("Arm controls before changing pointer")
            val service = AirAccessibilityService.instance
                ?: return@withContext ClientToolResult.failure("Aergis accessibility service is unavailable")
            val requestId = AirRuntime.beginActionFeedback(action)
            val pending = AirRuntime.observeActionResult(requestId)
            val result = try {
                service.dispatchVoice(action, requestId)
                awaitVoiceActionTerminal(pending, 3000L)
            } finally { AirRuntime.releaseActionResult(requestId) }
            when (result) {
                ActionDispatchOutcome.COMPLETED -> ClientToolResult.success("Executed: ${action.name}; app effect not independently verified")
                ActionDispatchOutcome.SUBMITTED -> ClientToolResult.failure("Command submitted; terminal outcome unconfirmed")
                ActionDispatchOutcome.POLICY_DENIED -> ClientToolResult.failure("Blocked by current control or screen safety state")
                ActionDispatchOutcome.PLATFORM_UNAVAILABLE -> ClientToolResult.failure("Required Android service unavailable")
                ActionDispatchOutcome.PLATFORM_REJECTED -> ClientToolResult.failure("Android rejected the command")
                ActionDispatchOutcome.EXECUTION_FAILED -> ClientToolResult.failure("Command failed")
                ActionDispatchOutcome.CANCELLED -> ClientToolResult.failure("Command cancelled")
                else -> ClientToolResult.failure("Command terminal outcome could not be confirmed within 3 seconds")
            }
        }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Aergis voice", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(): Notification {
        val stopIntent = Intent(this, AergisVoiceService::class.java).setAction(ACTION_STOP)
        val pendingStop = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aergis voice assistant")
            .setContentText("Microphone active · Tap Stop to disconnect")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop voice", pendingStop).build())
            .build()
    }

    override fun onDestroy() {
        closing = true
        if (started) {
            lifecycle.stop()
            publish(AergisVoiceState(active = true, message = "Stopping voice microphone"))
            // Keep active (and TTS suppressed) until a pending start and end both finish.
            // New starts are refused while cleanup owns the mic. Late callbacks are fenced.
            scope.launch {
                try {
                    cleanupConfirmed = finishVoiceSession(connectionJob, 3000L) {
                        val ending = conversation
                        conversation = null
                        if (ending != null) {
                            cleanupConfirmed = false
                            withTimeoutOrNull(3000L) { ending.endSession(); true } == true
                        } else cleanupConfirmed
                    }
                } catch (failure: Exception) {
                    cleanupConfirmed = false
                } finally {
                    publish(if (cleanupConfirmed) AergisVoiceState() else
                        AergisVoiceState(active = true, message = "Voice cleanup unconfirmed; force-stop and reopen Aergis before restarting"))
                    if (connectionJob?.isCompleted != false) scope.cancel()
                    else connectionJob?.invokeOnCompletion { scope.cancel() }
                }
            }
        } else scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val lifecycle = VoiceSessionLifecycle()
        private val nextSessionId = java.util.concurrent.atomic.AtomicLong()
        private var currentSessionId = 0L
        const val EXTRA_ACCESS_TOKEN = "broker_access_token"
        private const val CHANNEL_ID = "aergis_voice"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.airgesture.control.START_VOICE"
        const val ACTION_STOP = "com.airgesture.control.STOP_VOICE"
        private val state = MutableStateFlow(AergisVoiceState())
        val status: StateFlow<AergisVoiceState> = state
        fun sessionId(): Long = currentSessionId
        fun isConnected(): Boolean = state.value.connected
        fun reportError(message: String) { if (!state.value.active) publish(AergisVoiceState(message = message)) }
        private fun publish(value: AergisVoiceState) {
            if (state.value.connected != value.connected) {
                AirRuntime.invalidatePendingActions(clearSelection = true, invalidateFrames = false)
            }
            state.value = value
        }
        private fun revokeVoiceAuthority() {
            lifecycle.stop()
            publish(state.value.copy(connected = false))
        }
        fun stop(context: Context) {
            revokeVoiceAuthority()
            context.stopService(Intent(context, AergisVoiceService::class.java))
        }
    }
}
