package com.airgesture.control.gaze

import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.airgesture.control.CameraCoordinateTransform
import com.airgesture.control.RgbaFrameData
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/** Disabled unless the visible lab owns a worker. No path to pointer or action dispatch. */
internal object GazeLab {
    data class Observation(val features: DoubleArray?, val uptimeMs: Long, val message: String)
    @Volatile private var worker: Worker? = null
    fun start(context: Context, listener: (Observation) -> Unit) {
        stop(); worker = Worker(context.applicationContext, listener)
    }
    fun stop() { val previous = worker; worker = null; previous?.close() }
    fun offer(frame: RgbaFrameData, timestamp: Long) { worker?.offer(frame, timestamp) }

    private class Worker(val context: Context, val listener: (Observation) -> Unit) {
        val busy = AtomicBoolean(false)
        @Volatile var closed = false
        var lastOffered = -1L
        var model: FaceLandmarker? = null
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); task.run() }, "AergisGazeLab")
        }
        fun offer(frame: RgbaFrameData, timestamp: Long) {
            if (closed || timestamp <= lastOffered || (lastOffered >= 0 && timestamp - lastOffered < 100L) || !busy.compareAndSet(false, true)) return
            lastOffered = timestamp
            // Own a small immutable copy before the hand pipeline releases/reuses its frame.
            val width = minOf(256, frame.width)
            val height = (frame.height * width / frame.width).coerceAtLeast(1)
            val bytes = ByteArray(width * height * 4)
            try {
                for (y in 0 until height) for (x in 0 until width) {
                    val source = (y * frame.height / height) * frame.rowStride + (x * frame.width / width) * frame.pixelStride
                    val target = (y * width + x) * 4
                    for (c in 0..3) bytes[target + c] = frame.buffer.get(source + c)
                }
            } catch (_: RuntimeException) { busy.set(false); return }
            val capturedAt = SystemClock.uptimeMillis()
            try { executor.execute {
                try {
                    if (closed) return@execute
                    val task = model ?: FaceLandmarker.createFromOptions(context,
                        FaceLandmarker.FaceLandmarkerOptions.builder()
                            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                            .setRunningMode(RunningMode.VIDEO).setNumFaces(1)
                            .setMinFaceDetectionConfidence(.65f).setMinFacePresenceConfidence(.65f)
                            .setMinTrackingConfidence(.65f).build()).also { model = it }
                    val image = ByteBufferImageBuilder(ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); rewind() }, width, height, MPImage.IMAGE_FORMAT_RGBA).build()
                    val result = try { task.detectForVideo(image, ImageProcessingOptions.builder()
                        .setRotationDegrees(frame.rotationDegrees).build(), timestamp) } finally { image.close() }
                    val faces = result.faceLandmarks()
                    val points = faces.singleOrNull()?.map {
                        CameraCoordinateTransform.toUpright(it.x(), it.y(), it.z(), frame.rotationDegrees)
                    }
                    val features = if (points != null && points.size >= 478) {
                        fun eye(a: Int, b: Int, iris: Int, top: Int, bottom: Int): Pair<Double, Double>? {
                            val p = points[a]; val q = points[b]; val center = points[iris]
                            val dx = (q.x-p.x).toDouble(); val dy = (q.y-p.y).toDouble()
                            val size = hypot(dx,dy)
                            if (size < .015 || hypot((points[top].x-points[bottom].x).toDouble(),
                                    (points[top].y-points[bottom].y).toDouble()) / size < .08) return null
                            val ex = center.x-p.x; val ey = center.y-p.y
                            return ((ex*dx+ey*dy)/(size*size)) to ((-ex*dy+ey*dx)/(size*size))
                        }
                        val left = eye(33,133,468,159,145); val right = eye(362,263,473,386,374)
                        if (left == null || right == null) null else doubleArrayOf(
                            (left.first+right.first)/2, (left.second+right.second)/2,
                            points[1].x.toDouble(), points[1].y.toDouble()).takeIf { f -> f.all { it.isFinite() } }
                    } else null
                    if (!closed && SystemClock.uptimeMillis()-capturedAt <= 350L)
                        listener(Observation(features, capturedAt, if (features == null) "Face/eyes unavailable" else "Eyes detected"))
                    else if (!closed) listener(Observation(null, capturedAt, "Gaze sample stale"))
                } catch (_: RuntimeException) {
                    if (!closed) listener(Observation(null, capturedAt, "Gaze model unavailable; hand control unaffected"))
                } finally { busy.set(false) }
            } } catch (_: java.util.concurrent.RejectedExecutionException) { busy.set(false) }
        }
        fun close() {
            closed = true
            executor.execute { model?.close(); model = null }
            executor.shutdown()
        }
    }
}
