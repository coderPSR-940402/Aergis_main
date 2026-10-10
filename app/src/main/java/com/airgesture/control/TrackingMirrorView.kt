package com.airgesture.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View

/** Camera pixels and landmarks share one aspect-preserving image rectangle. */
internal class TrackingMirrorView(context: Context) : View(context) {
    var frame: MirrorFrame? = null
        set(value) { field = value; invalidate() }
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val connections = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12, 9 to 13, 13 to 14, 14 to 15, 15 to 16,
        13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(12, 19, 28))
        val current = frame
        val now = SystemClock.uptimeMillis()
        val live = current?.isLive(now) == true
        val lineHeight = maxOf(14 * density, sp(10f) * 1.25f)
        val footerHeight = lineHeight * 7 + 8 * density
        val imageHeight = (height - footerHeight).toInt().coerceAtLeast(1)
        current?.bitmap?.let { bitmap ->
            val rect = MirrorGeometry.imageRect(width, imageHeight, bitmap.width, bitmap.height)
            paint.style = Paint.Style.FILL; paint.alpha = if (live) 255 else 110
            canvas.drawBitmap(bitmap, null, rect, paint)
            paint.alpha = 255
            if (live) {
                canvas.save()
                canvas.clipRect(rect)
                drawReach(canvas, rect, current.calibration)
                drawHands(canvas, rect, current)
                canvas.restore()
            }
        }
        val status = when {
            current == null -> context.getString(R.string.testing_mirror_waiting)
            current.bitmap == null -> current.label
            !live -> context.getString(R.string.testing_mirror_stalled, (now - current.timestampMs).coerceAtLeast(0L))
            // Show true camera→pointer latency when known. The old value (draw time minus analyzer
            // start) was a sawtooth of the redraw ticker, not a latency.
            current.pipelineAgeMs >= 0L ->
                context.getString(R.string.testing_mirror_live_pipeline, current.pipelineAgeMs, current.hands.size)
            else -> context.getString(R.string.testing_mirror_live, now - current.timestampMs, current.hands.size)
        }
        var y = imageHeight + lineHeight
        text(canvas, status, y, if (live) Color.GREEN else Color.YELLOW); y += lineHeight
        text(canvas, current?.label ?: "", y); y += lineHeight
        current?.detail.orEmpty().lineSequence().take(4).forEach { line ->
            text(canvas, line, y); y += lineHeight
        }
        text(canvas, context.getString(R.string.testing_mirror_legend), height - 5 * density, Color.LTGRAY)
        // Detect stalled analysis even when StateFlow receives no further camera frames.
        if (isAttachedToWindow && visibility == VISIBLE) postInvalidateDelayed(250)
    }

    private fun drawReach(canvas: Canvas, image: RectF, calibration: PointerCalibrationProfile?) {
        val rect = MirrorGeometry.calibrationRect(image, calibration)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = density; paint.color = Color.WHITE
        canvas.drawRect(rect, paint)
        canvas.drawLine(rect.centerX() - 4 * density, rect.centerY(), rect.centerX() + 4 * density, rect.centerY(), paint)
        canvas.drawLine(rect.centerX(), rect.centerY() - 4 * density, rect.centerX(), rect.centerY() + 4 * density, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawHands(canvas: Canvas, rect: RectF, current: MirrorFrame) {
        for ((handIndex, hand) in current.hands.withIndex()) {
            val selected = handIndex == current.selectedHandIndex
            val color = if (selected) Color.GREEN else Color.CYAN
            fun valid(index: Int) = hand.getOrNull(index)?.takeIf { it.x.isFinite() && it.y.isFinite() }
            paint.color = color; paint.strokeWidth = 1.5f * density
            for ((a, b) in connections) {
                val start = valid(a) ?: continue
                val end = valid(b) ?: continue
                canvas.drawLine(rect.left + start.x * rect.width(), rect.top + start.y * rect.height(),
                    rect.left + end.x * rect.width(), rect.top + end.y * rect.height(), paint)
            }
            if (selected) {
                val thumb = valid(4); val middle = valid(12)
                if (thumb != null && middle != null) {
                    paint.color = Color.MAGENTA
                    canvas.drawLine(rect.left + thumb.x * rect.width(), rect.top + thumb.y * rect.height(),
                        rect.left + middle.x * rect.width(), rect.top + middle.y * rect.height(), paint)
                }
            }
            hand.forEachIndexed { index, point ->
                if (!point.x.isFinite() || !point.y.isFinite()) return@forEachIndexed
                val x = rect.left + point.x * rect.width(); val y = rect.top + point.y * rect.height()
                paint.color = when (index) { 8 -> Color.YELLOW; 4, 12 -> Color.MAGENTA; else -> color }
                canvas.drawCircle(x, y, (if (index in listOf(4, 8, 12)) 3.5f else 2f) * density, paint)
                // Number every selected landmark; key control points stay numbered on other hands.
                if (selected || index in listOf(4, 8, 12)) {
                    textPaint.textSize = sp(9f); textPaint.color = Color.WHITE
                    textPaint.setShadowLayer(2 * density, 0f, 0f, Color.BLACK)
                    canvas.drawText(index.toString(), x + 3 * density, y - 3 * density, textPaint)
                    textPaint.clearShadowLayer()
                }
            }
        }
    }

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    private fun text(canvas: Canvas, value: String, y: Float, color: Int = Color.WHITE) {
        textPaint.color = color; textPaint.textSize = sp(10f)
        val clipped = TextUtils.ellipsize(value, textPaint, (width - 12 * density).coerceAtLeast(0f), TextUtils.TruncateAt.END)
        canvas.drawText(clipped.toString(), 6 * density, y, textPaint)
    }
}
