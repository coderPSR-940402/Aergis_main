package com.airgesture.control

import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeferredCameraConversionTest {
    @Test fun yuvCaptureOwnsPixelsAndConvertsOnlyWhenConsumed() {
        val input = CameraFrameInput()
        val image = camera(2, 2, byteArrayOf(76,76,99, 76,76), 3,
            byteArrayOf(85), byteArrayOf(255.toByte()), 1, 1)
        val frame = input.capture(image.proxy, 100L, 200L)!!
        val capturedEpoch = frame.inputEpoch
        image.proxy.close()
        image.y.fill(0)
        AirRuntime.invalidatePendingActions()
        val rgba = frame.data
        assertEquals("Deferred conversion cannot adopt a newer authority epoch",capturedEpoch,frame.inputEpoch)
        assertTrue(frame.inputEpoch != AirRuntime.state.value.inputEpoch)
        assertEquals(270, rgba.rotationDegrees)
        assertEquals(123000000L, rgba.timestampNs)
        assertEquals("[0,0][2,2]", rgba.cropRect)
        for (i in 0 until 4) assertColor(rgba.buffer, i, 255, 0, 0)
        assertSame("Repeated reads must not reconvert", rgba, frame.data)
        frame.close()
    }

    @Test fun interleavedChromaPaddingAndOddDimensionsRemainCorrect() {
        // 3x3 luma, 2x2 chroma; U and V pixelStride=2 with padding and no last-row tail.
        val image = camera(3, 3, ByteArray(11) { 150.toByte() }, 4,
            byteArrayOf(44,9,44,9,9, 44,9,44),
            byteArrayOf(21,9,21,9,9, 21,9,21), 5, 2)
        val frame = CameraFrameInput().capture(image.proxy, 0L, 0L)!!
        image.proxy.close()
        for (i in 0 until 9) assertColor(frame.data.buffer, i, 0, 255, 1)
        frame.close()
    }

    @Test fun chromaRowsAndColumnsProduceDistinctRedGreenBlueAndWhiteQuadrants() {
        val image = camera(4, 4,
            byteArrayOf(76,76,150.toByte(),150.toByte(), 76,76,150.toByte(),150.toByte(),
                29,29,255.toByte(),255.toByte(), 29,29,255.toByte(),255.toByte()), 4,
            byteArrayOf(85,44,255.toByte(),128.toByte()),
            byteArrayOf(255.toByte(),21,107,128.toByte()), 2, 1)
        val frame = CameraFrameInput().capture(image.proxy, 0L, 0L)!!
        image.proxy.close()
        val colors = listOf(listOf(255,0,0),listOf(0,255,1),listOf(0,0,255),listOf(255,255,255))
        for(row in 0..3) for(column in 0..3) {
            val c = colors[row / 2 * 2 + column / 2]
            assertColor(frame.data.buffer, row * 4 + column, c[0], c[1], c[2])
        }
        frame.close()
    }

    @Test fun neutralLumaKeepsCameraXFullRangeLevels() {
        val image = camera(2, 2, byteArrayOf(16,64,128.toByte(),235.toByte()), 2,
            byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), 1, 1)
        val frame = CameraFrameInput().capture(image.proxy, 0L, 0L)!!
        image.proxy.close()
        for ((i, level) in listOf(16,64,128,235).withIndex()) assertColor(frame.data.buffer, i, level, level, level)
        frame.close()
    }

    @Test fun droppedFramesNeverAllocateRgbaAndAllLeasesReturnToPools() {
        val input = CameraFrameInput()
        val queue = ArrayDeque<Runnable>()
        var consumed = 0
        val processor = LatestFrameProcessor<CapturedCameraFrame>(java.util.concurrent.Executor(queue::addLast), {
            consumed++
            assertColor(it.data.buffer, 0, 0, 0, 0)
        }, { throw it })
        repeat(20) {
            val image = camera(2, 2, ByteArray(4) { 0 }, 2, byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), 1, 1)
            val frame = input.capture(image.proxy, it.toLong(), it.toLong())!!
            image.proxy.close()
            // Converted buffers belong only to consumed frames; a discarded frame must have none.
            val prepared = frame.javaClass.getDeclaredField("prepared").apply { isAccessible = true }
            assertNull(prepared.get(frame))
            processor.offer(frame)
        }
        assertEquals(1, queue.size)
        queue.removeFirst().run()
        assertEquals(1, consumed)
        processor.close()
        repeat(10) {
            val image = camera(2, 2, ByteArray(4) { 0 }, 2, byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), 1, 1)
            val frame = input.capture(image.proxy, 0L, 0L)!!
            image.proxy.close()
            frame.data
            frame.close()
            frame.close()
        }
    }

    @Test fun incompleteCameraPlaneIsRejectedBeforeItCanPoisonTheNextFrame() {
        val input = CameraFrameInput()
        val bad = camera(2, 2, byteArrayOf(0), 2, byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), 1, 1)
        assertThrows(IllegalArgumentException::class.java) { input.capture(bad.proxy, 0L, 0L) }
        bad.proxy.close()
        val good = camera(2, 2, ByteArray(4) { 255.toByte() }, 2, byteArrayOf(128.toByte()), byteArrayOf(128.toByte()), 1, 1)
        val frame = input.capture(good.proxy, 0L, 0L)!!
        good.proxy.close()
        assertColor(frame.data.buffer, 0, 255, 255, 255)
        frame.close()
    }

    private fun assertColor(buffer: ByteBuffer, pixel: Int, r: Int, g: Int, b: Int) {
        for ((channel, expected) in listOf(r,g,b).withIndex()) {
            val actual = buffer.get(pixel * 4 + channel).toInt() and 255
            assertTrue("Channel $channel expected $expected, got $actual", kotlin.math.abs(expected - actual) <= 1)
        }
        assertEquals(255, buffer.get(pixel * 4 + 3).toInt() and 255)
    }

    private data class Camera(val proxy: ImageProxy, val y: ByteArray)
    private fun camera(width: Int, height: Int, y: ByteArray, yRow: Int, u: ByteArray, v: ByteArray,
        uvRow: Int, uvPixel: Int): Camera {
        var closed = false
        fun plane(bytes: ByteArray, row: Int, pixel: Int) = proxy(ImageProxy.PlaneProxy::class.java) { name ->
            check(!closed)
            when(name) { "getBuffer" -> ByteBuffer.wrap(bytes); "getRowStride" -> row; "getPixelStride" -> pixel; else -> error(name) }
        }
        val planes = arrayOf(plane(y,yRow,1),plane(u,uvRow,uvPixel),plane(v,uvRow,uvPixel))
        val info = proxy(ImageInfo::class.java) { name ->
            check(!closed)
            when(name) { "getTimestamp" -> 123000000L; "getRotationDegrees" -> 270; else -> error(name) }
        }
        val image = proxy(ImageProxy::class.java) { name ->
            if(name != "close") check(!closed)
            when(name) { "getWidth" -> width; "getHeight" -> height; "getFormat" -> ImageFormat.YUV_420_888
                "getPlanes" -> planes; "getImageInfo" -> info; "getCropRect" -> Rect(0,0,width,height)
                "close" -> { closed=true; null }; else -> error(name) }
        }
        return Camera(image,y)
    }
    private fun <T> proxy(type: Class<T>, value: (String) -> Any?): T = type.cast(
        java.lang.reflect.Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> value(method.name) })
}
