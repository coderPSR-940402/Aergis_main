package com.airgesture.control

import android.graphics.PixelFormat
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.nio.ByteBuffer

/** Pixels and metadata belonging to one camera exposure, independent of ImageProxy lifetime. */
internal data class RgbaFrameData(
    val buffer: ByteBuffer,
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val pixelStride: Int,
    val rotationDegrees: Int,
    val timestampNs: Long,
    val cropRect: String
) {
    companion object {
        fun fromImage(image: ImageProxy): RgbaFrameData {
            val plane = image.planes.single()
            return RgbaFrameData(plane.buffer, image.width, image.height, plane.rowStride,
                plane.pixelStride, image.imageInfo.rotationDegrees, image.imageInfo.timestamp,
                image.cropRect.toShortString())
        }
    }
}

internal class CapturedCameraFrame(
    val data: RgbaFrameData,
    val receivedUptimeMs: Long,
    val receivedElapsedMs: Long,
    private val lease: FrameBufferPool.Lease
) : AutoCloseable {
    // MPImage must close before the lease is recycled; both belong to the inference worker.
    fun createImage(): MPImage = ByteBufferImageBuilder(
        data.buffer.duplicate().apply { rewind() }, data.width, data.height, MPImage.IMAGE_FORMAT_RGBA
    ).build()

    override fun close() = lease.close()
}

/** Capture only copies. Inference and diagnostics never retain CameraX's borrowed image. */
internal class CameraFrameInput {
    private val buffers = FrameBufferPool()

    fun capture(image: ImageProxy, receivedUptimeMs: Long, receivedElapsedMs: Long): CapturedCameraFrame? {
        require(image.format == PixelFormat.RGBA_8888) { "Expected CameraX RGBA output" }
        val data = RgbaFrameData.fromImage(image)
        require(data.width > 0 && data.height > 0)
        require(data.pixelStride == 4) { "Expected four-byte RGBA pixels" }
        val rowBytes = Math.multiplyExact(data.width, 4)
        val size = Math.multiplyExact(rowBytes, data.height)
        require(data.rowStride >= rowBytes) { "Incomplete RGBA row" }
        val source = data.buffer.duplicate().apply { rewind() }
        require((data.height - 1L) * data.rowStride + rowBytes <= source.limit()) { "Incomplete RGBA camera plane" }
        val lease = buffers.acquire(size) ?: return null
        try {
            val target = lease.buffer
            for (row in 0 until data.height) {
                source.limit(row * data.rowStride + rowBytes)
                source.position(row * data.rowStride)
                target.put(source)
            }
            target.flip()
            return CapturedCameraFrame(data.copy(buffer = target, rowStride = rowBytes),
                receivedUptimeMs, receivedElapsedMs, lease)
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
    }
}
