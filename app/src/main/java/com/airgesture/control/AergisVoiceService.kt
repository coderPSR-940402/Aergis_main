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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
 * No ElevenLabs credential is included in the APK. The public agent ID is not a secret.
 */
class AergisVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversation: ConversationSession? = null
    private var started = false
    private var closing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START || started) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            reportError("Microphone permission required")
            stopSelf()
            return START_NOT_STICKY
        }
        started = true
        createChannel()
        // Android 14+ foreground-service microphone permission is declared in manifest.
        try {
            startForeground(NOTIFICATION_ID, notification())
        } catch (failure: RuntimeException) {
            reportError("Unable to start voice microphone service")
            stopSelf()
            return START_NOT_STICKY
        }
        state.value = AergisVoiceState(active = true, message = "Connecting to Aergis voice")
        scope.launch {
            try {
                val config = ConversationConfig(
                    agentId = AGENT_ID,
                    onStatusChange = { status ->
                        when (status.toString()) {
                            "CONNECTED" -> state.value = AergisVoiceState(true, true, "Aergis listening")
                            "CONNECTING" -> state.value = AergisVoiceState(true, false, "Connecting to Aergis voice")
                            "ERROR" -> state.value = AergisVoiceState(true, false, "Voice connection failed")
                            "DISCONNECTED" -> if (!closing) state.value = AergisVoiceState(true, false, "Voice disconnected")
                        }
                    },
                    onDisconnect = { if (!closing) stopSelf() },
                    clientTools = mapOf(
                        "get_aergis_status" to object : ClientTool {
                            override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? =
                                ClientToolResult.success(currentStatus())
                        },
                        "perform_aergis_action" to object : ClientTool {
                            override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? =
                                executeCommand(parameters["action"] as? String)
                        }
                    )
                )
                conversation = ConversationClient.startSession(config, this@AergisVoiceService)
            } catch (failure: Exception) {
                android.util.Log.w("AergisVoice", "Agent connection failed", failure)
                reportError("Voice service could not connect")
                stopSelf()
            }
        }
        return START_NOT_STICKY
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
            if (!state.value.connected) return@withContext ClientToolResult.failure("Voice session disconnected")
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
            val previous = AirRuntime.state.value.actionFeedback?.requestId ?: 0L
            service.dispatchVoice(action)
            val issued = AirRuntime.state.value.actionFeedback
            if (issued == null || issued.requestId <= previous || issued.action != action)
                return@withContext ClientToolResult.failure("Command not accepted for dispatch")
            val result = withTimeoutOrNull(3000L) {
                AirRuntime.state.map { it.actionFeedback }
                    .filterNotNull()
                    .first { it.requestId == issued.requestId && it.outcome != null }
            }
            when (result?.outcome) {
                ActionDispatchOutcome.COMPLETED -> ClientToolResult.success("Completed: ${action.name}")
                ActionDispatchOutcome.SUBMITTED -> ClientToolResult.success("Submitted, not confirmed: ${action.name}")
                ActionDispatchOutcome.POLICY_DENIED -> ClientToolResult.failure("Blocked by current control or screen safety state")
                ActionDispatchOutcome.PLATFORM_UNAVAILABLE -> ClientToolResult.failure("Required Android service unavailable")
                ActionDispatchOutcome.PLATFORM_REJECTED -> ClientToolResult.failure("Android rejected the command")
                ActionDispatchOutcome.EXECUTION_FAILED -> ClientToolResult.failure("Command failed")
                ActionDispatchOutcome.CANCELLED -> ClientToolResult.failure("Command cancelled")
                else -> ClientToolResult.failure("Command outcome could not be confirmed")
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
        conversation?.endSession()
        conversation = null
        scope.cancel()
        state.value = AergisVoiceState()
        super.onDestroy()
    }

    companion object {
        private const val AGENT_ID = "agent_4101m73erqmeenm9mydanx3fjayk"
        private const val CHANNEL_ID = "aergis_voice"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.airgesture.control.START_VOICE"
        const val ACTION_STOP = "com.airgesture.control.STOP_VOICE"
        private val state = MutableStateFlow(AergisVoiceState())
        val status: StateFlow<AergisVoiceState> = state
        fun isConnected(): Boolean = state.value.connected
        fun reportError(message: String) { state.value = AergisVoiceState(message = message) }
        fun stop(context: Context) {
            context.stopService(Intent(context, AergisVoiceService::class.java))
        }
    }
}
