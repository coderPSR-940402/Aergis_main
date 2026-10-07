package com.airgesture.control

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/** Decode only a small image, respecting CameraX's RGBA plane padding. */
internal object DiagnosticCameraImage {
    fun decodeRgba(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int,
        pixelStride: Int, rotation: Int, maxEdge: Int): Bitmap {
        require(width > 0 && height > 0 && maxEdge > 0 && pixelStride >= 4)
        require(rotation in listOf(0, 90, 180, 270))
        val data = buffer.duplicate()
        val base = data.position()
        val scale = min(1f, maxEdge.toFloat() / max(width, height))
        val w = max(1, (width * scale).toInt())
        val h = max(1, (height * scale).toInt())
        val outW = if (rotation % 180 == 0) w else h
        val outH = if (rotation % 180 == 0) h else w
        val pixels = IntArray(outW * outH)
        for (y in 0 until h) for (x in 0 until w) {
            val index = base + (y * height / h) * rowStride + (x * width / w) * pixelStride
            require(index + 3 < data.limit()) { "Incomplete RGBA camera plane" }
            val color = Color.argb(data.get(index + 3).toInt() and 255, data.get(index).toInt() and 255,
                data.get(index + 1).toInt() and 255, data.get(index + 2).toInt() and 255)
            val uprightX = when (rotation) { 90 -> h - 1 - y; 180 -> w - 1 - x; 270 -> y; else -> x }
            val uprightY = when (rotation) { 90 -> x; 180 -> h - 1 - y; 270 -> w - 1 - x; else -> y }
            pixels[uprightY * outW + outW - 1 - uprightX] = color
        }
        return Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)
    }
}

internal object MirrorGeometry {
    fun imageRect(width: Int, height: Int, imageWidth: Int, imageHeight: Int): RectF {
        if (width <= 0 || height <= 0 || imageWidth <= 0 || imageHeight <= 0) return RectF()
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val w = imageWidth * scale
        val h = imageHeight * scale
        return RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }
}
