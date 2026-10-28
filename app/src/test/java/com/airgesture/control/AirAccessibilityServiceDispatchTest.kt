package com.airgesture.control

import android.os.Looper
import android.content.Context
import android.graphics.RectF
import android.util.DisplayMetrics
import android.view.WindowManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAccessibilityService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AirAccessibilityServiceDispatchTest {
    private lateinit var controller: org.robolectric.android.controller.ServiceController<AirAccessibilityService>
    private lateinit var service: AirAccessibilityService
    private lateinit var shadow: ShadowAccessibilityService

    private lateinit var legacyTouch: HeldPointerTouch
    private val legacyHandler = android.os.Handler(Looper.getMainLooper())
    private fun updateLegacyPointerTouch(x: Float, y: Float, pressed: Boolean) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val point = PointerCoordinateMapper.Point(Math.round(x * (metrics.widthPixels - 1)).toFloat(),
            Math.round(y * (metrics.heightPixels - 1)).toFloat())
        val epoch = AirRuntime.actionEpoch
        val at = android.os.SystemClock.uptimeMillis()
        legacyHandler.post { legacyTouch.update(point, pressed, epoch, at) }
    }

    @Before
    fun setUp() {
        controller = Robolectric.buildService(AirAccessibilityService::class.java).create()
        service = controller.get()
        AirAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
            .apply { isAccessible = true }.invoke(service)
        shadow = org.robolectric.Shadows.shadowOf(service)
        legacyTouch = HeldPointerTouch(legacyHandler,
            dispatch = { gesture, callback -> service.dispatchGesture(gesture, callback, legacyHandler) },
            permitted = { epoch -> AirRuntime.pointerEnabled && ActionDispatchGate.evaluate(
                AirAction.LONG_PRESS, epoch, AirRuntime.actionEpoch, AirRuntime.gesturesEnabled,
                AirRuntime.controlMode, AirRuntime.state.value.foregroundContext.safety).allowed },
            record = AirRuntime::recordActionDispatchOutcome)
        AirRuntime.resetActionDispatchTelemetry()
        AirRuntime.gesturesEnabled = true
        AirRuntime.pointerEnabled = true
        AirRuntime.pointerClickingEnabled = true
        AirRuntime.controlMode = ControlMode.ARMED
        AirRuntime.setForegroundContext(
            ForegroundContextState(
                packageName = "com.example.safe",
                className = "ExampleActivity",
                safety = ForegroundSafety.SAFE,
                reason = "Foreground context allowed"
            )
        )
        AirRuntime.setPointerState(0.5f, 0.5f, tracking = true)
        service.updatePointer(.5f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @After
    fun tearDown() {
        legacyTouch.cancel()
        controller.destroy()
        AirRuntime.pointerEnabled = true
        AirRuntime.pointerClickingEnabled = true
        AirRuntime.resetActionDispatchTelemetry()
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.setForegroundContext(ForegroundContextState())
        AirRuntime.setPointerState(0f, 0f, tracking = false)
    }

    @Test fun pointerDisabledScrollUsesAvailableNodeWithoutInjectingATouch() {
        @Suppress("DEPRECATION")
        val node = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            isVisibleToUser = true; isEnabled = true
            setBoundsInScreen(android.graphics.Rect(400, 100, 600, 900))
            addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
        }
        var calls = 0
        shadowOf(node).setOnPerformActionListener { _, _ -> calls++; true }
        shadow.setRootInActiveWindow(node)
        AirRuntime.pointerEnabled = false
        val before = shadow.gesturesDispatched.size
        service.dispatch(AirAction.SCROLL_UP)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, calls)
        assertEquals(before, shadow.gesturesDispatched.size)
        assertEquals(ActionDispatchOutcome.SUBMITTED, AirRuntime.state.value.actionDispatchTelemetry.lastOutcome)
        shadow.setRootInActiveWindow(null)
    }

    @Test
    @Suppress("DEPRECATION")
    fun focusedPaneWithoutRequestedActionKeepsBoundedPointerFreeFallback() {
        fun node(bounds: android.graphics.Rect) = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            isVisibleToUser = true; isEnabled = true; setBoundsInScreen(bounds)
        }
        val root = node(android.graphics.Rect(0, 0, 1000, 1000))
        val focused = node(android.graphics.Rect(10, 10, 100, 100)).apply {
            isAccessibilityFocused = true
        }
        val otherPane = node(android.graphics.Rect(500, 0, 1000, 900)).apply {
            addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
            addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP)
        }
        shadowOf(root).addChild(focused); shadowOf(root).addChild(otherPane)
        var otherCalls = 0
        shadowOf(otherPane).setOnPerformActionListener { _, _ -> otherCalls++; true }
        shadow.setRootInActiveWindow(root)
        AirRuntime.pointerEnabled = false
        val metrics = DisplayMetrics()
        (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        try {
            for (action in listOf(AirAction.SCROLL_UP, AirAction.SCROLL_DOWN)) {
                val before = shadow.gesturesDispatched.size
                val completedBefore = AirRuntime.state.value.actionDispatchTelemetry.completed
                service.dispatch(action, GestureTuning(scrollPercent = 30))
                shadowOf(Looper.getMainLooper()).idle()
                assertEquals(0, otherCalls)
                assertEquals(before + 1, shadow.gesturesDispatched.size)
                val call = shadow.gesturesDispatched.last()
                val stroke = call.description().getStroke(0)
                val bounds = RectF()
                stroke.path.computeBounds(bounds, true)
                assertEquals((metrics.widthPixels - 1) * .5f, bounds.left, .01f)
                assertEquals(metrics.heightPixels * .30f, bounds.height(), .01f)
                assertTrue(bounds.top >= 0f && bounds.bottom <= metrics.heightPixels - 1f)
                assertEquals(280L, stroke.duration)
                // Native submission is not completion; the callback is authoritative.
                assertEquals(completedBefore, AirRuntime.state.value.actionDispatchTelemetry.completed)
                call.callback()!!.onCompleted(call.description())
                assertEquals(completedBefore + 1, AirRuntime.state.value.actionDispatchTelemetry.completed)
            }
        } finally {
            shadow.setRootInActiveWindow(null)
        }
    }

    @Test fun mappedPointerToggleWorksBothWaysWithoutTouchAndPersists() {
        AirRuntime.pointerEnabled = false
        val before = AirRuntime.actionEpoch
        service.dispatch(AirAction.TOGGLE_POINTER)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(AirRuntime.pointerEnabled)
        assertTrue(ActionMappingStore(service).pointerEnabled())
        assertTrue(AirRuntime.actionEpoch > before)
        assertEquals(ActionDispatchOutcome.COMPLETED, AirRuntime.state.value.actionDispatchTelemetry.lastOutcome)
        service.dispatch(AirAction.TOGGLE_POINTER)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(AirRuntime.pointerEnabled)
        assertFalse(ActionMappingStore(service).pointerEnabled())
        ActionMappingStore(service).setPointerEnabled(true)
    }

    @Test fun pointerToggleCanExitAnActivePrecisionDrag() {
        service.dispatch(AirAction.POINTER_DRAG)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(service.nativeTouchInFlight)
        service.dispatch(AirAction.TOGGLE_POINTER)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(AirRuntime.pointerEnabled)
        assertFalse(service.precisionDragActive)
        ActionMappingStore(service).setPointerEnabled(true)
    }

    @Test fun queuedPointerToggleCannotExecuteAfterDisarm() {
        AirRuntime.pointerEnabled = false
        service.dispatch(AirAction.TOGGLE_POINTER)
        AirRuntime.controlMode = ControlMode.OFF
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(AirRuntime.pointerEnabled)
        assertEquals(ActionDispatchOutcome.POLICY_DENIED, AirRuntime.state.value.actionDispatchTelemetry.lastOutcome)
    }

    @Test
    @Config(sdk = [26])
    fun unavailableActionsCannotDispatchOnOlderAndroid() {
        service.dispatch(AirAction.SCREENSHOT)
        service.dispatch(AirAction.LOCK_SCREEN)
        service.dispatch(AirAction.DISMISS_SHADE)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadow.globalActionsPerformed.isEmpty())
        assertEquals(3L, AirRuntime.state.value.actionDispatchTelemetry.platformRejected)
    }

    @Test fun newSystemActionsReachTheirCorrespondingAndroidCommands() {
        val pairs = listOf(
            AirAction.NOTIFICATIONS to android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            AirAction.QUICK_SETTINGS to android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
            AirAction.DISMISS_SHADE to android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE,
            AirAction.SCREENSHOT to android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
            AirAction.LOCK_SCREEN to android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        pairs.forEach { (action, _) -> service.dispatch(action) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(pairs.map { it.second }, shadow.globalActionsPerformed)
    }

    @Test fun volumeBindingUsesItsConfiguredStepsAndRemainsSafetyGated() {
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 4, 0)
        service.dispatch(AirAction.VOLUME_UP, GestureTuning(volumeSteps = 3))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(7, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        service.dispatch(AirAction.VOLUME_DOWN, GestureTuning(volumeSteps = 2))
        AirRuntime.controlMode = ControlMode.PAUSED
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(7, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }

    @Test fun configuredLongPressDurationReachesNativeStroke() {
        service.dispatch(AirAction.LONG_PRESS, GestureTuning(longPressMs = 1200))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1200L, shadow.gesturesDispatched.single().description().getStroke(0).duration)
    }

    @Test fun mediaCommandsSubmitMatchedDownAndUpKeys() {
        for (action in listOf(AirAction.MEDIA_PLAY_PAUSE, AirAction.MEDIA_NEXT, AirAction.MEDIA_PREVIOUS)) service.dispatch(action)
        shadowOf(Looper.getMainLooper()).idle()
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val events = shadowOf(audio).dispatchedMediaKeyEvents
        assertEquals(listOf(85, 85, 87, 87, 88, 88), events.map { it.keyCode })
        assertEquals(listOf(0, 1, 0, 1, 0, 1), events.map { it.action })
        assertEquals(3L, AirRuntime.state.value.actionDispatchTelemetry.submitted)
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)
    }

    @Test
    fun acceptedGestureIsRecordedOnlyWhenPlatformCallbackCompletes() {
        service.dispatch(AirAction.TAP)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, shadow.gesturesDispatched.size)
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)

        val dispatched = shadow.gesturesDispatched.single()
        dispatched.callback()!!.onCompleted(dispatched.description())

        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
        assertEquals(ActionDispatchOutcome.COMPLETED, AirRuntime.state.value.actionDispatchTelemetry.lastOutcome)
    }

    @Test
    fun platformImmediateRejectionIsRecordedAndNotReportedAsCompletion() {
        shadow.setCanDispatchGestures(false)

        service.dispatch(AirAction.TAP)
        shadowOf(Looper.getMainLooper()).idle()

        val telemetry = AirRuntime.state.value.actionDispatchTelemetry
        assertTrue(shadow.gesturesDispatched.isEmpty())
        assertEquals(1L, telemetry.platformRejected)
        assertEquals(0L, telemetry.completed)
        assertEquals(ActionDispatchOutcome.PLATFORM_REJECTED, telemetry.lastOutcome)
    }

    @Test
    fun callbackCancellationIsRecordedSeparatelyFromImmediateRejection() {
        service.dispatch(AirAction.SCROLL_DOWN)
        shadowOf(Looper.getMainLooper()).idle()

        val dispatched = shadow.gesturesDispatched.single()
        dispatched.callback()!!.onCancelled(dispatched.description())

        val telemetry = AirRuntime.state.value.actionDispatchTelemetry
        assertEquals(0L, telemetry.platformRejected)
        assertEquals(1L, telemetry.cancelled)
        assertEquals(0L, telemetry.completed)
        assertEquals(ActionDispatchOutcome.CANCELLED, telemetry.lastOutcome)
    }

    @Test fun explicitSourceEpochCannotBeReplacedByCurrentPointerAuthority() {
        val sourceEpoch = AirRuntime.actionEpoch
        AirRuntime.invalidatePendingActions()
        service.updatePointer(.5f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        service.dispatchPointerTap(sourceEpoch)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadow.gesturesDispatched.isEmpty())
        assertEquals(ActionDispatchOutcome.POLICY_DENIED, AirRuntime.state.value.actionDispatchTelemetry.lastOutcome)
    }

    @Test fun calibrationOpensWithoutPointerAndRevokesActionAuthority() {
        AirRuntime.pointerEnabled = false
        AirRuntime.setPointerState(.5f, .5f, false)
        val epoch = AirRuntime.actionEpoch
        service.dispatch(AirAction.CALIBRATE_POINTER)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ControlMode.READY, AirRuntime.controlMode)
        assertTrue(AirRuntime.actionEpoch > epoch)
        val launched = shadowOf(service).nextStartedActivity
        assertEquals(MainActivity.ACTION_OPEN_CALIBRATION, launched?.action)
        assertEquals(MainActivity::class.java.name, launched?.component?.className)
        assertTrue(shadow.gesturesDispatched.isEmpty())
        assertEquals(ActionDispatchOutcome.SUBMITTED, AirRuntime.state.value.actionDispatchTelemetry.lastOutcome)
    }

    @Test fun pointerTapWorksWhenCommandsOffButToggleInvalidatesQueuedTap() {
        AirRuntime.gesturesEnabled = false
        service.updatePointer(.5f,.5f,true)
        shadowOf(Looper.getMainLooper()).idle()
        service.dispatchPointerTap()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, shadow.gesturesDispatched.size)
        val tap=shadow.gesturesDispatched.single()
        tap.callback()!!.onCompleted(tap.description())
        service.dispatchPointerTap()
        AirRuntime.pointerClickingEnabled = false
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Queued pointer click cannot survive switch-off",1,shadow.gesturesDispatched.size)
    }

    @Test fun screenSelectionCannotCommitDuringNativeGesture() {
        service.dispatch(AirAction.SCROLL_DOWN)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,shadow.gesturesDispatched.size)
        AirRuntime.resetActionDispatchTelemetry()
        service.dispatch(AirAction.SELECT_ITEM)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Selection must be rejected before node execution while input is busy",1L,
            AirRuntime.state.value.actionDispatchTelemetry.platformRejected)
        assertEquals(1,shadow.gesturesDispatched.size)
    }

    @Test fun gestureNavigationWorksWithPointerAndClickingOff() {
        AirRuntime.pointerEnabled = false
        AirRuntime.pointerClickingEnabled = false
        service.dispatch(AirAction.BACK)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),shadow.globalActionsPerformed)
        assertTrue(shadow.gesturesDispatched.isEmpty())
    }

    @Test
    fun policyDenialProducesNoPlatformGesture() {
        AirRuntime.gesturesEnabled = false

        service.dispatch(AirAction.TAP)
        shadowOf(Looper.getMainLooper()).idle()

        val telemetry = AirRuntime.state.value.actionDispatchTelemetry
        assertTrue(shadow.gesturesDispatched.isEmpty())
        assertEquals(1L, telemetry.policyDenied)
        assertEquals(0L, telemetry.platformRejected)
    }

    @Test
    fun epochChangeDeniesQueuedActionBeforePlatformDispatch() {
        service.dispatch(AirAction.TAP)
        AirRuntime.controlMode = ControlMode.PAUSED
        shadowOf(Looper.getMainLooper()).idle()

        val telemetry = AirRuntime.state.value.actionDispatchTelemetry
        assertTrue(shadow.gesturesDispatched.isEmpty())
        assertEquals(1L, telemetry.policyDenied)
    }

    @Test
    @Suppress("DEPRECATION")
    fun bottomRightTapUsesLastPixelAndKeepsItsRequestedTarget() {
        val metrics = DisplayMetrics()
        (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        AirRuntime.setPointerState(1f, 1f, tracking = true)
        service.updatePointer(1f, 1f, true)
        shadowOf(Looper.getMainLooper()).idle()

        service.dispatch(AirAction.TAP)
        AirRuntime.setPointerState(0.2f, 0.2f, tracking = true)
        shadowOf(Looper.getMainLooper()).idle()

        val stroke = shadow.gesturesDispatched.single().description().getStroke(0)
        val bounds = RectF()
        stroke.path.computeBounds(bounds, true)
        assertEquals((metrics.widthPixels - 1).toFloat(), bounds.left, 0.01f)
        assertEquals((metrics.heightPixels - 1).toFloat(), bounds.top, 0.01f)
    }

    @Test
    @Suppress("DEPRECATION")
    fun edgeScrollStaysInsideDisplayAtBothEndpoints() {
        val metrics = DisplayMetrics()
        (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        AirRuntime.setPointerState(1f, 1f, tracking = true)
        service.updatePointer(1f, 1f, true)
        shadowOf(Looper.getMainLooper()).idle()
        service.dispatch(AirAction.SCROLL_UP)
        shadowOf(Looper.getMainLooper()).idle()

        // Legacy Robolectric PathMeasure.getPosTan synthesizes 0..1 values.
        // Bounds inspect the actual dispatched path, including both scroll endpoints.
        val bounds = RectF()
        shadow.gesturesDispatched.single().description().getStroke(0).path.computeBounds(bounds, true)
        assertEquals((metrics.widthPixels - 1) * .5f, bounds.left, 0.01f)
        assertEquals(bounds.left, bounds.right, 0.01f)
        assertTrue(bounds.top in 0f..(metrics.heightPixels - 1).toFloat())
        assertTrue(bounds.bottom in 0f..(metrics.heightPixels - 1).toFloat())
        assertTrue(bounds.bottom > bounds.top)
    }

    @Test fun allFourNavigationStrokesWorkWithPointerDisabledAndRemainCancellable() {
        AirRuntime.pointerEnabled = false
        service.updatePointer(0f,0f,false)
        shadowOf(Looper.getMainLooper()).idle()
        for (action in listOf(AirAction.SCROLL_UP, AirAction.SCROLL_DOWN, AirAction.SWIPE_LEFT, AirAction.SWIPE_RIGHT)) {
            val before = shadow.gesturesDispatched.size
            service.dispatch(action)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before + 1, shadow.gesturesDispatched.size)
            service.cancelPointerTouch() // Hiding a disabled pointer must not cancel navigation.
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(before + 1, shadow.gesturesDispatched.size)
            val call = shadow.gesturesDispatched.last()
            call.callback()!!.onCompleted(call.description())
        }
    }

    @Test fun explicitPrecisionDragDoesNotAlsoTapAndReleaseIsBounded() {
        service.dispatch(AirAction.POINTER_DRAG)
        shadowOf(Looper.getMainLooper()).idle()
        val down = shadow.gesturesDispatched.single()
        assertTrue(service.nativeTouchInFlight)
        assertTrue(down.description().getStroke(0).willContinue())
        down.callback()!!.onCompleted(down.description())
        service.dispatch(AirAction.TAP)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, shadow.gesturesDispatched.size)
        service.dispatch(AirAction.POINTER_DRAG)
        shadowOf(Looper.getMainLooper()).idle()
        val up = shadow.gesturesDispatched.last()
        assertEquals(2, shadow.gesturesDispatched.size)
        assertFalse(up.description().getStroke(0).willContinue())
        up.callback()!!.onCompleted(up.description())
        assertFalse(service.precisionDragActive)
        assertFalse(service.nativeTouchInFlight)
    }

    @Test fun trackingCancellationInvalidatesQueuedTouchBeforeNativeDispatch() {
        service.dispatch(AirAction.TAP)
        service.cancelPointerTouch()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadow.gesturesDispatched.isEmpty())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.policyDenied)
    }

    @Test fun clippingAfterQueueingInvalidatesTouchBeforeNativeDispatch() {
        val overlay = AirAccessibilityService::class.java.getDeclaredField("pointerOverlay")
            .apply { isAccessible = true }.get(service) as PointerOverlay
        val root = PointerOverlay::class.java.getDeclaredField("rootView")
            .apply { isAccessible = true }.get(overlay) as android.widget.FrameLayout
        assertTrue(overlay.isHotspotVisible)
        service.dispatch(AirAction.TAP)
        // A relayout can clip the retained target before the queued command executes.
        root.layout(0, 0, 1, 1)
        assertFalse(overlay.isHotspotVisible)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadow.gesturesDispatched.isEmpty())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.policyDenied)
    }

    @Test fun doubleTapIsOneBoundedNativeRequestAtTheDisplayedHotspot() {
        AirRuntime.setPointerState(.9f, .9f, tracking = true)
        // The runtime estimate moved; the displayed .5/.5 hotspot has not.
        service.dispatch(AirAction.DOUBLE_TAP)
        shadowOf(Looper.getMainLooper()).idle()
        val gesture = shadow.gesturesDispatched.single().description()
        assertEquals(2, gesture.strokeCount)
        assertTrue(gesture.getStroke(1).startTime > gesture.getStroke(0).duration)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        for (i in 0..1) {
            val stroke = gesture.getStroke(i)
            assertFalse(stroke.willContinue())
            val bounds = RectF()
            stroke.path.computeBounds(bounds, true)
            assertEquals(.5f * (metrics.widthPixels - 1), bounds.left, .01f)
            assertEquals(.5f * (metrics.heightPixels - 1), bounds.top, .01f)
        }
    }

    @Test fun hiddenHotspotCannotInjectCoordinateCommands() {
        service.updatePointer(.5f, .5f, false)
        shadowOf(Looper.getMainLooper()).idle()
        service.dispatch(AirAction.TAP)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadow.gesturesDispatched.isEmpty())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.policyDenied)
    }

    @Test fun pauseCancelsBoundedLongPressThroughPlatformAndDoesNotReplay() {
        service.dispatch(AirAction.LONG_PRESS)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        AirRuntime.controlMode = ControlMode.PAUSED
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16))
        assertEquals(2, shadow.gesturesDispatched.size)
        val cancellation = shadow.gesturesDispatched.last()
        cancellation.callback()!!.onCancelled(cancellation.description())
        first.callback()!!.onCompleted(first.description())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.cancelled)
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)
        AirRuntime.controlMode = ControlMode.ARMED
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertEquals(2, shadow.gesturesDispatched.size)
    }

    @Test fun disablingPointerRejectsAnInFlightFrameEvenWithTheNewEpoch() {
        AirRuntime.pointerEnabled = false
        updateLegacyPointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("A late frame must not start a native touch after pointer disable", shadow.gesturesDispatched.isEmpty())
    }

    @Test fun pointerDisableCancelsHeldTouchAndLateCallbackCannotContinue() {
        updateLegacyPointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        AirRuntime.pointerEnabled = false
        first.callback()!!.onCompleted(first.description())
        assertEquals(2, shadow.gesturesDispatched.size)
        first.callback()!!.onCompleted(first.description())
        assertEquals(2, shadow.gesturesDispatched.size)
    }

    @Test fun pinchPressKeepsOneTouchDownAndReleaseEndsItWithoutAnExtraTap() {
        updateLegacyPointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        assertTrue(first.description().getStroke(0).willContinue())
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)

        updateLegacyPointerTouch(0.4f, 0.5f, false)
        shadowOf(Looper.getMainLooper()).idle()
        first.callback()!!.onCompleted(first.description())
        val ending = shadow.gesturesDispatched.last()
        assertEquals(2, shadow.gesturesDispatched.size)
        assertFalse(ending.description().getStroke(0).willContinue())
        ending.callback()!!.onCompleted(ending.description())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
    }

    @Test fun newPinchCannotEraseReleaseWhilePlatformCallbackIsPending() {
        updateLegacyPointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        updateLegacyPointerTouch(.4f, .5f, false)
        updateLegacyPointerTouch(.7f, .8f, true)
        shadowOf(Looper.getMainLooper()).idle()
        first.callback()!!.onCompleted(first.description())
        val ending = shadow.gesturesDispatched.last()
        assertFalse("A new press cannot swallow finger-up", ending.description().getStroke(0).willContinue())
        ending.callback()!!.onCompleted(ending.description())
        assertEquals(3, shadow.gesturesDispatched.size)
        assertTrue(shadow.gesturesDispatched.last().description().getStroke(0).willContinue())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
        first.callback()!!.onCompleted(first.description())
        ending.callback()!!.onCompleted(ending.description())
        assertEquals("Old callbacks cannot extend the next press", 3, shadow.gesturesDispatched.size)
    }

    @Test fun queuedDragMovementReachesItsOwnReleaseTargetBeforeTheNextPress() {
        updateLegacyPointerTouch(.2f, .2f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val down = shadow.gesturesDispatched.single()
        updateLegacyPointerTouch(.4f, .6f, true)
        // Aiming after finger-up is not part of the drag; a next pinch has its own target.
        updateLegacyPointerTouch(.9f, .9f, false)
        updateLegacyPointerTouch(.7f, .8f, true)
        shadowOf(Looper.getMainLooper()).idle()
        down.callback()!!.onCompleted(down.description())
        val release = shadow.gesturesDispatched.last()
        val bounds = RectF()
        release.description().getStroke(0).path.computeBounds(bounds, true)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        assertEquals(Math.round(.4f * (metrics.widthPixels - 1)).toFloat(), bounds.right, 0f)
        assertEquals(Math.round(.6f * (metrics.heightPixels - 1)).toFloat(), bounds.bottom, 0f)
        assertFalse(release.description().getStroke(0).willContinue())
        assertTrue("Pending movement uses a real movement interval", release.description().getStroke(0).duration > 1L)
        release.callback()!!.onCompleted(release.description())
        assertEquals(3, shadow.gesturesDispatched.size)
        val nextBounds = RectF()
        shadow.gesturesDispatched.last().description().getStroke(0).path.computeBounds(nextBounds, true)
        assertEquals(Math.round(.7f * (metrics.widthPixels - 1)).toFloat(), nextBounds.left, 0f)
        assertEquals(Math.round(.8f * (metrics.heightPixels - 1)).toFloat(), nextBounds.top, 0f)
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
    }

    @Test fun safetyChangeCancelsQueuedFinalMovementInsteadOfDropping() {
        updateLegacyPointerTouch(.2f, .2f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val down = shadow.gesturesDispatched.single()
        updateLegacyPointerTouch(.4f, .6f, true)
        updateLegacyPointerTouch(.9f, .9f, false)
        shadowOf(Looper.getMainLooper()).idle()
        AirRuntime.controlMode = ControlMode.PAUSED
        down.callback()!!.onCompleted(down.description())
        val cancellation = shadow.gesturesDispatched.last()
        val bounds = RectF()
        cancellation.description().getStroke(0).path.computeBounds(bounds, true)
        assertEquals("Cancellation must not move to the queued drop target", 0f, bounds.width(), 0f)
        assertEquals(0f, bounds.height(), 0f)
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)
    }

    @Test fun releaseDuringEndingDoesNotReplayAnotherTouch() {
        updateLegacyPointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        updateLegacyPointerTouch(.4f, .5f, false)
        shadowOf(Looper.getMainLooper()).idle()
        first.callback()!!.onCompleted(first.description())
        val ending = shadow.gesturesDispatched.last()
        updateLegacyPointerTouch(.7f, .8f, true)
        updateLegacyPointerTouch(.7f, .8f, false)
        shadowOf(Looper.getMainLooper()).idle()
        ending.callback()!!.onCompleted(ending.description())
        assertEquals("Do not replay old contacts after callback congestion", 2, shadow.gesturesDispatched.size)
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
    }

    @Test fun safetyChangeDropsNextPressWaitingForFingerUp() {
        updateLegacyPointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        updateLegacyPointerTouch(.4f, .5f, false)
        updateLegacyPointerTouch(.7f, .8f, true)
        shadowOf(Looper.getMainLooper()).idle()
        first.callback()!!.onCompleted(first.description())
        val ending = shadow.gesturesDispatched.last()
        AirRuntime.controlMode = ControlMode.PAUSED
        ending.callback()!!.onCompleted(ending.description())
        assertEquals(2, shadow.gesturesDispatched.size)
    }

    @Test fun stationaryHoldSurvivesThenDragsPausesAndReleases() {
        updateLegacyPointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val down = shadow.gesturesDispatched.single()
        down.callback()!!.onCompleted(down.description())
        repeat(12) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            updateLegacyPointerTouch(.4000001f, .5000001f, true)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Stationary/subpixel continuation would have no Android motion events", 1,
                shadow.gesturesDispatched.size)
        }
        updateLegacyPointerTouch(.4f, .7f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, shadow.gesturesDispatched.size)
        val drag = shadow.gesturesDispatched.last()
        val bounds = RectF()
        drag.description().getStroke(0).path.computeBounds(bounds, true)
        assertTrue(bounds.bottom > bounds.top)
        assertTrue(drag.description().getStroke(0).willContinue())
        drag.callback()!!.onCompleted(drag.description())
        assertEquals("Pausing a drag must retain finger-down without an empty continuation", 2,
            shadow.gesturesDispatched.size)
        updateLegacyPointerTouch(.4f, .7f, false)
        shadowOf(Looper.getMainLooper()).idle()
        val release = shadow.gesturesDispatched.last()
        assertEquals(3, shadow.gesturesDispatched.size)
        assertFalse(release.description().getStroke(0).willContinue())
        release.callback()!!.onCompleted(release.description())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.cancelled)
    }

    @Test fun completedDownStillHasWatchdogProtectionWhileWaitingForMovement() {
        updateLegacyPointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val down = shadow.gesturesDispatched.single()
        down.callback()!!.onCompleted(down.description())
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(375))
        assertEquals("A held finger must cancel when camera samples stop", 2, shadow.gesturesDispatched.size)
        down.callback()!!.onCompleted(down.description())
        assertEquals(2, shadow.gesturesDispatched.size)
    }

    @Test fun lossOrSafetyChangeCancelsTouchAndLateCallbacksCannotRestartIt() {
        for (safetyChanged in listOf(false, true)) {
            AirRuntime.controlMode = ControlMode.ARMED
            updateLegacyPointerTouch(0.4f, 0.5f, false)
            shadowOf(Looper.getMainLooper()).idle()
            val oldCount = shadow.gesturesDispatched.size
            updateLegacyPointerTouch(0.4f, 0.5f, true)
            shadowOf(Looper.getMainLooper()).idle()
            val first = shadow.gesturesDispatched.last()
            if (safetyChanged) {
                AirRuntime.controlMode = ControlMode.PAUSED
                first.callback()!!.onCompleted(first.description())
            } else {
                legacyTouch.cancel()
                shadowOf(Looper.getMainLooper()).idle()
            }
            val afterCancel = shadow.gesturesDispatched.size
            assertEquals(oldCount + 2, afterCancel)
            first.callback()!!.onCompleted(first.description())
            assertEquals(afterCancel, shadow.gesturesDispatched.size)
            assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)
        }
    }

    @Test fun cameraStallCannotLeaveAnIndefinitelyHeldTouch() {
        updateLegacyPointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300L))
        assertEquals("Normal slow-camera cadence must not cancel the press", 1, shadow.gesturesDispatched.size)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(75L))
        assertEquals(2, shadow.gesturesDispatched.size)
        first.callback()!!.onCompleted(first.description())
        assertEquals(2, shadow.gesturesDispatched.size)
    }

    @Test fun rejectedPressCannotRetryUntilTheUserOpensTheirFingers() {
        shadow.setCanDispatchGestures(false)
        updateLegacyPointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.platformRejected)
        updateLegacyPointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.platformRejected)
        updateLegacyPointerTouch(0.4f, 0.5f, false)
        shadowOf(Looper.getMainLooper()).idle()
        shadow.setCanDispatchGestures(true)
        updateLegacyPointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, shadow.gesturesDispatched.size)
    }
    @Test fun lateNativeCompletionCannotRelabelANewerAction() {
        service.dispatch(AirAction.TAP)
        shadowOf(Looper.getMainLooper()).idle()
        val touch = shadow.gesturesDispatched.single()
        service.dispatch(AirAction.HOME)
        shadowOf(Looper.getMainLooper()).idle()
        val latest = AirRuntime.state.value.actionFeedback
        val completedBefore = AirRuntime.state.value.actionDispatchTelemetry.completed
        assertEquals(AirAction.HOME,latest?.action)
        touch.callback()!!.onCompleted(touch.description())
        assertEquals(latest,AirRuntime.state.value.actionFeedback)
        assertEquals(completedBefore+1L,AirRuntime.state.value.actionDispatchTelemetry.completed)
    }
}
