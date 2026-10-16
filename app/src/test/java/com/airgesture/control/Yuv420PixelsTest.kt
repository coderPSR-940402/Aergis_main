package com.airgesture.control

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class Yuv420PixelsTest {
    @Test fun bulkRowsPreserveEveryChannelForOddPaddedAndInterleavedPlanes() {
        for (stride in 1..2) {
            val width = 5; val height = 3
            val yStride = 7; val cStride = 3 * stride + 2
            val y = ByteArray(yStride * height) { (it * 17).toByte() }
            val u = ByteArray(cStride * 2) { (it * 31 + 41).toByte() }
            val v = ByteArray(cStride * 2) { (it * 43 + 29).toByte() }
            val planes = listOf(Yuv420Pixels.Plane(ByteBuffer.wrap(y), yStride, 1),
                Yuv420Pixels.Plane(ByteBuffer.wrap(u), cStride, stride),
                Yuv420Pixels.Plane(ByteBuffer.wrap(v), cStride, stride))
            val owned = ByteBuffer.allocate(Yuv420Pixels.requiredBytes(width, height, planes))
            val pixels = Yuv420Pixels.copy(width, height, planes, owned)
            val target = ByteBuffer.allocateDirect(width * height * 4 + 16)
            repeat(2) {
                pixels.writeRgba(target)
                assertEquals(0, target.position())
                assertEquals(width * height * 4, target.limit())
                for (row in 0 until height) for (column in 0 until width) {
                    val luma = (y[row * yStride + column].toInt() and 255) * 256
                    val cb = (u[row / 2 * cStride + column / 2 * stride].toInt() and 255) - 128
                    val cr = (v[row / 2 * cStride + column / 2 * stride].toInt() and 255) - 128
                    val expected = intArrayOf(((luma + 359 * cr + 128) shr 8).coerceIn(0, 255),
                        ((luma - 88 * cb - 183 * cr + 128) shr 8).coerceIn(0, 255),
                        ((luma + 454 * cb + 128) shr 8).coerceIn(0, 255), 255)
                    expected.forEachIndexed { channel, value ->
                        assertEquals(value, target.get((row * width + column) * 4 + channel).toInt() and 255)
                    }
                }
            }
        }
    }
}
