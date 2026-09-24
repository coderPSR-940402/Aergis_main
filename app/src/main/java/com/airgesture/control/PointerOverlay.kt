package com.airgesture.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class PointerOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val view = PointerView(context)
    private val updateScheduled = AtomicBoolean(false)
    @Volatile private var pendingX = 0f
    @Volatile private var pendingY = 0f
    @Volatile private var pendingGeneration = 0L
    private var attached = false

    fun show() {
        mainHandler.post {
            if (attached) return@post
            val params = WindowManager.LayoutParams(
                44,
                44,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            runCatching {
                windowManager.addView(view, params)
                attached = true
            }
        }
    }

    fun update(x: Float, y: Float) {
        pendingX = x.coerceIn(0f, 1f)
        pendingY = y.coerceIn(0f, 1f)
        pendingGeneration += 1L
        if (!updateScheduled.compareAndSet(false, true)) return
        mainHandler.post(::renderLatest)
    }

    private fun renderLatest() {
        if (!attached) {
            updateScheduled.set(false)
            show()
            return
        }
        val generation = pendingGeneration
        val x = pendingX
        val y = pendingY
        val metrics = context.resources.displayMetrics
        val params = view.layoutParams as? WindowManager.LayoutParams
        if (params != null) {
            params.x = (x * metrics.widthPixels).roundToInt() - 22
            params.y = (y * metrics.heightPixels).roundToInt() - 22
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        if (pendingGeneration != generation) {
            mainHandler.post(::renderLatest)
            return
        }
        updateScheduled.set(false)
        if (pendingGeneration != generation && updateScheduled.compareAndSet(false, true)) {
            mainHandler.post(::renderLatest)
        }
    }

    fun hide() {
        mainHandler.post {
            if (!attached) return@post
            runCatching { windowManager.removeView(view) }
            attached = false
        }
    }

    private class PointerView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xffff3b30.toInt()
            style = Paint.Style.FILL
        }

        init {
            background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawCircle(width / 2f, height / 2f, 14f, paint)
        }
    }
}
