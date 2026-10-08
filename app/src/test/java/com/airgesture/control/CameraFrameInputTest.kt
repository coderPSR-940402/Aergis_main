package com.airgesture.control

import androidx.camera.core.ImageProxy
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
    @Test fun inferenceOwnsPackedPixelsAndNeverClosesOrConsumesCameraPlane() {
        // Padding is deliberately not a pixel; last row has no trailing padding.
        val bytes = byteArrayOf(-1,0,0,-1, 0,-1,0,-1, 9,9,9,9, 0,0,-1,-1, -1,-1,-1,-1)
        val buffer = ByteBuffer.wrap(bytes).apply { position(4) }
        var closed = false
        val plane = proxy(ImageProxy.PlaneProxy::class.java) { name ->
            check(!closed) { "Camera image already closed" }
            when (name) { "getBuffer" -> buffer; "getRowStride" -> 12; "getPixelStride" -> 4; else -> error(name) }
        }
        val image = proxy(ImageProxy::class.java) { name ->
            when (name) {
                "getWidth", "getHeight" -> 2
                "getFormat" -> android.graphics.PixelFormat.RGBA_8888
                "getPlanes" -> arrayOf(plane)
                "close" -> { closed = true; null }
                "getImage" -> error("CameraX owns the underlying Android Image; inference must not borrow it")
                else -> error(name)
            }
        }
        val cls = runCatching { Class.forName("com.airgesture.control.CameraFrameInput") }.getOrNull()
        assertNotNull("Inference needs an owned RGBA input, independent of CameraX image lifetime", cls)
        val input = cls!!.getDeclaredConstructor().newInstance()
        val mpImage = cls.getMethod("create", ImageProxy::class.java).invoke(input, image) as MPImage
        try {
            val pixels = ByteBufferExtractor.extract(mpImage)
            val actual = ByteArray(pixels.remaining()).also { pixels.get(it) }
            assertArrayEquals(byteArrayOf(-1,0,0,-1, 0,-1,0,-1, 0,0,-1,-1, -1,-1,-1,-1), actual)
            buffer.put(0, 0) // Independent storage, not a borrowed camera-buffer view.
            assertEquals(255, ByteBufferExtractor.extract(mpImage).get(0).toInt() and 255)
        } finally { mpImage.close() }
        assertFalse(closed)
        assertEquals(4, buffer.position())
        assertEquals(20, image.planes[0].buffer.limit())
        image.close()
        assertTrue(closed)
    }

    private fun <T> proxy(type: Class<T>, value: (String) -> Any?): T = type.cast(
        java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> value(method.name) }
    )
}
