package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class TouchFreeCalibrationTest {
    private val input = TouchFreeCalibration()
    private var time = 0L
    private fun frame(x: Float = .8f, y: Float = .2f, distance: Float = .7f, pose: String = "None", owner: String = "Left:1", epoch: Long = 1L): TouchFreeCalibration.Event? {
        time += 100L
        return input.update(CalibrationGestureFrame(time, epoch, owner, x, y, distance, 0f, pose))
    }
    private fun corner(x: Float, y: Float): TouchFreeCalibration.Event? {
        repeat(5) { frame(x, y) }
        return frame(x, y, .1f)
    }
    @Test fun fourOrderedCapturesRequireSeparateExplicitConfirmation() {
        input.start()
        for ((x,y) in listOf(.8f to .2f, .2f to .2f, .2f to .8f, .8f to .8f)) {
            assertTrue(corner(x,y) is TouchFreeCalibration.Event.Capture)
            assertNull(frame(x,y,.1f))
        }
        repeat(10) { assertNull(frame(pose="None")) }
        repeat(10) { assertNull(frame(pose="Open_Palm")) }
        assertEquals(TouchFreeCalibration.Event.Confirm, frame(pose="Open_Palm"))
        assertNull(frame(pose="Open_Palm"))
    }
    @Test fun motionAndWrongCornerCannotAdvance() {
        input.start()
        repeat(5) { frame(if(it%2==0).8f else .5f) }
        assertNull(frame(distance=.1f))
        assertTrue(corner(.8f,.2f) is TouchFreeCalibration.Event.Capture)
        assertEquals(TouchFreeCalibration.Event.WrongCorner, corner(.8f,.8f))
    }
    @Test fun handLossAndOwnerChangeRequireFreshOpenAndStableAim() {
        input.start(); repeat(5) { frame() }; input.update(null)
        assertNull(frame(distance=.1f))
        repeat(5) { frame() }
        assertNull(frame(distance=.1f,owner="Right:2"))
        assertNull(frame(distance=.1f,owner="Right:2"))
    }
    @Test fun staleFramesAndEpochChangesCannotConfirmAndFistCancelsOnce() {
        input.start()
        repeat(8) { frame(pose="Closed_Fist") }
        frame(pose="Closed_Fist",epoch=2)
        repeat(11) { assertNull(frame(pose="Closed_Fist",epoch=2)) }
        assertEquals(TouchFreeCalibration.Event.Cancel, frame(pose="Closed_Fist",epoch=2))
        assertNull(frame(pose="Closed_Fist",epoch=2))
    }
    @Test fun approachingContactCannotKeepAnOldAimAliveIndefinitely() {
        input.start(); repeat(5) { frame() }
        repeat(8) { frame(distance=.25f) }
        assertNull(frame(distance=.1f))
    }
    @Test fun palmLabelDuringHeldContactCannotConfirmTheLastCorner() {
        input.start()
        for ((x,y) in listOf(.8f to .2f,.2f to .2f,.2f to .8f,.8f to .8f)) corner(x,y)
        repeat(15) { assertNull(frame(distance=.1f,pose="Open_Palm")) }
    }
}
