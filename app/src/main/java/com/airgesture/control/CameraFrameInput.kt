package com.airgesture.control

import android.graphics.PixelFormat
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.nio.ByteBuffer

/** Analyzer-thread confined input for synchronous VIDEO inference. Close before creating the next. */
internal class CameraFrameInput {
    private var packed: ByteBuffer? = null

    fun create(image: ImageProxy): MPImage {
        require(image.format == PixelFormat.RGBA_8888) { "Expected CameraX RGBA output" }
        val width = image.width
        val height = image.height
        require(width > 0 && height > 0)
        val plane = image.planes.single()
        require(plane.pixelStride == 4) { "Expected four-byte RGBA pixels" }
        val rowBytes = Math.multiplyExact(width, 4)
        val size = Math.multiplyExact(rowBytes, height)
        require(plane.rowStride >= rowBytes) { "Incomplete RGBA row" }
        val source = plane.buffer.duplicate().apply { rewind() }
        require((height - 1L) * plane.rowStride + rowBytes <= source.limit()) { "Incomplete RGBA camera plane" }
        val target = packed?.takeIf { it.capacity() == size }
            ?: ByteBuffer.allocateDirect(size).also { packed = it }
        target.clear()
        for (row in 0 until height) {
            source.limit(row * plane.rowStride + rowBytes)
            source.position(row * plane.rowStride)
            target.put(source)
        }
        target.flip()
        // Never wrap image.image: MPImage.close() would close CameraX's borrowed Android Image.
        // A packed owned buffer also avoids mistaking RGBA output for the underlying YUV image.
        return ByteBufferImageBuilder(target, width, height, MPImage.IMAGE_FORMAT_RGBA).build()
    }
}
