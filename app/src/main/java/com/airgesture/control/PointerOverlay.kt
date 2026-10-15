package com.airgesture.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
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
        root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        val cursor = CursorView(context)
        root.addView(
            cursor,
            FrameLayout.LayoutParams(cursor.sizePx, cursor.sizePx)
        )
        rootView = root
        cursorView = cursor
        try {
            windowManager.addView(root, params)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to attach pointer overlay", e)
            rootView = null
            cursorView = null
        }
    }

    fun hide() {
        val root = rootView ?: return
        try {
            if (root.isAttachedToWindow) {
                windowManager.removeViewImmediate(root)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to detach pointer overlay", e)
        } finally {
            cursorView = null
            rootView = null
        }
    }

    fun updatePosition(x: Float, y: Float, isClicking: Boolean = false) {
        val cursor = cursorView ?: return
        cursor.setClicking(isClicking)
        cursor.translationX = x - cursor.sizePx / 2f
        cursor.translationY = y - cursor.sizePx / 2f
    }

    /** Opt-in recording evidence for clipping/inset failures; no screen pixels are captured. */
    internal fun diagnosticBounds(): org.json.JSONObject {
        val root = rootView ?: return org.json.JSONObject()
        val origin = IntArray(2)
        root.getLocationOnScreen(origin)
        return org.json.JSONObject().put("width", root.width).put("height", root.height)
            .put("originX", origin[0]).put("originY", origin[1])
    }

    private companion object {
        const val TAG = "PointerOverlay"
    }

    internal class CursorView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        val sizePx = (32f * density).toInt().coerceAtLeast(1)
        private var isClicking = false
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 18, 24)
            style = Paint.Style.STROKE
            strokeWidth = 4f * density
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 228, 235)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 214, 0) }
        private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(172, 126, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }

        fun setClicking(clicking: Boolean) {
            if (isClicking != clicking) {
                isClicking = clicking
                invalidate()
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val radius = (if (isClicking) 8f else 10f) * density
            // Transparent center preserves the target; the center dot remains the exact hotspot.
            canvas.drawCircle(centerX, centerY, radius, outline)
            canvas.drawCircle(centerX, centerY, radius, if (isClicking) accent else ring)
            canvas.drawCircle(centerX, centerY, 3.5f * density, outline)
            canvas.drawCircle(centerX, centerY, 2f * density, core)
        }
    }
}
