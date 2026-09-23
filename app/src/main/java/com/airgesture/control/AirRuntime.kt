package com.airgesture.control

import android.content.Context

object AirRuntime {
    @Volatile var running: Boolean = false
    @Volatile var cameraReady: Boolean = false
    @Volatile var pointerEnabled: Boolean = true

    fun status(context: Context): RuntimeStatus = RuntimeStatus(
        running = running,
        pointerEnabled = ActionMappingStore(context).pointerEnabled(),
        cameraReady = cameraReady,
        message = if (running) "Aergis session active" else "Aergis session stopped"
    )
}
