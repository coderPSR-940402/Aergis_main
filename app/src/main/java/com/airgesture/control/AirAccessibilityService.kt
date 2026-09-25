package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AirAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateScheduled = AtomicBoolean(false)
    private val pendingVersion = AtomicLong(0L)
    @Volatile private var pendingX = 0f
    @Volatile private var pendingY = 0f
    @Volatile private var pendingVisible = false
    @Volatile private var pendingClicking = false
    private var pointerOverlay: PointerOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        pointerOverlay = PointerOverlay(this)
    }

    fun updatePointer(normalizedX: Float, normalizedY: Float, visible: Boolean, isClicking: Boolean = false) {
        pendingX = normalizedX.coerceIn(0f, 1f)
        pendingY = normalizedY.coerceIn(0f, 1f)
        pendingVisible = visible
        pendingClicking = isClicking
        pendingVersion.incrementAndGet()
        schedulePointerUpdate()
    }

    private fun schedulePointerUpdate() {
        if (!updateScheduled.compareAndSet(false, true)) return
        mainHandler.post {
            val appliedVersion = pendingVersion.get()
            try {
                applyLatestPointer()
            } finally {
                updateScheduled.set(false)
                if (pendingVersion.get() != appliedVersion) {
                    schedulePointerUpdate()
                }
            }
        }
    }

    private fun applyLatestPointer() {
        val overlay = pointerOverlay ?: return
        if (!pendingVisible) {
            overlay.hide()
            return
        }
        if (!overlay.isVisible) overlay.show()
        val metrics = resources.displayMetrics
        overlay.updatePosition(
            pendingX * metrics.widthPixels,
            pendingY * metrics.heightPixels,
            pendingClicking
        )
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

    private fun currentPointerX(): Float = AirRuntime.pointerSnapshot().x * resources.displayMetrics.widthPixels

    private fun currentPointerY(): Float = AirRuntime.pointerSnapshot().y * resources.displayMetrics.heightPixels

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        mainHandler.post { pointerOverlay?.hide() }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        mainHandler.removeCallbacksAndMessages(null)
        pointerOverlay?.hide()
        pointerOverlay = null
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
