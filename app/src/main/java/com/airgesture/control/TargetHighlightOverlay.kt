package com.airgesture.control

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** Output-only screen-space highlight, independent of the precision pointer. */
internal class TargetHighlightOverlay(context: Context) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var attached = false
    private var bounds = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 215, 64); style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
    }
    private val view = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val origin = IntArray(2); getLocationOnScreen(origin)
            canvas.drawRect((bounds.left - origin[0]).toFloat(), (bounds.top - origin[1]).toFloat(),
                (bounds.right - origin[0]).toFloat(), (bounds.bottom - origin[1]).toFloat(), paint)
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    val isAttached: Boolean get() = attached && view.isAttachedToWindow
    fun show(target: Rect): Boolean {
        bounds = Rect(target)
        if (!attached) {
            val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            }
            try { manager.addView(view, params); attached = true } catch (_: RuntimeException) { return false }
        }
        view.invalidate(); return true
    }
    fun hide() {
        if (attached) { runCatching { manager.removeViewImmediate(view) }; attached = false }
    }
}
