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

    @Before
    fun setUp() {
        controller = Robolectric.buildService(AirAccessibilityService::class.java).create()
        service = controller.get()
        shadow = org.robolectric.Shadows.shadowOf(service)
        AirRuntime.resetActionDispatchTelemetry()
        AirRuntime.gesturesEnabled = true
        AirRuntime.pointerEnabled = true
        AirRuntime.controlMode = ControlMode.ARMED
        AirRuntime.motionActive = false
        AirRuntime.setForegroundContext(
            ForegroundContextState(
                packageName = "com.example.safe",
                className = "ExampleActivity",
                safety = ForegroundSafety.SAFE,
                reason = "Foreground context allowed"
            )
        )
        AirRuntime.setPointerState(0.5f, 0.5f, tracking = true)
    }

    @After
    fun tearDown() {
        controller.destroy()
        AirRuntime.pointerEnabled = true
        AirRuntime.resetActionDispatchTelemetry()
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.setForegroundContext(ForegroundContextState())
        AirRuntime.setPointerState(0f, 0f, tracking = false)
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
        AirRuntime.motionActive = true
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
        AirRuntime.motionActive = true
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
        service.dispatch(AirAction.SCROLL_UP)
        shadowOf(Looper.getMainLooper()).idle()

        // Legacy Robolectric PathMeasure.getPosTan synthesizes 0..1 values.
        // Bounds inspect the actual dispatched path, including both scroll endpoints.
        val bounds = RectF()
        shadow.gesturesDispatched.single().description().getStroke(0).path.computeBounds(bounds, true)
        assertEquals((metrics.widthPixels - 1).toFloat(), bounds.left, 0.01f)
        assertEquals(bounds.left, bounds.right, 0.01f)
        assertTrue(bounds.top in 0f..(metrics.heightPixels - 1).toFloat())
        assertTrue(bounds.bottom in 0f..(metrics.heightPixels - 1).toFloat())
        assertTrue(bounds.bottom > bounds.top)
    }

    @Test fun disablingPointerRejectsAnInFlightFrameEvenWithTheNewEpoch() {
        AirRuntime.pointerEnabled = false
        service.updatePointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("A late frame must not start a native touch after pointer disable", shadow.gesturesDispatched.isEmpty())
    }

    @Test fun pointerDisableCancelsHeldTouchAndLateCallbackCannotContinue() {
        service.updatePointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        AirRuntime.pointerEnabled = false
        first.callback()!!.onCompleted(first.description())
        assertEquals(2, shadow.gesturesDispatched.size)
        first.callback()!!.onCompleted(first.description())
        assertEquals(2, shadow.gesturesDispatched.size)
    }

    @Test fun pinchPressKeepsOneTouchDownAndReleaseEndsItWithoutAnExtraTap() {
        service.updatePointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        assertTrue(first.description().getStroke(0).willContinue())
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)

        service.updatePointerTouch(0.4f, 0.5f, false)
        shadowOf(Looper.getMainLooper()).idle()
        first.callback()!!.onCompleted(first.description())
        val ending = shadow.gesturesDispatched.last()
        assertEquals(2, shadow.gesturesDispatched.size)
        assertFalse(ending.description().getStroke(0).willContinue())
        ending.callback()!!.onCompleted(ending.description())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
    }

    @Test fun newPinchCannotEraseReleaseWhilePlatformCallbackIsPending() {
        service.updatePointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        service.updatePointerTouch(.4f, .5f, false)
        service.updatePointerTouch(.7f, .8f, true)
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

    @Test fun releaseDuringEndingDoesNotReplayAnotherTouch() {
        service.updatePointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        service.updatePointerTouch(.4f, .5f, false)
        shadowOf(Looper.getMainLooper()).idle()
        first.callback()!!.onCompleted(first.description())
        val ending = shadow.gesturesDispatched.last()
        service.updatePointerTouch(.7f, .8f, true)
        service.updatePointerTouch(.7f, .8f, false)
        shadowOf(Looper.getMainLooper()).idle()
        ending.callback()!!.onCompleted(ending.description())
        assertEquals("Do not replay old contacts after callback congestion", 2, shadow.gesturesDispatched.size)
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
    }

    @Test fun safetyChangeDropsNextPressWaitingForFingerUp() {
        service.updatePointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val first = shadow.gesturesDispatched.single()
        service.updatePointerTouch(.4f, .5f, false)
        service.updatePointerTouch(.7f, .8f, true)
        shadowOf(Looper.getMainLooper()).idle()
        first.callback()!!.onCompleted(first.description())
        val ending = shadow.gesturesDispatched.last()
        AirRuntime.motionActive = true
        ending.callback()!!.onCompleted(ending.description())
        assertEquals(2, shadow.gesturesDispatched.size)
    }

    @Test fun stationaryHoldSurvivesThenDragsPausesAndReleases() {
        service.updatePointerTouch(.4f, .5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val down = shadow.gesturesDispatched.single()
        down.callback()!!.onCompleted(down.description())
        repeat(12) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
            service.updatePointerTouch(.4000001f, .5000001f, true)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("Stationary/subpixel continuation would have no Android motion events", 1,
                shadow.gesturesDispatched.size)
        }
        service.updatePointerTouch(.4f, .7f, true)
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
        service.updatePointerTouch(.4f, .7f, false)
        shadowOf(Looper.getMainLooper()).idle()
        val release = shadow.gesturesDispatched.last()
        assertEquals(3, shadow.gesturesDispatched.size)
        assertFalse(release.description().getStroke(0).willContinue())
        release.callback()!!.onCompleted(release.description())
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.cancelled)
    }

    @Test fun completedDownStillHasWatchdogProtectionWhileWaitingForMovement() {
        service.updatePointerTouch(.4f, .5f, true)
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
            AirRuntime.motionActive = false
            service.updatePointerTouch(0.4f, 0.5f, false)
            shadowOf(Looper.getMainLooper()).idle()
            val oldCount = shadow.gesturesDispatched.size
            service.updatePointerTouch(0.4f, 0.5f, true)
            shadowOf(Looper.getMainLooper()).idle()
            val first = shadow.gesturesDispatched.last()
            if (safetyChanged) {
                AirRuntime.motionActive = true
                first.callback()!!.onCompleted(first.description())
            } else {
                service.cancelPointerTouch()
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
        service.updatePointerTouch(0.4f, 0.5f, true)
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
        service.updatePointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.platformRejected)
        service.updatePointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.platformRejected)
        service.updatePointerTouch(0.4f, 0.5f, false)
        shadowOf(Looper.getMainLooper()).idle()
        shadow.setCanDispatchGestures(true)
        service.updatePointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, shadow.gesturesDispatched.size)
    }
}
