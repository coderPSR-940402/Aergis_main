package com.airgesture.control

import android.content.Context
import com.airgesture.control.filtering.PoseGeometryEvidence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class PointerSnapshot(
    val x: Float,
    val y: Float,
    val tracking: Boolean
)

enum class PointerFeedback { TRACKING, COASTING, NO_HAND, DISABLED, AMBIGUOUS, INVALID_TIP, VISION_REJECTED }

data class VisionTelemetry(
    val totalResults: Long = 0L,
    val acceptedResults: Long = 0L,
    val rejectedResults: Long = 0L,
    val duplicateResults: Long = 0L,
    val outOfOrderResults: Long = 0L,
    val staleResults: Long = 0L,
    val excessiveGapResults: Long = 0L,
    val invalidClockOrderResults: Long = 0L,
    val lastRejectionReason: String? = null
)

data class ActionDispatchTelemetry(
    val totalOutcomes: Long = 0L,
    val policyDenied: Long = 0L,
    val platformRejected: Long = 0L,
    val platformUnavailable: Long = 0L,
    val executionFailed: Long = 0L,
    val completed: Long = 0L,
    val submitted: Long = 0L,
    val cancelled: Long = 0L,
    val lastOutcome: ActionDispatchOutcome? = null
)

data class AirRuntimeState(
    val running: Boolean = false,
    val cameraReady: Boolean = false,
    val pointerEnabled: Boolean = true,
    val gesturesEnabled: Boolean = true,
    val pointerClickingEnabled: Boolean = true,
    val controlMode: ControlMode = ControlMode.OFF,
    val foregroundContext: ForegroundContextState = ForegroundContextState(),
    val handPreference: ControlHandPreference = ControlHandPreference.EITHER,
    val visionReady: Boolean = false,
    val visionError: String? = null,
    val handsDetected: Int = 0,
    val lastGesture: String = "None",
    val handedness: String = "Unknown",
    val pointer: PointerSnapshot = PointerSnapshot(0f, 0f, false),
    val pointerFeedback: PointerFeedback = PointerFeedback.NO_HAND,
    val pointerInteraction: PointerInteractionPhase = PointerInteractionPhase.IDLE,
    /** Raw normalized vision coordinates, before screen mapping or calibration. */
    val rawPointer: PointerSnapshot = PointerSnapshot(0f, 0f, false),
    val calibrationGesture: CalibrationGestureFrame? = null,
    val visionTelemetry: VisionTelemetry = VisionTelemetry(),
    val actionDispatchTelemetry: ActionDispatchTelemetry = ActionDispatchTelemetry(),
    val actionFeedback: ActionFeedback? = null,
    val poseEvidence: PoseGeometryEvidence? = null,
    /** Invalidates actions queued under an older session or safety state. */
    val actionEpoch: Long = 0L,
    /** A selected target survives vision gaps, but never a session/safety/settings change. */
    val selectionEpoch: Long = 0L
) {
    val pointerTracking: Boolean
        get() = pointer.tracking

    val pointerX: Float
        get() = pointer.x

    val pointerY: Float
        get() = pointer.y
}

data class AirRuntimeUiState(
    val running: Boolean,
    val cameraReady: Boolean,
    val visionReady: Boolean,
    val visionError: String?,
    val handsDetected: Int,
    val lastGesture: String,
    val handedness: String,
    val pointerTracking: Boolean,
    val pointerFeedback: PointerFeedback,
    val controlMode: ControlMode,
    val foregroundSafety: ForegroundSafety,
    val safetyReason: String,
    val visionTelemetry: VisionTelemetry,
    val actionDispatchTelemetry: ActionDispatchTelemetry,
    val poseEvidence: PoseGeometryEvidence?,
    val pointerInteraction: PointerInteractionPhase = PointerInteractionPhase.IDLE
)

private fun AirRuntimeState.toUiState(): AirRuntimeUiState = AirRuntimeUiState(
    running = running,
    cameraReady = cameraReady,
    visionReady = visionReady,
    visionError = visionError,
    handsDetected = handsDetected,
    lastGesture = lastGesture,
    handedness = handedness,
    pointerTracking = pointerTracking,
    pointerFeedback = pointerFeedback,
    controlMode = controlMode,
    foregroundSafety = foregroundContext.safety,
    safetyReason = foregroundContext.reason,
    visionTelemetry = visionTelemetry,
    actionDispatchTelemetry = actionDispatchTelemetry,
    poseEvidence = poseEvidence,
    pointerInteraction = pointerInteraction
)

object AirRuntime {
    private val _state = MutableStateFlow(AirRuntimeState())
    val state: StateFlow<AirRuntimeState> = _state.asStateFlow()
    val uiState: Flow<AirRuntimeUiState> = state.map { it.toUiState() }.distinctUntilChanged()

    fun uiStateSnapshot(): AirRuntimeUiState = state.value.toUiState()

