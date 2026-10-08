package com.airgesture.control

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class GestureCaptureService : Service(), LifecycleOwner, SensorEventListener {
    private var executor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var visionEngine: GestureRecognitionEngine? = null
    private var analysis: ImageAnalysis? = null
    private var stopping = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (!stopping && displayId == Display.DEFAULT_DISPLAY) updateCameraRotation()
        }
    }
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val motionMonitor = DeviceMotionCancellation()
    private lateinit var sensorManager: SensorManager
    private var latestGyroMagnitude = 0f
    private var latestAccelerationDeviation = 0f

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    @ExperimentalGetImage
    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        executor = Executors.newSingleThreadExecutor()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        AirRuntime.running = true
        AirRuntime.controlMode = ControlMode.READY
        val mappings = ActionMappingStore(this)
        AirRuntime.pointerEnabled = mappings.pointerEnabled()
        AirRuntime.gesturesEnabled = mappings.gesturesEnabled()
        runCatching { visionEngine = GestureRecognitionEngine(this) }
            .onFailure {
                AirRuntime.visionReady = false
                AirRuntime.visionError = it.message ?: it.javaClass.simpleName
            }
        registerMotionSensors()
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        setupCamera()
    }

    private fun registerMotionSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        AirRuntime.motionSensorsAvailable = gyro != null || accelerometer != null
        if (gyro == null && accelerometer == null) {
            AirRuntime.setMotionState(motionMonitor.reset())
            return
        }
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                latestGyroMagnitude = sqrt(
                    event.values[0] * event.values[0] +
                        event.values[1] * event.values[1] +
                        event.values[2] * event.values[2]
                )
            }
            Sensor.TYPE_ACCELEROMETER -> {
                latestAccelerationDeviation = accelerationDeviation(
                    event.values[0], event.values[1], event.values[2]
                )
            }
            else -> return
        }
        AirRuntime.setMotionState(
            motionMonitor.update(
                latestGyroMagnitude,
                latestAccelerationDeviation,
                event.timestamp / 1_000_000L
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @ExperimentalGetImage
    private fun setupCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            AirRuntime.cameraReady = false
            return
        }
        val exec = executor ?: return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (stopping) return@addListener
            runCatching {
                val provider = future.get()
                cameraProvider = provider
                val useCase = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(Size(960, 540))
                    .setTargetRotation(displayRotation())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis = useCase
                useCase.setAnalyzer(exec) { image ->
                    try {
                        visionEngine?.analyze(image)
                    } finally {
                        image.close()
                    }
                }
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, useCase)
                AirRuntime.cameraReady = true
            }.onFailure {
                AirRuntime.cameraReady = false
                AirRuntime.visionError = it.message ?: it.javaClass.simpleName
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun displayRotation(): Int = getSystemService(DisplayManager::class.java)
        .getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0

    private fun updateCameraRotation() {
        val useCase = analysis ?: return
        val rotation = displayRotation()
        if (useCase.targetRotation != rotation) {
            AirRuntime.invalidatePendingActions()
            AirAccessibilityService.instance?.cancelPointerTouch()
            useCase.targetRotation = rotation
        }
    }

    override fun onDestroy() {
        stopping = true
        val engine = visionEngine
        engine?.stop()
        visionEngine = null
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        TestingTools.stop("Camera session stopped")
        TestingTools.setMirror(false)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        analysis?.let { useCase -> useCase.clearAnalyzer(); cameraProvider?.unbind(useCase) }
        analysis = null
        cameraProvider = null
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        AirRuntime.setMotionState(motionMonitor.reset())
        AirRuntime.motionSensorsAvailable = false
        // GPU creation, inference and native disposal all belong to the same executor.
        // stop() already prevents an old frame from publishing into a restarted session.
        executor?.let { exec ->
            exec.execute { runCatching { engine?.close() } }
            exec.shutdown()
        }
        executor = null
        AirRuntime.cameraReady = false
        AirRuntime.running = false
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.handsDetected = 0
        AirRuntime.lastGesture = "None"
        AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, tracking = false)
        AirRuntime.gesturesEnabled = false
        AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
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
