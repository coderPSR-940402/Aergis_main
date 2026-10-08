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
        // CameraX planes start at zero. Other readers may have advanced their cursor.
        val data = buffer.duplicate().apply { rewind() }
        require(rowStride.toLong() >= width.toLong() * pixelStride) { "Incomplete RGBA row" }
        require((height - 1L) * rowStride + (width - 1L) * pixelStride + 4 <= data.limit()) {
            "Incomplete RGBA camera plane"
        }
        val scale = min(1f, maxEdge.toFloat() / max(width, height))
        val w = max(1, (width * scale).toInt())
        val h = max(1, (height * scale).toInt())
        val outW = if (rotation % 180 == 0) w else h
        val outH = if (rotation % 180 == 0) h else w
        val pixels = IntArray(outW * outH)
        for (y in 0 until h) for (x in 0 until w) {
            val index = (y * height / h) * rowStride + (x * width / w) * pixelStride
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
    fun calibrationRect(image: RectF, profile: PointerCalibrationProfile?): RectF {
        // The default mapper uses a 2% inset; profile.DEFAULT is a different, opt-in mapping.
        val p = profile?.takeIf { it.isValid() }
        val left = p?.left ?: .02f
        val right = p?.right ?: .98f
        val mirrorLeft = if (p?.mirrorX == false) 1f - right else left
        val mirrorRight = if (p?.mirrorX == false) 1f - left else right
        return RectF(image.left + mirrorLeft * image.width(), image.top + (p?.top ?: .02f) * image.height(),
            image.left + mirrorRight * image.width(), image.top + (p?.bottom ?: .98f) * image.height())
    }
    fun windowRect(x: Int, y: Int, width: Int, height: Int, screenWidth: Int, screenHeight: Int): android.graphics.Rect {
        val w = width.coerceIn(1, screenWidth.coerceAtLeast(1))
        val h = height.coerceIn(1, screenHeight.coerceAtLeast(1))
        val left = x.coerceIn(0, (screenWidth - w).coerceAtLeast(0))
        val top = y.coerceIn(0, (screenHeight - h).coerceAtLeast(0))
        return android.graphics.Rect(left, top, left + w, top + h)
    }
    fun imageRect(width: Int, height: Int, imageWidth: Int, imageHeight: Int): RectF {
        if (width <= 0 || height <= 0 || imageWidth <= 0 || imageHeight <= 0) return RectF()
        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val w = imageWidth * scale
        val h = imageHeight * scale
        return RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }
}
