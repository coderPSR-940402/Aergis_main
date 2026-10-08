package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
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
    private val camera = TrackingMirrorView(service)
    private var expanded = false
    private val params = WindowManager.LayoutParams(dp(240), dp(360),
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
        val expand = Button(service).apply {
            text = "＋"; contentDescription = service.getString(R.string.testing_mirror_expand)
            minimumWidth = 0; minWidth = 0; minimumHeight = 0; minHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                expanded = !expanded
                text = if (expanded) "−" else "＋"
                contentDescription = service.getString(if (expanded) R.string.testing_mirror_shrink else R.string.testing_mirror_expand)
                reposition()
            }
        }
        val close = Button(service).apply {
            text = "×"; contentDescription = service.getString(R.string.testing_mirror_hide)
            minimumWidth = 0; minWidth = 0; minimumHeight = 0; minHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { TestingTools.setMirror(false) }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(expand, LinearLayout.LayoutParams(dp(44), dp(44)))
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
            TestingTools.mirrorFrame.collect { camera.frame = it }
        }
    }

    private fun hide() {
        if (attached) runCatching { manager.removeView(root) }
        attached = false
        camera.frame = null
    }

    fun reposition() {
        val display = service.resources.displayMetrics
        val rect = MirrorGeometry.windowRect(params.x, params.y, dp(if (expanded) 340 else 240), dp(if (expanded) 500 else 360), display.widthPixels, display.heightPixels)
        params.x = rect.left; params.y = rect.top; params.width = rect.width(); params.height = rect.height()
        if (attached) runCatching { manager.updateViewLayout(root, params) }
    }

    fun close() { scope.cancel(); hide(); TestingTools.setMirror(false) }

}
