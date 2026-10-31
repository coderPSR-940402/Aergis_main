package com.airgesture.control

import android.graphics.ImageFormat
import android.graphics.PixelFormat
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

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

internal class CapturedCameraFrame private constructor(
    val timestampNs: Long,
    val receivedUptimeMs: Long,
    val receivedElapsedMs: Long,
    val inputEpoch: Long,
    private val preparePixels: () -> RgbaFrameData,
    private val releasePixels: () -> Unit
) : AutoCloseable {
    private var prepared: RgbaFrameData? = null
    private val closed = AtomicBoolean(false)

    constructor(data: RgbaFrameData, receivedUptimeMs: Long, receivedElapsedMs: Long,
        lease: FrameBufferPool.Lease, inputEpoch: Long = AirRuntime.state.value.inputEpoch) :
        this(data.timestampNs, receivedUptimeMs, receivedElapsedMs, inputEpoch,
        { data }, { lease.close() })

    // Only the single inference worker reads pixels; dropped pending frames never convert.
    val data: RgbaFrameData
        get() {
            check(!closed.get()) { "Camera frame already released" }
            return prepared ?: preparePixels().also { prepared = it }
        }

    // MPImage closes before either source or converted pixels return to their pools.
    fun createImage(): MPImage {
        val rgba = data
        return ByteBufferImageBuilder(rgba.buffer.duplicate().apply { rewind() },
            rgba.width, rgba.height, MPImage.IMAGE_FORMAT_RGBA).build()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            prepared = null
            releasePixels()
        }
    }

    companion object {
        fun fromYuv(pixels: Yuv420Pixels, rotation: Int, timestampNs: Long, cropRect: String,
            receivedUptimeMs: Long, receivedElapsedMs: Long, sourceLease: FrameBufferPool.Lease,
            rgbaBuffers: FrameBufferPool, inputEpoch: Long = AirRuntime.state.value.inputEpoch): CapturedCameraFrame {
            var rgbaLease: FrameBufferPool.Lease? = null
            return CapturedCameraFrame(timestampNs, receivedUptimeMs, receivedElapsedMs, inputEpoch, {
                val size = Math.multiplyExact(Math.multiplyExact(pixels.width, pixels.height), 4)
                val lease = checkNotNull(rgbaBuffers.acquire(size)) { "RGBA conversion buffer unavailable" }
                rgbaLease = lease
                try { pixels.writeRgba(lease.buffer) }
                catch (failure: Throwable) {
                    lease.close()
                    rgbaLease = null
                    throw failure
                }
                RgbaFrameData(lease.buffer, pixels.width, pixels.height, pixels.width * 4,
                    4, rotation, timestampNs, cropRect)
            }, {
                rgbaLease?.close()
                sourceLease.close()
            })
        }
    }
}

/** Capture only copies. Inference and diagnostics never retain CameraX's borrowed image. */
internal class CameraFrameInput {
    private val buffers = FrameBufferPool()
    private val rgbaBuffers = FrameBufferPool()
    private val yuvBuffers = FrameBufferPool(direct = false)

    fun capture(image: ImageProxy, receivedUptimeMs: Long, receivedElapsedMs: Long): CapturedCameraFrame? {
        // Bind authority before any plane read/copy, including a concurrent Pause/Resume.
        val inputEpoch = AirRuntime.state.value.inputEpoch
        if (image.format == ImageFormat.YUV_420_888) return captureYuv(image, receivedUptimeMs, receivedElapsedMs, inputEpoch)
        require(image.format == PixelFormat.RGBA_8888) { "Expected CameraX YUV or RGBA output" }
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
            if (data.rowStride == rowBytes) {
                source.limit(size)
                target.put(source)
            } else for (row in 0 until data.height) {
                source.limit(row * data.rowStride + rowBytes)
                source.position(row * data.rowStride)
                target.put(source)
            }
            target.flip()
            return CapturedCameraFrame(data.copy(buffer = target, rowStride = rowBytes),
                receivedUptimeMs, receivedElapsedMs, lease, inputEpoch)
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
    }

    private fun captureYuv(image: ImageProxy, receivedUptimeMs: Long, receivedElapsedMs: Long,
        inputEpoch: Long): CapturedCameraFrame? {
        val width = image.width
        val height = image.height
        val planes = image.planes.map { Yuv420Pixels.Plane(it.buffer, it.rowStride, it.pixelStride) }
        val bytes = Yuv420Pixels.requiredBytes(width, height, planes)
        val lease = yuvBuffers.acquire(bytes) ?: return null
        try {
            val pixels = Yuv420Pixels.copy(width, height, planes, lease.buffer)
            return CapturedCameraFrame.fromYuv(pixels, image.imageInfo.rotationDegrees,
                image.imageInfo.timestamp, image.cropRect.toShortString(), receivedUptimeMs,
                receivedElapsedMs, lease, rgbaBuffers, inputEpoch)
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
    }
}
