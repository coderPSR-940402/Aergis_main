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
        if (pointerActive && indexTip != null) {
            AirRuntime.pointerX = indexTip.x().coerceIn(0f, 1f)
            AirRuntime.pointerY = indexTip.y().coerceIn(0f, 1f)
            AirRuntime.pointerTracking = true
            AirAccessibilityService.instance?.updatePointer(
                AirRuntime.pointerX,
                AirRuntime.pointerY,
                true
            )
        } else {
            AirRuntime.pointerTracking = false
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
    }
}
