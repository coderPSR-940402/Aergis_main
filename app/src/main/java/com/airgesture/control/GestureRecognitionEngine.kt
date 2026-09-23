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

/**
 * On-device MediaPipe gesture and hand-landmark inference.
 *
 * The model identity is pinned to the exact bundle found in the known-good
 * 0.10.0-preview APK: 8,373,440 bytes, SHA-256
 * 97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482.
 */
class GestureRecognitionEngine(context: Context) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val recognizer: GestureRecognizer

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
        AirRuntime.lastGesture = result.gestures()
            .firstOrNull()
            ?.firstOrNull()
            ?.categoryName()
            ?.takeIf { it.isNotBlank() }
            ?: "None"

        val indexTip = landmarks.firstOrNull()?.getOrNull(8)
        if (indexTip != null) {
            AirRuntime.pointerX = indexTip.x().coerceIn(0f, 1f)
            AirRuntime.pointerY = indexTip.y().coerceIn(0f, 1f)
            AirRuntime.pointerTracking = true
        } else {
            AirRuntime.pointerTracking = false
        }
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
        }
    }

    companion object {
        private const val MODEL_ASSET = "gesture_recognizer.task"
    }
}
