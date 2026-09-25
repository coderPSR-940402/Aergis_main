package com.airgesture.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.LandmarkSmoother2D
import com.airgesture.control.filtering.Point3D
import com.airgesture.control.pointer.SwipeDirection
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer.GestureRecognizerOptions
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/** On-device MediaPipe gesture and hand-landmark inference. */
class GestureRecognitionEngine(private val context: Context) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private var recognizer: GestureRecognizer = createRecognizer()
    private var configuredNumHands = desiredNumHands()
    private val mappings = ActionMappingStore(context)
    private val interpreter = GestureInterpreter(mappings)
    private val pointerSmoother = LandmarkSmoother2D()
    private var lastActionAt = 0L
    private var lastGestureName = "None"
    private var pointerInitialized = false
    private var lastPointerAt = 0L
    private var trackedPhysicalHand: String? = null
    private var referencePalmScale = 0f

    init {
        AirRuntime.visionReady = true
        AirRuntime.visionError = null
    }

    fun analyze(image: ImageProxy) {
        if (closed.get()) return
        reconcileRecognizerConfiguration()
        val source = image.toBitmap()
        val bitmap = rotate(source, image.imageInfo.rotationDegrees)
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestamp = SystemClock.uptimeMillis()
            val result = recognizer.recognizeForVideo(mpImage, timestamp)
            publish(result, timestamp)
        } catch (t: Throwable) {
            Log.e(TAG, "Gesture recognition failed for frame", t)
            AirRuntime.visionError = t.message ?: t.javaClass.simpleName
            AirRuntime.visionReady = false
        } finally {
            if (bitmap !== source) bitmap.recycle()
            source.recycle()
        }
    }

    private fun desiredNumHands(): Int =
        if (mappings.pointerEnabled() && !mappings.gesturesEnabled()) 1 else 2

    private fun createRecognizer(): GestureRecognizer {
        val options = GestureRecognizerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(desiredNumHands())
            .setMinHandDetectionConfidence(0.45f)
            .setMinHandPresenceConfidence(0.4f)
            .setMinTrackingConfidence(0.4f)
            .build()
        return GestureRecognizer.createFromOptions(context, options)
    }

    private fun reconcileRecognizerConfiguration() {
        val desired = desiredNumHands()
        if (desired == configuredNumHands || closed.get()) return

        val replacement = runCatching { createRecognizer() }.getOrElse {
            AirRuntime.visionReady = false
            AirRuntime.visionError = it.message ?: it.javaClass.simpleName
            return
        }

        val previous = recognizer
        recognizer = replacement
        configuredNumHands = desired
        previous.close()
        resetTrackingState()
        AirRuntime.visionReady = true
        AirRuntime.visionError = null
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
        val wrist = selectedHand?.getOrNull(WRIST)
        val middleMcp = selectedHand?.getOrNull(MIDDLE_MCP)

        if (pointerActive && indexTip != null && wrist != null && middleMcp != null && selectedHand != null) {
            val pointerPoint = depthCompensatedTip(indexTip, wrist, middleMcp)
            val mapped = mapActiveRegion(pointerPoint.x, pointerPoint.y)
            val smoothed = pointerSmoother.filter(mapped.x, mapped.y, timestamp)
            pointerInitialized = true
            lastPointerAt = timestamp
            AirRuntime.pointerX = smoothed.x.coerceIn(0f, 1f)
            AirRuntime.pointerY = smoothed.y.coerceIn(0f, 1f)
            AirRuntime.pointerTracking = true

            val points = selectedHand.map { landmark ->
                Point3D(landmark.x(), landmark.y(), landmark.z())
            }
            val processed = interpreter.processFrame(points, timestamp)
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

            AirAccessibilityService.instance?.updatePointer(
                AirRuntime.pointerX,
                AirRuntime.pointerY,
                true
            )
        } else if (pointerActive && pointerInitialized && timestamp - lastPointerAt <= POINTER_LOSS_GRACE_MS) {
            AirRuntime.pointerTracking = true
            AirAccessibilityService.instance?.updatePointer(
                AirRuntime.pointerX,
                AirRuntime.pointerY,
                true
            )
        } else {
            AirRuntime.pointerTracking = false
            pointerInitialized = false
            trackedPhysicalHand = null
            referencePalmScale = 0f
            pointerSmoother.reset()
            interpreter.reset()
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

    private fun depthCompensatedTip(
        tip: NormalizedLandmark,
        wrist: NormalizedLandmark,
        middleMcp: NormalizedLandmark
    ): LandmarkPoint {
        val palmScale = distance(wrist, middleMcp).coerceAtLeast(MIN_PALM_SCALE)
        if (referencePalmScale <= 0f) referencePalmScale = palmScale
        val scaleRatio = (referencePalmScale / palmScale).coerceIn(0.65f, 1.55f)
        val x = wrist.x() + (tip.x() - wrist.x()) * scaleRatio
        val y = wrist.y() + (tip.y() - wrist.y()) * scaleRatio
        return LandmarkPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    private fun mapActiveRegion(x: Float, y: Float): LandmarkPoint = LandmarkPoint(
        ((x - ACTIVE_LEFT) / (ACTIVE_RIGHT - ACTIVE_LEFT)).coerceIn(0f, 1f),
        ((y - ACTIVE_TOP) / (ACTIVE_BOTTOM - ACTIVE_TOP)).coerceIn(0f, 1f)
    )

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

    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float =
        hypot(a.x() - b.x(), a.y() - b.y())

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun resetTrackingState() {
        pointerInitialized = false
        lastPointerAt = 0L
        trackedPhysicalHand = null
        referencePalmScale = 0f
        pointerSmoother.reset()
        interpreter.reset()
        AirRuntime.pointerTracking = false
        AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            recognizer.close()
            resetTrackingState()
            AirRuntime.visionReady = false
        }
    }

    private data class LandmarkPoint(val x: Float, val y: Float)

    companion object {
        private const val TAG = "GestureRecognitionEngine"
        private const val MODEL_ASSET = "gesture_recognizer.task"
        private const val MIN_GESTURE_SCORE = 0.65f
        private const val ACTION_COOLDOWN_MS = 700L
        private const val POINTER_LOSS_GRACE_MS = 250L
        private const val ACTIVE_LEFT = 0.08f
        private const val ACTIVE_RIGHT = 0.92f
        private const val ACTIVE_TOP = 0.08f
        private const val ACTIVE_BOTTOM = 0.92f
        private const val MIN_PALM_SCALE = 0.001f
        private const val WRIST = KinematicValidator.WRIST
        private const val INDEX_TIP = KinematicValidator.INDEX_TIP
        private const val MIDDLE_MCP = 9
    }
}
