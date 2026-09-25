package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import com.airgesture.control.pointer.ClickManager
import com.airgesture.control.pointer.PointerController

class AirAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pointerOverlay: PointerOverlay? = null
    private var pointerController: PointerController? = null
    private var clickManager: ClickManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val metrics = resources.displayMetrics
        pointerController = PointerController(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels
        )
        clickManager = ClickManager(dwellTimeMs = 400L, maxDwellMovePx = 15.0f)
        pointerOverlay = PointerOverlay(this)
    }

    fun updatePointer(normalizedX: Float, normalizedY: Float, visible: Boolean) {
        mainHandler.post {
            val overlay = pointerOverlay ?: return@post
            if (!visible) {
                overlay.hide()
                return@post
            }

            if (!overlay.isVisible) overlay.show()
            val metrics = resources.displayMetrics
            val x = normalizedX.coerceIn(0f, 1f) * metrics.widthPixels
            val y = normalizedY.coerceIn(0f, 1f) * metrics.heightPixels
            overlay.updatePosition(x, y)
        }
    }

    fun onRawInputReceived(rawX: Float, rawY: Float, timestampMs: Long) {
        mainHandler.post {
            val controller = pointerController ?: return@post
            val overlay = pointerOverlay ?: return@post
            val manager = clickManager ?: return@post

            val state = controller.updateRawInput(rawX, rawY, timestampMs)
            val shouldClick = manager.processPosition(state.x, state.y, timestampMs)
            overlay.show()
            overlay.updatePosition(state.x, state.y, shouldClick)

            if (shouldClick) performClickAt(state.x, state.y)
        }
    }

    fun dispatch(action: AirAction) {
        if (action == AirAction.NONE) return
        mainHandler.post {
            when (action) {
                AirAction.NONE -> Unit
                AirAction.TAP -> performClickAt(currentPointerX(), currentPointerY())
                AirAction.DOUBLE_TAP -> {
                    val x = currentPointerX()
                    val y = currentPointerY()
                    performClickAt(x, y)
                    mainHandler.postDelayed({ performClickAt(x, y) }, DOUBLE_TAP_GAP_MS)
                }
                AirAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                AirAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                AirAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                AirAction.LONG_PRESS -> performLongPressAt(currentPointerX(), currentPointerY())
                AirAction.SCROLL_UP -> performScroll(up = true)
                AirAction.SCROLL_DOWN -> performScroll(up = false)
            }
        }
    }

    fun performClickAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    ViewConfiguration.getTapTimeout().toLong().coerceAtLeast(40L)
                )
            )
            .build()
        dispatchGesture(gesture, null, mainHandler)
    }

    private fun performLongPressAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, LONG_PRESS_DURATION_MS))
            .build()
        dispatchGesture(gesture, null, mainHandler)
    }

    private fun performScroll(up: Boolean) {
        val metrics = resources.displayMetrics
        val x = currentPointerX().coerceIn(0f, metrics.widthPixels.toFloat())
        val centerY = currentPointerY().coerceIn(0f, metrics.heightPixels.toFloat())
        val distance = (metrics.heightPixels * 0.25f).coerceAtLeast(180f)
        val startY = if (up) centerY + distance else centerY - distance
        val endY = if (up) centerY - distance else centerY + distance
        val path = Path().apply {
            moveTo(x, startY.coerceIn(0f, metrics.heightPixels.toFloat()))
            lineTo(x, endY.coerceIn(0f, metrics.heightPixels.toFloat()))
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SCROLL_DURATION_MS))
            .build()
        dispatchGesture(gesture, null, mainHandler)
    }

    private fun currentPointerX(): Float {
        val metrics = resources.displayMetrics
        return AirRuntime.pointerX.coerceIn(0f, 1f) * metrics.widthPixels
    }

    private fun currentPointerY(): Float {
        val metrics = resources.displayMetrics
        return AirRuntime.pointerY.coerceIn(0f, 1f) * metrics.heightPixels
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        mainHandler.post { pointerOverlay?.hide() }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        mainHandler.removeCallbacksAndMessages(null)
        pointerOverlay?.hide()
        pointerOverlay = null
        pointerController?.resetAnchor()
        pointerController = null
        clickManager = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AirAccessibilityService? = null
            private set

        fun enabled(): Boolean = instance != null

        private const val DOUBLE_TAP_GAP_MS = 120L
        private const val LONG_PRESS_DURATION_MS = 650L
        private const val SCROLL_DURATION_MS = 280L
    }
}
