package com.airgesture.control

import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageInfo
import android.graphics.Rect
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CameraFrameInputTest {
    @Test fun packedRgbaBulkCopyRetainsPixelsAfterSourceReuse() {
        val pixels = byteArrayOf(1,2,3,4,5,6,7,8)
        val source = ByteBuffer.wrap(pixels)
        val plane = proxy(ImageProxy.PlaneProxy::class.java) { name -> when (name) {
            "getBuffer" -> source; "getRowStride" -> 8; "getPixelStride" -> 4; else -> error(name)
        } }
        val info = proxy(ImageInfo::class.java) { name -> when (name) {
            "getTimestamp" -> 123000000L; "getRotationDegrees" -> 0; else -> error(name)
        } }
        val image = proxy(ImageProxy::class.java) { name -> when (name) {
            "getWidth" -> 2; "getHeight" -> 1; "getFormat" -> android.graphics.PixelFormat.RGBA_8888
            "getPlanes" -> arrayOf(plane); "getImageInfo" -> info; "getCropRect" -> Rect(0,0,2,1)
            else -> error(name)
        } }
        val frame = CameraFrameInput().capture(image, 150L, 200L)!!
        try {
            pixels.fill(0)
            val copy = ByteArray(8).also { frame.data.buffer.duplicate().get(it) }
            assertArrayEquals(byteArrayOf(1,2,3,4,5,6,7,8), copy)
            assertEquals(0, source.position())
        } finally { frame.close() }
    }

    @Test fun ownedSnapshotSurvivesCameraReleaseWithPixelsAndMetadataIntact() {
        // Padding is deliberately not a pixel; last row has no trailing padding.
        val bytes = byteArrayOf(-1,0,0,-1, 0,-1,0,-1, 9,9,9,9, 0,0,-1,-1, -1,-1,-1,-1)
        val buffer = ByteBuffer.wrap(bytes).apply { position(4) }
        var closed = false
        val plane = proxy(ImageProxy.PlaneProxy::class.java) { name ->
            check(!closed) { "Camera image already closed" }
            when (name) { "getBuffer" -> buffer; "getRowStride" -> 12; "getPixelStride" -> 4; else -> error(name) }
        }
        val info = proxy(ImageInfo::class.java) { name ->
            check(!closed)
            when (name) { "getTimestamp" -> 123000000L; "getRotationDegrees" -> 270; else -> error(name) }
        }
        val image = proxy(ImageProxy::class.java) { name ->
            when (name) {
                "getWidth", "getHeight" -> 2
                "getFormat" -> android.graphics.PixelFormat.RGBA_8888
                "getPlanes" -> arrayOf(plane)
                "getImageInfo" -> info
                "getCropRect" -> Rect(0, 0, 2, 2)
                "close" -> { closed = true; null }
                "getImage" -> error("CameraX owns the underlying Android Image; inference must not borrow it")
                else -> error(name)
            }
        }
        val cls = runCatching { Class.forName("com.airgesture.control.CameraFrameInput") }.getOrNull()
        assertNotNull("Inference needs an owned RGBA input, independent of CameraX image lifetime", cls)
        val input = cls!!.getDeclaredConstructor().newInstance()
        val capture = cls.methods.firstOrNull { it.name == "capture" }
        assertNotNull("Capture must detach pixels before the camera image is released", capture)
        val frame = capture!!.invoke(input, image, 150L, 200L)!!
        val data = frame.javaClass.getMethod("getData").invoke(frame)
        assertEquals(123000000L, data.javaClass.getMethod("getTimestampNs").invoke(data))
        assertEquals(270, data.javaClass.getMethod("getRotationDegrees").invoke(data))
        assertEquals("[0,0][2,2]", data.javaClass.getMethod("getCropRect").invoke(data))
        assertEquals(4, buffer.position())
        assertFalse(closed)
        image.close()
        buffer.put(0, 0)
        val mpImage = frame.javaClass.getMethod("createImage").invoke(frame) as MPImage
        try {
            val pixels = ByteBufferExtractor.extract(mpImage)
            val actual = ByteArray(pixels.remaining()).also { pixels.get(it) }
            assertArrayEquals(byteArrayOf(-1,0,0,-1, 0,-1,0,-1, 0,0,-1,-1, -1,-1,-1,-1), actual)
        } finally { mpImage.close(); (frame as AutoCloseable).close() }
        assertTrue(closed)
    }

    private fun <T> proxy(type: Class<T>, value: (String) -> Any?): T = type.cast(
        java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> value(method.name) }
    )
}
