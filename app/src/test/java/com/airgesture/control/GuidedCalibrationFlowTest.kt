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
