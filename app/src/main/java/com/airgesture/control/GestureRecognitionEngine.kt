package com.airgesture.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer.GestureRecognizerOptions
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.atomic.AtomicBoolean

/** On-device MediaPipe gesture and hand-landmark inference. */
class GestureRecognitionEngine(private val context: Context) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val recognizer: GestureRecognizer
    private val mappings = ActionMappingStore(context)
    private val interpreter = GestureInterpreter(mappings)
    private var lastActionAt = 0L
    private var lastGestureName = "None"
    private var filteredPointerX = 0f
    private var filteredPointerY = 0f
    private var pointerInitialized = false
    private var lastPointerAt = 0L

    init {
        val options = GestureRecognizerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        recognizer = GestureRecognizer.createFromOptions(context, options)
        AirRuntime.visionReady = true
        AirRuntime.visionError = null
    }

    fun analyze(image: ImageProxy) {
        if (closed.get()) return
        val source = image.toBitmap()
        val bitmap = rotate(source, image.imageInfo.rotationDegrees)
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestamp = SystemClock.uptimeMillis()
            val result = recognizer.recognizeForVideo(mpImage, timestamp)
            publish(result)
        } catch (t: Throwable) {
            AirRuntime.visionError = t.message ?: t.javaClass.simpleName
            AirRuntime.visionReady = false
        } finally {
            if (bitmap !== source) bitmap.recycle()
            source.recycle()
        }
    }

    private fun publish(result: GestureRecognizerResult) {
        val landmarks = result.landmarks()
        AirRuntime.handsDetected = landmarks.size

        val gesture = result.gestures()
            .firstOrNull()
            ?.firstOrNull()
        val gestureName = gesture?.categoryName()?.takeIf { it.isNotBlank() } ?: "None"
        val gestureScore = gesture?.score() ?: 0f
        AirRuntime.lastGesture = gestureName

        val indexTip = landmarks.firstOrNull()?.getOrNull(8)
        val pointerActive = mappings.pointerEnabled()
        AirRuntime.pointerEnabled = pointerActive
        val now = SystemClock.uptimeMillis()
        if (pointerActive && indexTip != null) {
            val rawX = indexTip.x().coerceIn(0f, 1f)
            val rawY = indexTip.y().coerceIn(0f, 1f)
            if (!pointerInitialized) {
                filteredPointerX = rawX
                filteredPointerY = rawY
                pointerInitialized = true
            } else {
                val dx = rawX - filteredPointerX
                val dy = rawY - filteredPointerY
                val distance = kotlin.math.hypot(dx, dy)
                if (distance <= POINTER_DEADBAND) {
                    // Ignore sub-pixel landmark noise while the finger is effectively still.
                } else {
                    val alpha = when {
                        distance <= SLOW_MOVEMENT_THRESHOLD -> STATIONARY_ALPHA
                        distance >= FAST_MOVEMENT_THRESHOLD -> FAST_ALPHA
                        else -> STATIONARY_ALPHA +
                            (FAST_ALPHA - STATIONARY_ALPHA) *
                            ((distance - SLOW_MOVEMENT_THRESHOLD) /
                                (FAST_MOVEMENT_THRESHOLD - SLOW_MOVEMENT_THRESHOLD))
                    }
                    filteredPointerX += dx * alpha
                    filteredPointerY += dy * alpha
                }
            }
            lastPointerAt = now
            AirRuntime.pointerX = filteredPointerX
            AirRuntime.pointerY = filteredPointerY
            AirRuntime.pointerTracking = true
            AirAccessibilityService.instance?.updatePointer(
                filteredPointerX,
                filteredPointerY,
                true
            )
        } else if (pointerActive && pointerInitialized && now - lastPointerAt <= POINTER_LOSS_GRACE_MS) {
            AirRuntime.pointerTracking = true
            AirAccessibilityService.instance?.updatePointer(
                filteredPointerX,
                filteredPointerY,
                true
            )
        } else {
            AirRuntime.pointerTracking = false
            pointerInitialized = false
            AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
        }

        AirRuntime.gesturesEnabled = mappings.gesturesEnabled()
        if (AirRuntime.gesturesEnabled && gestureName != "None" && gestureScore >= MIN_GESTURE_SCORE) {
            if (gestureName != lastGestureName || SystemClock.uptimeMillis() - lastActionAt >= ACTION_COOLDOWN_MS) {
                val decision = interpreter.interpret(GestureSignal(gestureName, gestureScore))
                if (decision.action != AirAction.NONE) {
                    AirAccessibilityService.instance?.dispatch(decision.action)
                    lastActionAt = SystemClock.uptimeMillis()
                }
            }
        }
        lastGestureName = gestureName
        AirRuntime.visionError = null
        AirRuntime.visionReady = true
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            recognizer.close()
            AirRuntime.visionReady = false
            AirRuntime.pointerTracking = false
            AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
        }
    }

    companion object {
        private const val MODEL_ASSET = "gesture_recognizer.task"
        private const val MIN_GESTURE_SCORE = 0.65f
        private const val ACTION_COOLDOWN_MS = 700L
        private const val POINTER_LOSS_GRACE_MS = 250L
        private const val POINTER_DEADBAND = 0.004f
        private const val SLOW_MOVEMENT_THRESHOLD = 0.025f
        private const val FAST_MOVEMENT_THRESHOLD = 0.12f
        private const val STATIONARY_ALPHA = 0.12f
        private const val FAST_ALPHA = 0.65f
    }
}
