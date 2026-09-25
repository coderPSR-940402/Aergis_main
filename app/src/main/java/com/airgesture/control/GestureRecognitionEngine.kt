package com.airgesture.control

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import com.airgesture.control.pointer.SwipeDirection
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer.GestureRecognizerOptions
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.atomic.AtomicBoolean

/** On-device MediaPipe gesture and hand-landmark inference. */
class GestureRecognitionEngine(private val context: Context) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val mappings = ActionMappingStore(context)
    private val interpreter = GestureInterpreter(mappings)
    private var recognizer: GestureRecognizer? = null
    private var pointerInitialized = false
    private var lastPointerAt = 0L
    private var trackedPhysicalHand: String? = null
    private var lastActionAt = 0L
    private var lastGestureName = "None"

    init {
        AirRuntime.visionReady = false
        AirRuntime.visionError = null
    }

    fun analyze(image: ImageProxy) {
        if (closed.get()) return
        try {
            ensureRecognizer()
            val currentRecognizer = recognizer ?: return
            val mediaImage = image.image ?: return
            val mpImage = MediaImageBuilder(mediaImage).build()
            val imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(image.imageInfo.rotationDegrees)
                .build()
            val timestamp = SystemClock.uptimeMillis()
            val result = currentRecognizer.recognizeForVideo(
                mpImage,
                imageProcessingOptions,
                timestamp
            )
            publish(result, timestamp)
        } catch (t: Throwable) {
            Log.e(TAG, "Gesture recognition failed for frame", t)
            AirRuntime.visionError = t.message ?: t.javaClass.simpleName
            AirRuntime.visionReady = false
            runCatching { recognizer?.close() }
            recognizer = null
            resetTrackingState()
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null || closed.get()) return
        recognizer = createRecognizerWithGpuFallback()
        AirRuntime.visionReady = true
        AirRuntime.visionError = null
    }

    private fun createRecognizerWithGpuFallback(): GestureRecognizer {
        return runCatching { createRecognizer(Delegate.GPU) }
            .onFailure { Log.w(TAG, "GPU delegate unavailable; falling back to CPU", it) }
            .getOrElse { createRecognizer(Delegate.CPU) }
    }

    private fun createRecognizer(delegate: Delegate): GestureRecognizer {
        val options = GestureRecognizerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(delegate)
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(MAX_HANDS)
            .setMinHandDetectionConfidence(0.45f)
            .setMinHandPresenceConfidence(0.4f)
            .setMinTrackingConfidence(0.4f)
            .build()
        return GestureRecognizer.createFromOptions(context, options)
    }

    private fun publish(result: GestureRecognizerResult, timestamp: Long) {
        val landmarks = result.landmarks()
        AirRuntime.handsDetected = landmarks.size

        val gesture = result.gestures().firstOrNull()?.firstOrNull()
        val gestureName = gesture?.categoryName()?.takeIf { it.isNotBlank() } ?: "None"
        val gestureScore = gesture?.score() ?: 0f
        AirRuntime.lastGesture = gestureName

        val physicalHandedness = result.handedness().map { categories ->
            when (categories.firstOrNull()?.categoryName()) {
                "Left" -> "Right"
                "Right" -> "Left"
                else -> "Unknown"
            }
        }
        AirRuntime.handedness = physicalHandedness.firstOrNull() ?: "Unknown"

        val pointerActive = mappings.pointerEnabled()
        AirRuntime.pointerEnabled = pointerActive
        val selectedIndex = selectPointerHandIndex(landmarks, physicalHandedness, pointerActive)
        val selectedHand = landmarks.getOrNull(selectedIndex)
        val indexTip = selectedHand?.getOrNull(INDEX_TIP)

        if (pointerActive && indexTip != null) {
            val rawMapped = PointerCoordinateMapper.map(indexTip.x(), indexTip.y())
            val points = selectedHand.map { landmark ->
                Point3D(landmark.x(), landmark.y(), landmark.z())
            }
            val processed = interpreter.processFrame(points, timestamp)
            val mapped = processed?.let { PointerCoordinateMapper.map(it.smoothedX, it.smoothedY) } ?: rawMapped
            pointerInitialized = true
            lastPointerAt = timestamp
            AirRuntime.setPointerState(mapped.x, mapped.y, true)

            if (processed != null) {
                if (processed.isClickEngaged) {
                    AirAccessibilityService.instance?.dispatch(AirAction.TAP)
                }
                when (processed.detectedSwipe) {
                    SwipeDirection.UP -> AirAccessibilityService.instance?.dispatch(AirAction.SCROLL_UP)
                    SwipeDirection.DOWN -> AirAccessibilityService.instance?.dispatch(AirAction.SCROLL_DOWN)
                    SwipeDirection.LEFT,
                    SwipeDirection.RIGHT,
                    SwipeDirection.NONE -> Unit
                }
            }

            AirAccessibilityService.instance?.updatePointer(mapped.x, mapped.y, true, processed?.isClickEngaged == true)
        } else {
            pointerInitialized = false
            lastPointerAt = 0L
            trackedPhysicalHand = null
            interpreter.reset()
            AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
            AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
        }

        AirRuntime.gesturesEnabled = mappings.gesturesEnabled()
        if (AirRuntime.gesturesEnabled && gestureName != "None" && gestureScore >= MIN_GESTURE_SCORE) {
            if (gestureName != lastGestureName || timestamp - lastActionAt >= ACTION_COOLDOWN_MS) {
                val decision = interpreter.interpret(GestureSignal(gestureName, gestureScore))
                if (decision.action != AirAction.NONE) {
                    AirAccessibilityService.instance?.dispatch(decision.action)
                    lastActionAt = timestamp
                }
            }
        }
        lastGestureName = gestureName
        AirRuntime.visionError = null
        AirRuntime.visionReady = true
    }

    private fun selectPointerHandIndex(
        landmarks: List<List<NormalizedLandmark>>,
        physicalHandedness: List<String>,
        pointerActive: Boolean
    ): Int {
        if (!pointerActive || landmarks.isEmpty()) return 0
        val tracked = trackedPhysicalHand
        if (tracked != null) {
            val matching = physicalHandedness.indexOfFirst { it == tracked }
            if (matching >= 0 && matching < landmarks.size) return matching
        }
        val firstKnown = physicalHandedness.indexOfFirst { it == "Right" || it == "Left" }
        val selected = if (firstKnown >= 0 && firstKnown < landmarks.size) firstKnown else 0
        trackedPhysicalHand = physicalHandedness.getOrNull(selected)?.takeIf { it != "Unknown" }
        return selected
    }

    private fun resetTrackingState() {
        pointerInitialized = false
        lastPointerAt = 0L
        trackedPhysicalHand = null
        interpreter.reset()
        AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
        AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            recognizer?.close()
            recognizer = null
            resetTrackingState()
            AirRuntime.visionReady = false
        }
    }

    companion object {
        private const val TAG = "GestureRecognitionEngine"
        private const val MODEL_ASSET = "gesture_recognizer.task"
        private const val MIN_GESTURE_SCORE = 0.65f
        private const val ACTION_COOLDOWN_MS = 700L
        private const val MAX_HANDS = 2
        private const val WRIST = KinematicValidator.WRIST
        private const val INDEX_TIP = KinematicValidator.INDEX_TIP
    }
}
