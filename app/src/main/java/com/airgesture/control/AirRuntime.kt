package com.airgesture.control

import android.content.Context
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

data class AirRuntimeState(
    val running: Boolean = false,
    val cameraReady: Boolean = false,
    val pointerEnabled: Boolean = true,
    val gesturesEnabled: Boolean = true,
    val controlMode: ControlMode = ControlMode.OFF,
    val motionActive: Boolean = false,
    val motionMagnitude: Float = 0f,
    val motionReason: String? = null,
    val motionSensorsAvailable: Boolean = false,
    val foregroundContext: ForegroundContextState = ForegroundContextState(),
    val handPreference: ControlHandPreference = ControlHandPreference.EITHER,
    val visionReady: Boolean = false,
    val visionError: String? = null,
    val handsDetected: Int = 0,
    val lastGesture: String = "None",
    val handedness: String = "Unknown",
    val pointer: PointerSnapshot = PointerSnapshot(0f, 0f, false),
    val visionTelemetry: VisionTelemetry = VisionTelemetry(),
    /** Invalidates actions queued under an older session or safety state. */
    val actionEpoch: Long = 0L
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
    val controlMode: ControlMode,
    val motionActive: Boolean,
    val motionSensorsAvailable: Boolean,
    val foregroundSafety: ForegroundSafety,
    val safetyReason: String
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
    controlMode = controlMode,
    motionActive = motionActive,
    motionSensorsAvailable = motionSensorsAvailable,
    foregroundSafety = foregroundContext.safety,
    safetyReason = motionReason ?: foregroundContext.reason
)

object AirRuntime {
    private val _state = MutableStateFlow(AirRuntimeState())
    val state: StateFlow<AirRuntimeState> = _state.asStateFlow()
    val uiState: Flow<AirRuntimeUiState> = state.map { it.toUiState() }.distinctUntilChanged()

    fun uiStateSnapshot(): AirRuntimeUiState = state.value.toUiState()

    var running: Boolean
        get() = state.value.running
        set(value) = _state.update {
            if (it.running == value) it else it.copy(running = value, actionEpoch = it.actionEpoch + 1L)
        }

    var cameraReady: Boolean
        get() = state.value.cameraReady
        set(value) = _state.update { it.copy(cameraReady = value) }

    var pointerEnabled: Boolean
        get() = state.value.pointerEnabled
        set(value) = _state.update { it.copy(pointerEnabled = value) }

    var gesturesEnabled: Boolean
        get() = state.value.gesturesEnabled
        set(value) = _state.update {
            if (it.gesturesEnabled == value) it
            else it.copy(gesturesEnabled = value, actionEpoch = it.actionEpoch + 1L)
        }

    var controlMode: ControlMode
        get() = state.value.controlMode
        set(value) = _state.update {
            if (it.controlMode == value) it
            else it.copy(controlMode = value, actionEpoch = it.actionEpoch + 1L)
        }

    var motionActive: Boolean
        get() = state.value.motionActive
        set(value) = _state.update {
            if (it.motionActive == value) it
            else it.copy(motionActive = value, actionEpoch = it.actionEpoch + 1L)
        }

    fun setMotionState(state: MotionState) {
        _state.update {
            if (it.motionActive == state.active && it.motionReason == state.reason) {
                it
            } else {
                it.copy(
                    motionActive = state.active,
                    motionMagnitude = state.magnitude,
                    motionReason = state.reason,
                    actionEpoch = it.actionEpoch + 1L
                )
            }
        }
    }

    var motionSensorsAvailable: Boolean
        get() = state.value.motionSensorsAvailable
        set(value) = _state.update { it.copy(motionSensorsAvailable = value) }

    fun setForegroundContext(context: ForegroundContextState) {
        _state.update {
            if (it.foregroundContext == context) it
            else it.copy(foregroundContext = context, actionEpoch = it.actionEpoch + 1L)
        }
    }

    var handPreference: ControlHandPreference
        get() = state.value.handPreference
        set(value) = _state.update { it.copy(handPreference = value) }

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

    fun pointerSnapshot(): PointerSnapshot = state.value.pointer

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
