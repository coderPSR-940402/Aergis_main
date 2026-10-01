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

data class AirRuntimeState(
    val running: Boolean = false,
    val cameraReady: Boolean = false,
    val pointerEnabled: Boolean = true,
    val gesturesEnabled: Boolean = true,
    val handPreference: ControlHandPreference = ControlHandPreference.EITHER,
    val visionReady: Boolean = false,
    val visionError: String? = null,
    val handsDetected: Int = 0,
    val lastGesture: String = "None",
    val handedness: String = "Unknown",
    val pointer: PointerSnapshot = PointerSnapshot(0f, 0f, false)
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
    val pointerTracking: Boolean
)

private fun AirRuntimeState.toUiState(): AirRuntimeUiState = AirRuntimeUiState(
    running = running,
    cameraReady = cameraReady,
    visionReady = visionReady,
    visionError = visionError,
    handsDetected = handsDetected,
    lastGesture = lastGesture,
    handedness = handedness,
    pointerTracking = pointerTracking
)

object AirRuntime {
    private val _state = MutableStateFlow(AirRuntimeState())
    val state: StateFlow<AirRuntimeState> = _state.asStateFlow()
    val uiState: Flow<AirRuntimeUiState> = state.map { it.toUiState() }.distinctUntilChanged()

    fun uiStateSnapshot(): AirRuntimeUiState = state.value.toUiState()

    var running: Boolean
        get() = state.value.running
        set(value) = _state.update { it.copy(running = value) }

    var cameraReady: Boolean
        get() = state.value.cameraReady
        set(value) = _state.update { it.copy(cameraReady = value) }

    var pointerEnabled: Boolean
        get() = state.value.pointerEnabled
        set(value) = _state.update { it.copy(pointerEnabled = value) }

    var gesturesEnabled: Boolean
        get() = state.value.gesturesEnabled
        set(value) = _state.update { it.copy(gesturesEnabled = value) }

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

    fun setPointerState(x: Float, y: Float, tracking: Boolean) {
        val safeX = x.coerceIn(0f, 1f)
        val safeY = y.coerceIn(0f, 1f)
        _state.update {
            it.copy(pointer = PointerSnapshot(safeX, safeY, tracking))
        }
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