    var running: Boolean
        get() = state.value.running
        set(value) = _state.update {
            if (it.running == value) it else it.copy(running = value, actionEpoch = it.actionEpoch + 1L, selectionEpoch = it.selectionEpoch + 1L)
        }

    var cameraReady: Boolean
        get() = state.value.cameraReady
        set(value) = _state.update { it.copy(cameraReady = value) }

    var pointerEnabled: Boolean
        get() = state.value.pointerEnabled
        set(value) = _state.update {
            if (it.pointerEnabled == value) it
            else it.copy(pointerEnabled = value, actionEpoch = it.actionEpoch + 1L, selectionEpoch = it.selectionEpoch + 1L)
        }

    var pointerClickingEnabled: Boolean
        get() = state.value.pointerClickingEnabled
        set(value) = _state.update {
            if (it.pointerClickingEnabled == value) it
            else it.copy(pointerClickingEnabled = value, actionEpoch = it.actionEpoch + 1L, selectionEpoch = it.selectionEpoch + 1L)
        }

    var gesturesEnabled: Boolean
        get() = state.value.gesturesEnabled
        set(value) = _state.update {
            if (it.gesturesEnabled == value) it
            else it.copy(gesturesEnabled = value, actionEpoch = it.actionEpoch + 1L, selectionEpoch = it.selectionEpoch + 1L)
        }

    var controlMode: ControlMode
        get() = state.value.controlMode
        set(value) = _state.update {
            if (it.controlMode == value) it
            else it.copy(controlMode = value, actionEpoch = it.actionEpoch + 1L, selectionEpoch = it.selectionEpoch + 1L)
        }

    fun setForegroundContext(context: ForegroundContextState) {
        _state.update {
            if (it.foregroundContext == context) it
            else it.copy(foregroundContext = context, actionEpoch = it.actionEpoch + 1L, selectionEpoch = it.selectionEpoch + 1L)
        }
    }

    var handPreference: ControlHandPreference
        get() = state.value.handPreference
        set(value) = _state.update {
            if (it.handPreference == value) it
            else it.copy(handPreference = value, actionEpoch = it.actionEpoch + 1L, selectionEpoch = it.selectionEpoch + 1L)
        }

    internal fun invalidatePendingActions(clearSelection: Boolean = true) {
        _state.update { it.copy(actionEpoch = it.actionEpoch + 1L,
            selectionEpoch = it.selectionEpoch + if (clearSelection) 1L else 0L) }
    }

    var pointerFeedback: PointerFeedback
        get() = state.value.pointerFeedback
        set(value) = _state.update { it.copy(pointerFeedback = value) }

    var pointerInteraction: PointerInteractionPhase
        get() = state.value.pointerInteraction
        internal set(value) = _state.update { it.copy(pointerInteraction = value) }

    var visionReady: Boolean
        get() = state.value.visionReady
        set(value) = _state.update { it.copy(visionReady = value) }

    var visionError: String?
        get() = state.value.visionError
        set(value) = _state.update { it.copy(visionError = value) }

    var handsDetected: Int
        get() = state.value.handsDetected
        set(value) = _state.update { it.copy(handsDetected = value.coerceAtLeast(0)) }

    var lastGesture: String
        get() = state.value.lastGesture
        set(value) = _state.update { it.copy(lastGesture = value) }

    var handedness: String
        get() = state.value.handedness
        set(value) = _state.update { it.copy(handedness = value) }

    val pointerTracking: Boolean
        get() = state.value.pointerTracking

    val pointerX: Float
        get() = state.value.pointerX

    val pointerY: Float
        get() = state.value.pointerY

    val actionEpoch: Long
        get() = state.value.actionEpoch

    fun setPointerState(x: Float, y: Float, tracking: Boolean) {
        val safeX = x.coerceIn(0f, 1f)
        val safeY = y.coerceIn(0f, 1f)
        _state.update {
            it.copy(pointer = PointerSnapshot(safeX, safeY, tracking))
        }
    }

    fun setRawPointerState(x: Float, y: Float, tracking: Boolean) {
        val safeX = x.coerceIn(0f, 1f)
        val safeY = y.coerceIn(0f, 1f)
        _state.update {
            it.copy(rawPointer = PointerSnapshot(safeX, safeY, tracking))
        }
    }

