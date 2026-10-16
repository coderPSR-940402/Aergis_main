package com.airgesture.control

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Owned Y/U/V rows. Interleaved chroma stays interleaved to keep capture a bulk copy. */
internal class Yuv420Pixels private constructor(
    val width: Int,
    val height: Int,
    private val y: Plane,
    private val u: Plane,
    private val v: Plane
) {
    data class Plane(val buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int)

    /** BT.601 full-range YUV -> RGBA (matching CameraX's JPEG color matrix), with no rotation, mirroring or bitmap allocation. */
    fun writeRgba(target: ByteBuffer) {
        val bytes = Math.multiplyExact(Math.multiplyExact(width, height), 4)
        require(target.capacity() >= bytes)
        target.clear()
        target.limit(bytes)
        val pixels = target.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        // Owned YUV rows use a pooled byte array: no per-pixel direct-buffer JNI reads on ART.
        val yBytes = y.buffer.array(); val yBase = y.buffer.arrayOffset()
        val uBytes = u.buffer.array(); val uBase = u.buffer.arrayOffset()
        val vBytes = v.buffer.array(); val vBase = v.buffer.arrayOffset()
        val rgbaRow = IntArray(width)
        for (row in 0 until height) {
            val yRow = yBase + row * y.rowStride
            val uRow = uBase + (row / 2) * u.rowStride
            val vRow = vBase + (row / 2) * v.rowStride
            var red = 0; var green = 0; var blue = 0
            for (column in 0 until width) {
                if (column % 2 == 0) {
                    val cb = (uBytes[uRow + column / 2 * u.pixelStride].toInt() and 255) - 128
                    val cr = (vBytes[vRow + column / 2 * v.pixelStride].toInt() and 255) - 128
                    red = 359 * cr
                    green = -88 * cb - 183 * cr
                    blue = 454 * cb
                }
                val luminance = 256 * (yBytes[yRow + column].toInt() and 255)
                val r = ((luminance + red + 128) shr 8).coerceIn(0, 255)
                val g = ((luminance + green + 128) shr 8).coerceIn(0, 255)
                val b = ((luminance + blue + 128) shr 8).coerceIn(0, 255)
                rgbaRow[column] = r or (g shl 8) or (b shl 16) or (255 shl 24)
            }
            // One bulk native-buffer write per row instead of one JNI write per pixel.
            pixels.put(rgbaRow)
        }
        target.position(0)
    }

    companion object {
        fun requiredBytes(width: Int, height: Int, planes: List<Plane>): Int {
            require(width > 0 && height > 0 && planes.size == 3)
            require(planes[0].pixelStride == 1) { "YUV_420_888 requires planar luma" }
            var total = 0
            for ((index, plane) in planes.withIndex()) {
                val columns = if (index == 0) width else width / 2 + width % 2
                val rows = if (index == 0) height else height / 2 + height % 2
                require(plane.pixelStride in 1..2) { "Unsupported YUV pixel stride" }
                val rowBytes = Math.addExact(Math.multiplyExact(columns - 1, plane.pixelStride), 1)
                require(plane.rowStride >= rowBytes) { "Incomplete YUV row" }
                require((rows - 1L) * plane.rowStride + rowBytes <= plane.buffer.limit()) { "Incomplete YUV plane" }
                total = Math.addExact(total, Math.multiplyExact(rowBytes, rows))
            }
            return total
        }

        fun copy(width: Int, height: Int, planes: List<Plane>, target: ByteBuffer): Yuv420Pixels {
            val bytes = requiredBytes(width, height, planes)
            require(target.capacity() >= bytes && target.hasArray()) { "Owned YUV pixels require a reusable array-backed buffer" }
            target.clear()
            target.limit(bytes)
            val copied = planes.mapIndexed { index, plane ->
                val columns = if (index == 0) width else width / 2 + width % 2
                val rows = if (index == 0) height else height / 2 + height % 2
                val rowBytes = (columns - 1) * plane.pixelStride + 1
                val start = target.position()
                val source = plane.buffer.duplicate().apply { rewind() }
                for (row in 0 until rows) {
                    source.limit(row * plane.rowStride + rowBytes)
                    source.position(row * plane.rowStride)
                    target.put(source)
                }
                val view = target.duplicate().apply { position(start); limit(start + rowBytes * rows) }.slice()
                Plane(view, rowBytes, plane.pixelStride)
            }
            target.flip()
            return Yuv420Pixels(width, height, copied[0], copied[1], copied[2])
        }
    }
}
