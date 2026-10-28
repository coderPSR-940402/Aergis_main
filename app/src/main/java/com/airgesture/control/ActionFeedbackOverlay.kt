package com.airgesture.control

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/** Small, non-interactive status surface. No target content or coordinates. */
internal class ActionFeedbackOverlay(private val context: Context) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private val hide = Runnable { close() }
    fun show(feedback: ActionFeedback?) {
        handler.removeCallbacks(hide)
        if (feedback == null || !feedback.visibleAt(SystemClock.uptimeMillis())) { close(); return }
        val text = view ?: TextView(context).apply {
            setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(30, 35, 43))
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad); textSize = 15f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }.also { created ->
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (80 * context.resources.displayMetrics.density).toInt()
            }
            try { manager.addView(created, params); view = created } catch (_: RuntimeException) { return }
        }
        text.text = feedback.message(context)
        handler.postDelayed(hide, (4000L - (SystemClock.uptimeMillis() - feedback.updatedAtMs)).coerceAtLeast(1L))
    }
    fun close() {
        handler.removeCallbacks(hide)
        view?.let { runCatching { manager.removeViewImmediate(it) } }
        view = null
    }
}
