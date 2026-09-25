package com.airgesture.control

import android.content.Context

object AirRuntime {
    @Volatile var running: Boolean = false
    @Volatile var cameraReady: Boolean = false
    @Volatile var pointerEnabled: Boolean = true
    @Volatile var gesturesEnabled: Boolean = true
    @Volatile var visionReady: Boolean = false
    @Volatile var visionError: String? = null
    @Volatile var handsDetected: Int = 0
    @Volatile var lastGesture: String = "None"
    @Volatile var handedness: String = "Unknown"
    @Volatile var pointerTracking: Boolean = false
    @Volatile var pointerX: Float = 0f
    @Volatile var pointerY: Float = 0f
    @Volatile private var pointerSnapshot = PointerSnapshot(0f, 0f, false)

    fun setPointerState(x: Float, y: Float, tracking: Boolean) {
        val safeX = x.coerceIn(0f, 1f)
        val safeY = y.coerceIn(0f, 1f)
        pointerSnapshot = PointerSnapshot(safeX, safeY, tracking)
        pointerX = safeX
        pointerY = safeY
        pointerTracking = tracking
    }

    fun pointerSnapshot(): PointerSnapshot = pointerSnapshot

    fun status(context: Context): RuntimeStatus = RuntimeStatus(
        running = running,
        pointerEnabled = ActionMappingStore(context).pointerEnabled(),
        cameraReady = cameraReady,
        message = if (running) "Aergis session active" else "Aergis session stopped"
    )

    data class PointerSnapshot(val x: Float, val y: Float, val tracking: Boolean)
}
