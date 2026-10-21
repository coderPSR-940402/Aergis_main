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

internal interface ScreenTargetHighlight {
    val isVisible: Boolean
    fun show(target: Rect): Boolean
    fun hide()
}

/** Output-only screen-space highlight, independent of the precision pointer. */
internal class TargetHighlightOverlay(context: Context) : ScreenTargetHighlight {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var attached = false
    private var drawn = false
    private var bounds = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 215, 64); style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
    }
    private val view = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val origin = IntArray(2); getLocationOnScreen(origin)
            val visible = Rect(bounds)
            drawn = visible.intersect(Rect(origin[0], origin[1], origin[0]+width, origin[1]+height))
            if (drawn) canvas.drawRect((visible.left-origin[0]).toFloat(), (visible.top-origin[1]).toFloat(),
                (visible.right-origin[0]).toFloat(), (visible.bottom-origin[1]).toFloat(), paint)
        }
    }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    override val isVisible: Boolean get() = drawn && attached && view.isAttachedToWindow && view.isShown
    override fun show(target: Rect): Boolean {
        drawn = false
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        manager.defaultDisplay.getRealMetrics(metrics)
        bounds = Rect(target)
        if (!bounds.intersect(Rect(0,0,metrics.widthPixels,metrics.heightPixels))) { hide(); return false }
        if (!attached) {
            val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= 30) {
                    setFitInsetsTypes(0)
                    layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            try { manager.addView(view, params); attached = true } catch (_: RuntimeException) { return false }
        }
        view.invalidate(); return true
    }
    override fun hide() {
        drawn = false
        if (attached) { runCatching { manager.removeViewImmediate(view) }; attached = false }
    }
}
