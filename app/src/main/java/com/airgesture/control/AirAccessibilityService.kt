package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.airgesture.control.pointer.ClickManager
import com.airgesture.control.pointer.PointerController

class AirAccessibilityService : AccessibilityService() {

    private var pointerOverlay: PointerOverlay? = null
    private var pointerController: PointerController? = null
    private var clickManager: ClickManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        pointerController = PointerController(screenWidth = width, screenHeight = height)
        clickManager = ClickManager(dwellTimeMs = 400L, maxDwellMovePx = 15.0f)
        pointerOverlay = PointerOverlay(this).apply { show() }
    }

    fun onRawInputReceived(rawX: Float, rawY: Float, timestampMs: Long) {
        val controller = pointerController ?: return
        val overlay = pointerOverlay ?: return
        val manager = clickManager ?: return

        val state = controller.updateRawInput(rawX, rawY, timestampMs)
        val shouldClick = manager.processPosition(state.x, state.y, timestampMs)

        overlay.updatePosition(state.x, state.y, shouldClick)

        if (shouldClick) {
            performClickAt(state.x, state.y)
        }
    }

    fun performClickAt(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gestureBuilder = GestureDescription.Builder().apply {
            addStroke(GestureDescription.StrokeDescription(path, 0, 50))
        }
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Event monitoring hooks
    }

    override fun onInterrupt() {
        pointerOverlay?.hide()
    }

    override fun onDestroy() {
        super.onDestroy()
        pointerOverlay?.hide()
        pointerOverlay = null
    }
}

