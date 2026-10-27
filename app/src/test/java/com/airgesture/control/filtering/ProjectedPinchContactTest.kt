package com.airgesture.control.filtering

import com.airgesture.control.GestureInterpreter
import org.junit.Assert.*
import org.junit.Test

/** Synthetic geometry: no private camera images or recorded landmarks. */
class ProjectedPinchContactTest {
    private fun pose(closed: Boolean) = MutableList(21) { Point3D(.5f, .5f) }.apply {
        this[5] = Point3D(.4f, .5f)
        this[17] = Point3D(.6f, .5f)
        this[8] = Point3D(.5f, .3f)
        this[4] = Point3D(if (closed) .50f else .42f, .4f, -.05f)
        this[12] = Point3D(.52f, .4f, 0f)
    }

    @Test fun estimatedTipDepthCannotVetoVisibleThumbMiddleContact() {
        // 0.02 image separation / 0.20 palm width = 0.10. The independent
        // monocular depth estimate must not turn that contact into 0.269.
        assertEquals(.10f, KinematicValidator().calculateNormalizedFingerDistance(pose(true)), .0001f)
    }

    @Test fun slowObservedContactClicksOnceDespiteTipDepthDisagreement() {
        val interpreter = GestureInterpreter()
        assertFalse(interpreter.processFrame(pose(false), 1000L)!!.isClickEngaged)
        assertTrue(interpreter.processFrame(pose(true), 1100L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(pose(true), 1200L)!!.isClickEngaged)
        assertTrue(interpreter.processFrame(pose(true), 1300L)!!.isPinchPressed)
        assertFalse(interpreter.processFrame(pose(false), 1400L)!!.isClickEngaged)
        assertTrue(interpreter.processFrame(pose(true), 1500L)!!.isClickEngaged)
    }

    @Test fun highRateContactStillNeedsConfirmationAndOpeningAfterReset() {
        val interpreter = GestureInterpreter()
        assertFalse(interpreter.processFrame(pose(true), 900L)!!.isClickEngaged)
        interpreter.processFrame(pose(false), 1000L)
        assertFalse(interpreter.processFrame(pose(true), 1033L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(pose(false), 1066L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(pose(true), 1099L)!!.isClickEngaged)
        assertTrue(interpreter.processFrame(pose(true), 1150L)!!.isClickEngaged)
        interpreter.resetActions()
        assertFalse(interpreter.processFrame(pose(true), 1250L)!!.isClickEngaged)
    }

    @Test fun estimatedPalmDepthCannotTurnSeparatedFingersIntoContact() {
        val interpreter = GestureInterpreter()
        val separated = pose(false).apply {
            this[4] = Point3D(.46f, .4f)
            this[12] = Point3D(.54f, .4f)
        }
        interpreter.processFrame(separated, 1000L)
        val refit = separated.toMutableList().apply { this[17] = Point3D(.6f, .5f, .5f) }
        assertFalse(interpreter.processFrame(refit, 1100L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(refit, 1200L)!!.isPinchApproaching)
    }

    @Test fun portraitAndLandscapeContactAgreeDespiteDepthDisagreement() {
        val portrait = pose(true)
        val landscape = portrait.map { Point3D(1f - it.y, it.x, it.z) }
        assertEquals(.10f, KinematicValidator().calculateNormalizedFingerDistance(portrait, .75f), .0001f)
        assertEquals(.10f, KinematicValidator().calculateNormalizedFingerDistance(landscape, 4f / 3f), .0001f)
    }
}
