package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.ViewConfiguration
import android.view.WindowManager
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
    private val windowManager: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        pointerOverlay = PointerOverlay(this)
        AirRuntime.setForegroundContext(ForegroundContextPolicy.evaluate(null, null))
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
        val display = screenSize()
        overlay.updatePosition(
            pendingX * display.width,
            pendingY * display.height,
            pendingClicking
        )
    }

    fun dispatch(action: AirAction) {
        if (action == AirAction.NONE) return
        if (!isActionAllowed(action)) {
            AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.POLICY_DENIED)
            return
        }
        val dispatchEpoch = AirRuntime.actionEpoch
        // Gesture recognition runs off the main thread. Snapshot the target now;
        // otherwise a queued click can land wherever the cursor moved later.
        val display = screenSize()
        val pointer = AirRuntime.pointerSnapshot()
        val targetX = (pointer.x * display.width).coerceIn(0f, display.width)
        val targetY = (pointer.y * display.height).coerceIn(0f, display.height)
        mainHandler.post {
            // Safety state can change while this action waits for the main thread
            // (for example, after a device-motion or protected-screen event). Check
            // again immediately before injecting instead of trusting the frame-time
            // decision above.
            if (!isActionAllowed(action, dispatchEpoch)) {
                AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.POLICY_DENIED)
                return@post
            }
            when (action) {
                AirAction.NONE -> Unit
                AirAction.TAP -> performClickAt(targetX, targetY)
                AirAction.DOUBLE_TAP -> {
                    performClickAt(targetX, targetY)
                    mainHandler.postDelayed({
                        if (isActionAllowed(action, dispatchEpoch)) {
                            performClickAt(targetX, targetY)
                        } else {
                            AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.POLICY_DENIED)
                        }
                    }, DOUBLE_TAP_GAP_MS)
                }
                AirAction.BACK -> recordGlobalAction(GLOBAL_ACTION_BACK)
                AirAction.HOME -> recordGlobalAction(GLOBAL_ACTION_HOME)
                AirAction.RECENTS -> recordGlobalAction(GLOBAL_ACTION_RECENTS)
                AirAction.LONG_PRESS -> performLongPressAt(targetX, targetY)
                AirAction.SCROLL_UP -> performScroll(up = true, anchorX = targetX, anchorY = targetY)
                AirAction.SCROLL_DOWN -> performScroll(up = false, anchorX = targetX, anchorY = targetY)
            }
        }
    }

    private fun isActionAllowed(action: AirAction, expectedEpoch: Long? = null): Boolean {
        return ActionDispatchGate.evaluate(
            action = action,
            expectedEpoch = expectedEpoch,
            currentEpoch = AirRuntime.actionEpoch,
            gesturesEnabled = AirRuntime.gesturesEnabled,
            controlMode = AirRuntime.controlMode,
            motionActive = AirRuntime.motionActive,
            foregroundSafety = AirRuntime.state.value.foregroundContext.safety
        ).allowed
    }

    private fun performClickAt(x: Float, y: Float) {
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
        dispatchGestureWithOutcome(gesture)
    }

    private fun performLongPressAt(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, LONG_PRESS_DURATION_MS))
            .build()
        dispatchGestureWithOutcome(gesture)
    }

    private fun performScroll(up: Boolean, anchorX: Float, anchorY: Float) {
        val display = screenSize()
        val x = anchorX.coerceIn(0f, display.width)
        val centerY = anchorY.coerceIn(0f, display.height)
        val distance = (display.height * 0.25f).coerceAtLeast(180f)
        val startY = if (up) centerY + distance else centerY - distance
        val endY = if (up) centerY - distance else centerY + distance
        val path = Path().apply {
            moveTo(x, startY.coerceIn(0f, display.height))
            lineTo(x, endY.coerceIn(0f, display.height))
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SCROLL_DURATION_MS))
            .build()
        dispatchGestureWithOutcome(gesture)
    }

    private fun recordGlobalAction(action: Int) {
        val accepted = performGlobalAction(action)
        AirRuntime.recordActionDispatchOutcome(
            if (accepted) ActionDispatchOutcome.COMPLETED
            else ActionDispatchOutcome.PLATFORM_REJECTED
        )
    }

    private fun dispatchGestureWithOutcome(gesture: GestureDescription) {
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.COMPLETED)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.CANCELLED)
                }
            },
            mainHandler
        )
        if (!accepted) {
            AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.PLATFORM_REJECTED)
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): ScreenSize {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return ScreenSize(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    }

    private data class ScreenSize(val width: Float, val height: Float)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        AirRuntime.setForegroundContext(
            ForegroundContextPolicy.evaluate(event.packageName, event.className)
        )
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacksAndMessages(null)
        updateScheduled.set(false)
        AirRuntime.controlMode = ControlMode.READY
        AirRuntime.setForegroundContext(ForegroundContextPolicy.evaluate(null, null))
        mainHandler.post { pointerOverlay?.hide() }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        mainHandler.removeCallbacksAndMessages(null)
        updateScheduled.set(false)
        AirRuntime.controlMode = ControlMode.READY
        AirRuntime.setForegroundContext(ForegroundContextPolicy.evaluate(null, null))
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
