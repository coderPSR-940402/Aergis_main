package com.airgesture.control

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.Executors

class GestureCaptureService : Service(), LifecycleOwner {
    private val executor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var visionEngine: GestureRecognitionEngine? = null
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        AirRuntime.running = true
        AirRuntime.pointerEnabled = ActionMappingStore(this).pointerEnabled()
        runCatching { visionEngine = GestureRecognitionEngine(this) }
            .onFailure {
                AirRuntime.visionReady = false
                AirRuntime.visionError = it.message ?: it.javaClass.simpleName
            }
        setupCamera()
    }

    private fun setupCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            AirRuntime.cameraReady = false
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            runCatching {
                val provider = future.get()
                cameraProvider = provider
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    try {
                        visionEngine?.analyze(image)
                    } finally {
                        image.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                AirRuntime.cameraReady = true
            }.onFailure {
                AirRuntime.cameraReady = false
                AirRuntime.visionError = it.message ?: it.javaClass.simpleName
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        visionEngine?.close()
        visionEngine = null
        executor.shutdownNow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        AirRuntime.cameraReady = false
        AirRuntime.running = false
        AirRuntime.handsDetected = 0
        AirRuntime.lastGesture = "None"
        AirRuntime.pointerTracking = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Aergis", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(): Notification = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("Aergis active")
        .setContentText("Gesture capture and recognition are running")
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setOngoing(true)
        .build()

    companion object {
        const val ACTION_START = "com.airgesture.control.START"
        const val ACTION_STOP = "com.airgesture.control.STOP"
        private const val CHANNEL_ID = "aergis_capture"
        private const val NOTIFICATION_ID = 1001
    }
}
