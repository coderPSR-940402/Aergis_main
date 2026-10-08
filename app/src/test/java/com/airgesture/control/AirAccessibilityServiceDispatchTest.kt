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
        AirRuntime.resetActionDispatchTelemetry()
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.setForegroundContext(ForegroundContextState())
        AirRuntime.setPointerState(0f, 0f, tracking = false)
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

    @Test fun heldTouchContinuesForLongPressAndMovesForDragScrolling() {
        service.updatePointerTouch(0.4f, 0.5f, true)
        shadowOf(Looper.getMainLooper()).idle()
        repeat(12) {
            val previous = shadow.gesturesDispatched.last()
            service.updatePointerTouch(0.4f, 0.5f, true)
            shadowOf(Looper.getMainLooper()).idle()
            previous.callback()!!.onCompleted(previous.description())
            assertTrue(shadow.gesturesDispatched.last().description().getStroke(0).willContinue())
        }
        service.updatePointerTouch(0.4f, 0.7f, true)
        shadowOf(Looper.getMainLooper()).idle()
        val stationary = shadow.gesturesDispatched.last()
        stationary.callback()!!.onCompleted(stationary.description())
        val drag = shadow.gesturesDispatched.last().description().getStroke(0)
        val bounds = RectF()
        drag.path.computeBounds(bounds, true)
        assertTrue(drag.willContinue())
        assertTrue(bounds.bottom > bounds.top)
        assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.completed)
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
