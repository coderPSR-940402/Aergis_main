package com.airgesture.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class PointerOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: CursorView? = null

    val isVisible: Boolean
        get() = overlayView?.isAttachedToWindow == true

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
    }

    fun show() {
        if (overlayView == null) {
            overlayView = CursorView(context)
            windowManager.addView(overlayView, params)
        }
    }

    fun hide() {
        overlayView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
            overlayView = null
        }
    }

    fun updatePosition(x: Float, y: Float, isClicking: Boolean = false) {
        params.x = x.toInt() - 25
        params.y = y.toInt() - 25
        overlayView?.setClicking(isClicking)
        overlayView?.let {
            if (it.isAttachedToWindow) {
                windowManager.updateViewLayout(it, params)
            }
        }
    }

    private class CursorView(context: Context) : View(context) {
        private var isClicking = false
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80000000")
            style = Paint.Style.FILL
        }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            style = Paint.Style.FILL
        }
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF1744")
            style = Paint.Style.FILL
        }

        init {
            minimumWidth = 50
            minimumHeight = 50
        }

        fun setClicking(clicking: Boolean) {
            if (isClicking != clicking) {
                isClicking = clicking
                invalidate()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(50, 50)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = 20f
            canvas.drawCircle(centerX, centerY, radius, outerPaint)
            canvas.drawCircle(centerX, centerY, radius * 0.6f, if (isClicking) activePaint else innerPaint)
        }
    }
}
