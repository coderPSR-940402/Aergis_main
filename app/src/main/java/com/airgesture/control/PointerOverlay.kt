package com.airgesture.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

class PointerOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: FrameLayout? = null
    private var cursorView: CursorView? = null

    val isVisible: Boolean
        get() = rootView?.isAttachedToWindow == true

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    fun show() {
        if (rootView != null) return
        val root = FrameLayout(context)
        val cursor = CursorView(context)
        root.addView(
            cursor,
            FrameLayout.LayoutParams(CursorView.SIZE, CursorView.SIZE)
        )
        rootView = root
        cursorView = cursor
        windowManager.addView(root, params)
    }

    fun hide() {
        rootView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }
        cursorView = null
        rootView = null
    }

    fun updatePosition(x: Float, y: Float, isClicking: Boolean = false) {
        val cursor = cursorView ?: return
        cursor.setClicking(isClicking)
        cursor.translationX = x - CursorView.SIZE / 2f
        cursor.translationY = y - CursorView.SIZE / 2f
    }

    private class CursorView(context: Context) : View(context) {
        companion object {
            const val SIZE = 50
        }

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
            minimumWidth = SIZE
            minimumHeight = SIZE
        }

        fun setClicking(clicking: Boolean) {
            if (isClicking != clicking) {
                isClicking = clicking
                invalidate()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(SIZE, SIZE)
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
