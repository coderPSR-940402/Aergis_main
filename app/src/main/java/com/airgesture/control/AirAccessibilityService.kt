package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.media.AudioManager
import android.view.KeyEvent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AirAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val boundedInput = BoundedGestureDispatcher(mainHandler,
        dispatch = { gesture, callback -> dispatchGesture(gesture, callback, mainHandler) },
        permitted = { epoch -> AirRuntime.pointerEnabled && isActionAllowed(AirAction.TAP, epoch) },
        record = AirRuntime::recordActionDispatchOutcome)
    private val updateScheduled = AtomicBoolean(false)
    private val pendingVersion = AtomicLong(0L)
    private val pointerCommandGeneration = AtomicLong(0L)
    private data class PointerUpdate(val x: Float, val y: Float, val visible: Boolean,
        val clicking: Boolean, val epoch: Long)
    @Volatile private var pendingPointer = PointerUpdate(0f, 0f, false, false, 0L)
    private data class VisibleHotspot(val point: PointerCoordinateMapper.Point, val epoch: Long)
    @Volatile private var visibleHotspot: VisibleHotspot? = null
    private var pointerOverlay: PointerOverlay? = null
    private var trackingMirror: TrackingMirrorOverlay? = null
    private val windowManager: WindowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        pointerOverlay = PointerOverlay(this)
        trackingMirror = TrackingMirrorOverlay(this)
        AirRuntime.setForegroundContext(ForegroundContextPolicy.evaluate(null, null))
    }

    fun updatePointer(normalizedX: Float, normalizedY: Float, visible: Boolean, isClicking: Boolean = false) {
        pendingPointer = PointerUpdate(normalizedX.coerceIn(0f, 1f), normalizedY.coerceIn(0f, 1f),
            visible, isClicking, AirRuntime.actionEpoch)
        pendingVersion.incrementAndGet()
        schedulePointerUpdate()
    }

    fun cancelPointerTouch() {
        pointerCommandGeneration.incrementAndGet()
        mainHandler.post { boundedInput.cancel() }
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
        val pending = pendingPointer
        if (!pending.visible || pending.epoch != AirRuntime.actionEpoch ||
            !AirRuntime.pointerEnabled || AirRuntime.controlMode != ControlMode.ARMED) {
            boundedInput.cancel()
            visibleHotspot = null
            overlay.hide()
            if (TestingTools.state.value.status == RecordingStatus.RECORDING) {
                TestingTools.event("overlay_applied", org.json.JSONObject().put("visible", false))
            }
            return
        }
        if (!overlay.isVisible) overlay.show()
        val display = screenSize()
        val position = display.map(pending.x, pending.y)
        overlay.updatePosition(
            position.x,
            position.y,
            pending.clicking
        ) { applied ->
            // Attachment can finish after the first update. Publish only once the
            // overlay actually applies the position, including that delayed layout.
            visibleHotspot = applied?.takeIf { pending.epoch == AirRuntime.actionEpoch }
                ?.let { VisibleHotspot(it, pending.epoch) }
        }
        if (TestingTools.state.value.status == RecordingStatus.RECORDING) {
            TestingTools.event("overlay_applied", org.json.JSONObject().put("visible", overlay.isVisible)
                .put("x", pending.x).put("y", pending.y).put("pixelX", position.x).put("pixelY", position.y)
                .put("viewport", overlay.diagnosticBounds()).put("clicking", pending.clicking))
        }
    }

    fun dispatch(action: AirAction, tuning: GestureTuning = GestureTuning()) {
        val settings = tuning.bounded()
        if (action == AirAction.NONE) return
        if (Build.VERSION.SDK_INT < action.minimumSdk) {
            AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.PLATFORM_REJECTED)
            return
        }
        val requestedHotspot = visibleHotspot
        if (TestingTools.state.value.status == RecordingStatus.RECORDING) {
            TestingTools.event("action_requested", org.json.JSONObject().put("action", action.name)
                .put("target", DiagnosticFrameData.pointer(AirRuntime.pointerSnapshot())).put("epoch", AirRuntime.actionEpoch)
                .put("visiblePixelX", requestedHotspot?.point?.x).put("visiblePixelY", requestedHotspot?.point?.y))
        }
        if (!isActionAllowed(action)) {
            AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.POLICY_DENIED)
            return
        }
        val dispatchEpoch = AirRuntime.actionEpoch
        val pointerGeneration = pointerCommandGeneration.get()
        // Gesture recognition runs off the main thread. Snapshot the target now;
        // otherwise a queued click can land wherever the cursor moved later.
        val display = screenSize()
        val pointer = AirRuntime.pointerSnapshot()
        val target = if (action.usesPointerTouch) {
            requestedHotspot?.takeIf { it.epoch == dispatchEpoch }?.point ?: run {
                AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.POLICY_DENIED)
                return
            }
        } else display.map(pointer.x, pointer.y)
        val targetX = target.x
        val targetY = target.y
        mainHandler.post {
            // Safety state can change while this action waits for the main thread
            // (for example, after a disarm or protected-screen event). Check
            // again immediately before injecting instead of trusting the frame-time
            // decision above.
            if (!isActionAllowed(action, dispatchEpoch) ||
                (action.usesPointerTouch && pointerGeneration != pointerCommandGeneration.get())) {
                AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.POLICY_DENIED)
                return@post
            }
            when (action) {
                AirAction.NONE -> Unit
                AirAction.TAP -> performClickAt(targetX, targetY, dispatchEpoch)
                AirAction.DOUBLE_TAP -> performDoubleTapAt(targetX, targetY, dispatchEpoch)
                AirAction.BACK -> recordGlobalAction(GLOBAL_ACTION_BACK)
                AirAction.HOME -> recordGlobalAction(GLOBAL_ACTION_HOME)
                AirAction.RECENTS -> recordGlobalAction(GLOBAL_ACTION_RECENTS)
                AirAction.LONG_PRESS -> performLongPressAt(targetX, targetY, settings.longPressMs, dispatchEpoch)
                AirAction.SCROLL_UP -> performScroll(up = true, anchorX = targetX, anchorY = targetY, percent = settings.scrollPercent, epoch = dispatchEpoch)
                AirAction.SCROLL_DOWN -> performScroll(up = false, anchorX = targetX, anchorY = targetY, percent = settings.scrollPercent, epoch = dispatchEpoch)
                AirAction.NOTIFICATIONS -> recordGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                AirAction.QUICK_SETTINGS -> recordGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                AirAction.DISMISS_SHADE -> if (Build.VERSION.SDK_INT >= 31) recordGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                AirAction.SCREENSHOT -> if (Build.VERSION.SDK_INT >= 28) recordGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                AirAction.LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= 28) recordGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                AirAction.VOLUME_UP -> adjustMediaVolume(AudioManager.ADJUST_RAISE, settings.volumeSteps)
                AirAction.VOLUME_DOWN -> adjustMediaVolume(AudioManager.ADJUST_LOWER, settings.volumeSteps)
                AirAction.MEDIA_PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                AirAction.MEDIA_NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                AirAction.MEDIA_PREVIOUS -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
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
            foregroundSafety = AirRuntime.state.value.foregroundContext.safety
        ).allowed
    }

    private fun performClickAt(x: Float, y: Float, epoch: Long) {
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
        dispatchGestureWithOutcome(gesture, epoch)
    }

    private fun performLongPressAt(x: Float, y: Float, durationMs: Long, epoch: Long) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        dispatchGestureWithOutcome(gesture, epoch)
    }

    private fun performDoubleTapAt(x: Float, y: Float, epoch: Long) {
        val path = Path().apply { moveTo(x, y) }
        val duration = ViewConfiguration.getTapTimeout().toLong().coerceAtLeast(40L)
        dispatchGestureWithOutcome(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .addStroke(GestureDescription.StrokeDescription(path, duration + DOUBLE_TAP_GAP_MS, duration))
            .build(), epoch)
    }

    private fun performScroll(up: Boolean, anchorX: Float, anchorY: Float, percent: Int, epoch: Long) {
        val display = screenSize()
        val x = anchorX.coerceIn(0f, display.maxX)
        val centerY = anchorY.coerceIn(0f, display.maxY)
        val distance = (display.height * percent / 200f).coerceAtLeast(1f)
        val startY = if (up) centerY + distance else centerY - distance
        val endY = if (up) centerY - distance else centerY + distance
        val path = Path().apply {
            moveTo(x, startY.coerceIn(0f, display.maxY))
            lineTo(x, endY.coerceIn(0f, display.maxY))
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SCROLL_DURATION_MS))
            .build()
        dispatchGestureWithOutcome(gesture, epoch)
    }

    private fun adjustMediaVolume(direction: Int, steps: Int) {
        val result = runCatching {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            check(!audio.isVolumeFixed)
            repeat(steps) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI) }
        }
        AirRuntime.recordActionDispatchOutcome(if (result.isSuccess) ActionDispatchOutcome.SUBMITTED
            else ActionDispatchOutcome.PLATFORM_REJECTED)
    }

    private fun sendMediaKey(keyCode: Int) {
        val result = runCatching {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val now = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
        // AudioManager acknowledges submission, not whether a player handled the key.
        AirRuntime.recordActionDispatchOutcome(if (result.isSuccess) ActionDispatchOutcome.SUBMITTED
            else ActionDispatchOutcome.PLATFORM_REJECTED)
    }

    private fun recordGlobalAction(action: Int) {
        val accepted = performGlobalAction(action)
        AirRuntime.recordActionDispatchOutcome(
            if (accepted) ActionDispatchOutcome.COMPLETED
            else ActionDispatchOutcome.PLATFORM_REJECTED
        )
    }

    private fun dispatchGestureWithOutcome(gesture: GestureDescription, epoch: Long) {
        boundedInput.submit(gesture, epoch)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): ScreenSize {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return ScreenSize(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
    }

    private data class ScreenSize(val width: Float, val height: Float) {
        val maxX = (width - 1f).coerceAtLeast(0f)
        val maxY = (height - 1f).coerceAtLeast(0f)

        /** Display dimensions are exclusive; the cursor and injection share the last pixel. */
        fun map(x: Float, y: Float) = PointerCoordinateMapper.Point(
            x.coerceIn(0f, 1f) * maxX, y.coerceIn(0f, 1f) * maxY
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        AirRuntime.setForegroundContext(
            ForegroundContextPolicy.evaluate(event.packageName, event.className)
        )
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        boundedInput.cancel()
        visibleHotspot = null
        AirRuntime.invalidatePendingActions()
        AirRuntime.pointerInteraction = PointerInteractionPhase.IDLE
        trackingMirror?.reposition()
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacksAndMessages(null)
        boundedInput.cancel()
        visibleHotspot = null
        AirRuntime.pointerInteraction = PointerInteractionPhase.IDLE
        TestingTools.setMirror(false)
        updateScheduled.set(false)
        AirRuntime.controlMode = ControlMode.READY
        AirRuntime.setForegroundContext(ForegroundContextPolicy.evaluate(null, null))
        mainHandler.post { pointerOverlay?.hide() }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        boundedInput.cancel()
        visibleHotspot = null
        AirRuntime.pointerInteraction = PointerInteractionPhase.IDLE
        trackingMirror?.close()
        trackingMirror = null
        if (instance === this) instance = null
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
        private const val SCROLL_DURATION_MS = 280L
    }
}
