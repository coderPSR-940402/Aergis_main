package com.airgesture.control
import com.airgesture.control.gaze.GazeLabActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class GazeLabActivityTest {
    @Test fun labDisarmsActionsAndLeavingCannotRearmOrAlterHandCalibration() {
        AirRuntime.controlMode=ControlMode.ARMED
        AirRuntime.setPointerState(.25f,.75f,true)
        val controller=Robolectric.buildActivity(GazeLabActivity::class.java).create().start().resume()
        assertEquals(ControlMode.READY,AirRuntime.controlMode)
        assertEquals(.25f,AirRuntime.pointerX,0f)
        assertEquals(.75f,AirRuntime.pointerY,0f)
        controller.pause().stop().destroy()
        assertEquals(ControlMode.READY,AirRuntime.controlMode)
        AirRuntime.controlMode=ControlMode.OFF
    }
}
