package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Small touchable window; dragging its header never intercepts the rest of the screen. */
internal class TrackingMirrorOverlay(private val service: AccessibilityService) {
    private val manager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private val scope = MainScope()
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    private val root = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.rgb(18, 26, 36))
        elevation = dp(8).toFloat()
    }
    private val camera = CameraView(service)
    private val params = WindowManager.LayoutParams(dp(200), dp(276),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(80) }
    private var attached = false

    init {
        val header = LinearLayout(service).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(service).apply {
            text = service.getString(R.string.testing_mirror_drag)
            setTextColor(Color.WHITE); textSize = 12f; setPadding(dp(8), 0, 0, 0)
        }
        val close = Button(service).apply {
            text = "×"; contentDescription = service.getString(R.string.testing_mirror_hide)
            minimumWidth = 0; minWidth = 0; minimumHeight = 0; minHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { TestingTools.setMirror(false) }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)
        root.addView(camera, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        var touchX = 0f; var touchY = 0f; var initialX = 0; var initialY = 0
        title.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { touchX = event.rawX; touchY = event.rawY; initialX = params.x; initialY = params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    val display = service.resources.displayMetrics
                    params.x = (initialX + event.rawX - touchX).toInt().coerceIn(0, (display.widthPixels - params.width).coerceAtLeast(0))
                    params.y = (initialY + event.rawY - touchY).toInt().coerceIn(0, (display.heightPixels - params.height).coerceAtLeast(0))
                    if (attached) runCatching { manager.updateViewLayout(root, params) }
                    true
                }
                MotionEvent.ACTION_UP -> { view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        scope.launch {
            TestingTools.state.collect { state ->
                if (state.mirror && !attached) {
                    reposition()
                    runCatching { manager.addView(root, params); attached = true }.onFailure { TestingTools.setMirror(false) }
                } else if (!state.mirror && attached) hide()
            }
        }
        scope.launch {
            TestingTools.mirrorFrame.collect { camera.frame = it; camera.invalidate() }
        }
    }

    private fun hide() {
        if (attached) runCatching { manager.removeView(root) }
        attached = false
        camera.frame = null
    }

    fun reposition() {
        val display = service.resources.displayMetrics
        val rect = MirrorGeometry.windowRect(params.x, params.y, dp(200), dp(276), display.widthPixels, display.heightPixels)
        params.x = rect.left; params.y = rect.top; params.width = rect.width(); params.height = rect.height()
        if (attached) runCatching { manager.updateViewLayout(root, params) }
    }

    fun close() { scope.cancel(); hide(); TestingTools.setMirror(false) }

    private class CameraView(context: android.content.Context) : View(context) {
        var frame: MirrorFrame? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val connections = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8,
            5 to 9, 9 to 10, 10 to 11, 11 to 12, 9 to 13, 13 to 14, 14 to 15, 15 to 16,
            13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val frame = frame
            val footer = 28 * resources.displayMetrics.density
            val imageHeight = (height - footer).toInt().coerceAtLeast(1)
            canvas.drawColor(Color.BLACK)
            if (frame != null) {
                val rect = MirrorGeometry.imageRect(width, imageHeight, frame.bitmap.width, frame.bitmap.height)
                paint.color = Color.WHITE
                canvas.drawBitmap(frame.bitmap, null, rect, paint)
                canvas.save(); canvas.clipRect(rect)
                for (hand in frame.hands) {
                    paint.color = Color.CYAN; paint.strokeWidth = 2 * resources.displayMetrics.density
                    fun valid(index: Int) = hand.getOrNull(index)?.takeIf { it.x.isFinite() && it.y.isFinite() }
                    for ((a, b) in connections) {
                        val start = valid(a) ?: continue; val end = valid(b) ?: continue
                        canvas.drawLine(rect.left + start.x * rect.width(), rect.top + start.y * rect.height(),
                            rect.left + end.x * rect.width(), rect.top + end.y * rect.height(), paint)
                    }
                    hand.forEachIndexed { index, p ->
                        if (p.x.isFinite() && p.y.isFinite()) {
                            paint.color = if (index == 8) Color.YELLOW else Color.CYAN
                            canvas.drawCircle(rect.left + p.x * rect.width(), rect.top + p.y * rect.height(),
                                (if (index == 8) 5 else 2) * resources.displayMetrics.density, paint)
                        }
                    }
                }
                canvas.restore()
            }
            paint.color = Color.WHITE; paint.textSize = 10 * resources.displayMetrics.scaledDensity
            canvas.drawText(frame?.label ?: context.getString(R.string.testing_mirror_waiting), 6f,
                height - 9 * resources.displayMetrics.density, paint)
        }
    }
}
