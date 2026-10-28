package com.airgesture.control

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35])
class GuidedCalibrationFlowTest {
    @Test fun gestureIntentStartsGuidedCaptureWithoutStartingCameraAndIsConsumedOnce() {
        val context = RuntimeEnvironment.getApplication() as Context
        val request = android.content.Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CALIBRATION)
        val controller = Robolectric.buildActivity(MainActivity::class.java, request).setup()
        try {
            val activity = controller.get()
            assertEquals(PointerCalibrationSession.State.COLLECTING, session(activity).state())
            assertEquals(ControlMode.READY, AirRuntime.controlMode)
            assertEquals(android.content.Intent.ACTION_MAIN, activity.intent.action)
            assertNull(org.robolectric.Shadows.shadowOf(activity).nextStartedService)
            AirRuntime.setRawPointerState(.2f, .2f, true)
            invoke(activity, "captureCalibrationSample")
            // Re-delivery of the consumed intent must not reset an in-progress draft.
            controller.newIntent(activity.intent)
            assertEquals(1, session(activity).sampleCount())
            controller.newIntent(android.content.Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CALIBRATION))
            assertEquals(0, session(activity).sampleCount())
            assertEquals(PointerCalibrationSession.State.COLLECTING, session(activity).state())
            assertFalse(PointerCalibrationStore(activity).enabled())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun recreationDoesNotReplayCalibrationOrOverwriteTheSavedProfile() {
        val context = RuntimeEnvironment.getApplication() as Context
        val original = PointerCalibrationProfile.COMFORTABLE_REACH
        PointerCalibrationStore(context).save(original)
        val request = android.content.Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CALIBRATION)
        val controller = Robolectric.buildActivity(MainActivity::class.java, request).setup()
        try {
            controller.recreate()
            assertEquals(android.content.Intent.ACTION_MAIN, controller.get().intent.action)
            assertEquals(PointerCalibrationSession.State.IDLE, session(controller.get()).state())
            assertEquals(original, PointerCalibrationStore(context).profile())
            assertNotEquals(ControlMode.ARMED, AirRuntime.controlMode)
        } finally { controller.pause().stop().destroy() }
    }

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("aergis_calibration", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE).edit().clear().commit()
        AirRuntime.controlMode = ControlMode.READY
        AirRuntime.setRawPointerState(.5f, .5f, false)
    }

    @After fun cleanup() {
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.setRawPointerState(.5f, .5f, false)
    }

    @Test fun startingCalibrationDisarmsActions() = withActivity { activity ->
        AirRuntime.controlMode = ControlMode.ARMED
        val epoch = AirRuntime.actionEpoch
        invoke(activity, "startCalibration")
        assertEquals(ControlMode.READY, AirRuntime.controlMode)
        assertTrue(AirRuntime.actionEpoch > epoch)
        assertEquals(PointerCalibrationSession.State.COLLECTING, session(activity).state())
    }

    @Test fun cancellingClearsDraftAndPreservesSavedProfile() = withActivity { activity ->
        val store = PointerCalibrationStore(activity)
        val original = PointerCalibrationProfile.COMFORTABLE_REACH
        assertTrue(store.save(original))
        invoke(activity, "startCalibration")
        AirRuntime.setRawPointerState(.2f, .2f, true)
        invoke(activity, "captureCalibrationSample")
        assertEquals(1, session(activity).sampleCount())
        invoke(activity, "cancelCalibration")
        assertEquals(PointerCalibrationSession.State.IDLE, session(activity).state())
        assertEquals(0, session(activity).sampleCount())
        assertEquals(original, store.profile())
        assertTrue(store.enabled())
        assertNotEquals(ControlMode.ARMED, AirRuntime.controlMode)
    }

    @Test fun savingValidCornersClosesDraftWithoutRearming() = withActivity { activity ->
        invoke(activity, "startCalibration")
        for ((x, y) in listOf(.2f to .2f, .8f to .2f, .8f to .8f, .2f to .8f)) {
            AirRuntime.setRawPointerState(x, y, true)
            invoke(activity, "captureCalibrationSample")
        }
        invoke(activity, "completeCalibration")
        val store = PointerCalibrationStore(activity)
        assertTrue(store.enabled())
        assertEquals(.2f, store.profile().left, .0001f)
        assertEquals(.8f, store.profile().bottom, .0001f)
        assertEquals(PointerCalibrationSession.State.IDLE, session(activity).state())
        assertNotEquals(ControlMode.ARMED, AirRuntime.controlMode)
    }

    @Test fun lostTrackingDoesNotAdvanceTheGuidedStep() = withActivity { activity ->
        invoke(activity, "startCalibration")
        invoke(activity, "captureCalibrationSample")
        assertEquals(0, session(activity).sampleCount())
        assertEquals(PointerCalibrationSession.State.COLLECTING, session(activity).state())
        assertFalse(PointerCalibrationStore(activity).enabled())
    }

    private fun invoke(activity: MainActivity, method: String) {
        MainActivity::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(activity)
    }

    private fun session(activity: MainActivity) = MainActivity::class.java
        .getDeclaredField("calibrationSession").apply { isAccessible = true }
        .get(activity) as PointerCalibrationSession

    private fun withActivity(block: (MainActivity) -> Unit) {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try { block(controller.get()) } finally { controller.pause().stop().destroy() }
    }
}
