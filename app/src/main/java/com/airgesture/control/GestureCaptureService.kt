package com.airgesture.control

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Display
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.SessionConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureCaptureService : Service(), LifecycleOwner {
    private var executor: ExecutorService? = null
    private var captureExecutor: ExecutorService? = null
    private var frameProcessor: LatestFrameProcessor<CapturedCameraFrame>? = null
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

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    @ExperimentalGetImage
    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        executor = trackingExecutor("aergis-inference")
        captureExecutor = trackingExecutor("aergis-capture")
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        AirRuntime.running = true
        AirRuntime.controlMode = ControlMode.READY
        val mappings = ActionMappingStore(this)
        AirRuntime.pointerEnabled = mappings.pointerEnabled()
        AirRuntime.gesturesEnabled = mappings.gesturesEnabled()
        AirRuntime.pointerClickingEnabled = mappings.pointerClickingEnabled()
        runCatching { visionEngine = GestureRecognitionEngine(this) }
            .onFailure {
                AirRuntime.visionReady = false
                AirRuntime.visionError = it.message ?: it.javaClass.simpleName
            }
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        setupCamera()
    }

    // Capture must be explicitly started again after Android kills this process.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun trackingExecutor(name: String): ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread({
            // Camera delivery and native work are interactive input, even while the
            // foreground app is busy. Set priority on the worker itself, including
            // any replacement thread created by the executor.
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY) }
                .onFailure { Log.w("GestureCaptureService", "Interactive worker priority unavailable", it) }
            task.run()
        }, name)
    }

    @ExperimentalGetImage
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun setupCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            AirRuntime.cameraReady = false
            return
        }
        val exec = executor ?: return
        val captureExec = captureExecutor ?: return
        val engine = visionEngine ?: return
        val input = CameraFrameInput()
        val processor = LatestFrameProcessor<CapturedCameraFrame>(exec, engine::analyze) {
            Log.e("GestureCaptureService", "Frame processing failed", it)
        }
        frameProcessor = processor
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (stopping) return@addListener
            runCatching {
                val provider = future.get()
                cameraProvider = provider
                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                val cameraInfo = selector.filter(provider.availableCameraInfos).first()
                val info = runCatching { Camera2CameraInfo.from(cameraInfo) }.getOrNull()
                // The legacy 960x540 target negotiated 1088x1088 on the A54.
                // Prefer a small 4:3 stream before spending work on RGBA conversion,
                // inference and diagnostic image copies. Keep device-compatible fallbacks.
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    ))
                    .build()
                val useCase = CameraLatencyPolicy.builder(
                    runCatching { info?.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) }.getOrNull(),
                    runCatching { info?.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) }.getOrNull())
                    .setResolutionSelector(resolutionSelector)
                    .setTargetRotation(displayRotation())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis = useCase
                var resolutionReported = false
                useCase.setAnalyzer(captureExec) { image ->
                    val receivedUptime = android.os.SystemClock.uptimeMillis()
                    val receivedElapsed = android.os.SystemClock.elapsedRealtime()
                    val frame = try {
                        if (!resolutionReported) {
                            Log.i("GestureCaptureService", "Analysis frame: ${image.width}x${image.height}, rotation=${image.imageInfo.rotationDegrees}")
                            resolutionReported = true
                        }
                        input.capture(image, receivedUptime, receivedElapsed)
                    } catch (failure: Exception) {
                        Log.w("GestureCaptureService", "Camera frame copy failed", failure)
                        null
                    } finally {
                        image.close()
                    }
                    // Release CameraX before scheduling native work. New arrivals replace the
                    // waiting snapshot while the GPU/diagnostics worker finishes its active one.
                    if (frame != null) processor.offer(frame)
                }
                val defaultSession = SessionConfig.Builder(useCase).build()
                engine.cameraTimestampsRealtime = runCatching {
                    Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE
                    ) == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
                }.getOrDefault(false)
                // Query this stream combination, not the sensor's unrestricted FPS list.
                // 60 FPS capture is a target; inference throughput is measured separately.
                val frameRate = runCatching {
                    TrackingFrameRate.select(cameraInfo.getSupportedFrameRateRanges(defaultSession))
                }.onFailure {
                    Log.w("GestureCaptureService", "Frame-rate query failed; using compatible camera defaults", it)
                }.getOrNull()
                if (frameRate == null) {
                    provider.bindToLifecycle(this, selector, useCase)
                } else {
                    val session = SessionConfig.Builder(useCase).setFrameRateRange(frameRate).build()
                    try {
                        provider.bindToLifecycle(this, selector, session)
                        Log.i("GestureCaptureService", "Requested analysis FPS: $frameRate")
                    } catch (unsupported: IllegalArgumentException) {
                        // Preserve capture on devices whose advertised combination cannot bind.
                        provider.unbind(useCase)
                        Log.w("GestureCaptureService", "Requested FPS could not bind; using camera defaults", unsupported)
                        provider.bindToLifecycle(this, selector, useCase)
                    }
                }
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
        frameProcessor?.close()
        frameProcessor = null
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
        // A capture already copying can finish; the closed processor recycles its late frame.
        captureExecutor?.shutdown()
        captureExecutor = null
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