    internal fun recordVisionResult(decision: VisionResultFreshnessPolicy.Decision) {
        _state.update { current ->
            val telemetry = current.visionTelemetry
            val total = telemetry.totalResults.safeIncrement()
            if (decision.accepted) {
                current.copy(
                    visionTelemetry = telemetry.copy(
                        totalResults = total,
                        acceptedResults = telemetry.acceptedResults.safeIncrement()
                    )
                )
            } else {
                val updated = when (decision.reason) {
                    VisionResultFreshnessPolicy.RejectionReason.DUPLICATE_TIMESTAMP ->
                        telemetry.copy(duplicateResults = telemetry.duplicateResults.safeIncrement())
                    VisionResultFreshnessPolicy.RejectionReason.OUT_OF_ORDER_TIMESTAMP ->
                        telemetry.copy(outOfOrderResults = telemetry.outOfOrderResults.safeIncrement())
                    VisionResultFreshnessPolicy.RejectionReason.STALE_RESULT ->
                        telemetry.copy(staleResults = telemetry.staleResults.safeIncrement())
                    VisionResultFreshnessPolicy.RejectionReason.EXCESSIVE_GAP ->
                        telemetry.copy(excessiveGapResults = telemetry.excessiveGapResults.safeIncrement())
                    VisionResultFreshnessPolicy.RejectionReason.INVALID_CLOCK_ORDER ->
                        telemetry.copy(invalidClockOrderResults = telemetry.invalidClockOrderResults.safeIncrement())
                    VisionResultFreshnessPolicy.RejectionReason.ACCEPTED -> telemetry
                }
                current.copy(
                    visionTelemetry = updated.copy(
                        totalResults = total,
                        rejectedResults = telemetry.rejectedResults.safeIncrement(),
                        lastRejectionReason = decision.reason.name
                    )
                )
            }
        }
    }

    internal fun resetVisionTelemetry() {
        _state.update { it.copy(visionTelemetry = VisionTelemetry()) }
    }

    internal fun recordPoseEvidence(evidence: PoseGeometryEvidence) {
        _state.update { it.copy(poseEvidence = evidence) }
    }

    internal fun clearPoseEvidence() {
        _state.update { it.copy(poseEvidence = null) }
    }

    private val feedbackSequence = java.util.concurrent.atomic.AtomicLong()
    internal fun beginActionFeedback(action: AirAction): Long {
        val id = feedbackSequence.incrementAndGet()
        val feedback = ActionFeedback(id, action, updatedAtMs = android.os.SystemClock.uptimeMillis())
        _state.update { if ((it.actionFeedback?.requestId ?: 0L) < id) it.copy(actionFeedback = feedback) else it }
        return id
    }
    internal fun recordActionFeedback(outcome: ActionDispatchOutcome, id: Long) {
        recordActionTelemetry(outcome, id)
        _state.update { current -> current.copy(actionFeedback = current.actionFeedback?.result(id, outcome,
            android.os.SystemClock.uptimeMillis())) }
    }

    internal fun recordActionDispatchOutcome(outcome: ActionDispatchOutcome) = recordActionTelemetry(outcome, null)

    private fun recordActionTelemetry(outcome: ActionDispatchOutcome, requestId: Long?) {
        if (TestingTools.state.value.status == RecordingStatus.RECORDING) {
            TestingTools.event("action_outcome", org.json.JSONObject().put("outcome", outcome.name).put("epoch", actionEpoch)
                .put("requestId", requestId ?: org.json.JSONObject.NULL))
        }
        _state.update { current ->
            val telemetry = current.actionDispatchTelemetry
            val updated = when (outcome) {
                ActionDispatchOutcome.POLICY_DENIED ->
                    telemetry.copy(policyDenied = telemetry.policyDenied.safeIncrement())
                ActionDispatchOutcome.PLATFORM_REJECTED ->
                    telemetry.copy(platformRejected = telemetry.platformRejected.safeIncrement())
                ActionDispatchOutcome.PLATFORM_UNAVAILABLE ->
                    telemetry.copy(platformUnavailable = telemetry.platformUnavailable.safeIncrement())
                ActionDispatchOutcome.EXECUTION_FAILED ->
                    telemetry.copy(executionFailed = telemetry.executionFailed.safeIncrement())
                ActionDispatchOutcome.SUBMITTED ->
                    telemetry.copy(submitted = telemetry.submitted.safeIncrement())
                ActionDispatchOutcome.COMPLETED ->
                    telemetry.copy(completed = telemetry.completed.safeIncrement())
                ActionDispatchOutcome.CANCELLED ->
                    telemetry.copy(cancelled = telemetry.cancelled.safeIncrement())
            }
            current.copy(
                actionDispatchTelemetry = updated.copy(
                    totalOutcomes = telemetry.totalOutcomes.safeIncrement(),
                    lastOutcome = outcome
                )
            )
        }
    }

    internal fun resetActionDispatchTelemetry() {
        _state.update { it.copy(actionDispatchTelemetry = ActionDispatchTelemetry()) }
    }

    fun pointerSnapshot(): PointerSnapshot = state.value.pointer

    internal fun setCalibrationGesture(frame: CalibrationGestureFrame?) {
        _state.update { it.copy(calibrationGesture = frame) }
    }

    fun rawPointerSnapshot(): PointerSnapshot = state.value.rawPointer

    fun status(context: Context): RuntimeStatus {
        val snapshot = state.value
        return RuntimeStatus(
            running = snapshot.running,
            pointerEnabled = ActionMappingStore(context).pointerEnabled(),
            cameraReady = snapshot.cameraReady,
            message = if (snapshot.running) "Aergis session active" else "Aergis session stopped"
        )
    }
}

private fun Long.safeIncrement(): Long = if (this == Long.MAX_VALUE) this else this + 1L
