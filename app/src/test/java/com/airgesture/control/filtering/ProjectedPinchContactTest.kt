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

    @Test fun tiltingAnOpenHandCannotCreateContactFromProjectedOverlap() {
        // Rigid pitch compresses vertical fingertip separation into depth without
        // closing the fingers. Palm width, along the perpendicular axis, is unchanged.
        val open = pose(false).apply {
            this[4] = Point3D(.5f, .36f)
            this[12] = Point3D(.5f, .44f)
        }
        val tilted = open.map {
            val dy = it.y - .5f
            Point3D(it.x, .5f + dy * .17364818f, dy * .98480775f)
        }
        val interpreter = GestureInterpreter()
        interpreter.processFrame(open, 1000L)
        for (time in 1100L..1600L step 100L) {
            assertFalse("A rigid 80-degree pitch cannot close open fingers",
                interpreter.processFrame(tilted, time)!!.isClickEngaged)
        }
    }

    @Test fun clearlyDepthSeparatedFingersCannotClickWhenTheirImagesCoincide() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(false), 1000L)
        val overlap = pose(true).apply { this[4] = Point3D(.52f, .4f, -.08f) }
        assertFalse(interpreter.processFrame(overlap, 1100L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(overlap, 1200L)!!.isClickEngaged)
    }

    @Test fun depthDisagreementDuringAHeldClickCannotRearmIt() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(false), 1000L)
        assertTrue(interpreter.processFrame(pose(true), 1100L)!!.isClickEngaged)
        val refit = pose(true).apply { this[4] = Point3D(.5f, .4f, -.08f) }
        assertFalse(interpreter.processFrame(refit, 1200L)!!.isClickEngaged)
        assertTrue(interpreter.processFrame(pose(true), 1300L)!!.isPinchPressed)
        assertFalse(interpreter.processFrame(pose(true), 1400L)!!.isClickEngaged)
    }

    @Test fun recordedContactScaleDepthDisagreementStillAllowsOneClick() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(false), 1000L)
        val contact = pose(true).apply { this[4] = Point3D(.5f, .4f, -.0614f) }
        // 0.307 palm widths in depth is below the existing clearly-open boundary.
        assertTrue(interpreter.processFrame(contact, 1100L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(contact, 1200L)!!.isClickEngaged)
    }

    @Test fun quarterTurnDepthUsesSensorWidthRatherThanUprightWidth() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(false), 1000L, aspectRatio = .75f, depthScale = 1f)
        val separated = pose(true).apply { this[4] = Point3D(.5f, .4f, -.051f) }
        // Upright projected palm = .15; .051 / .15 = .34: clearly separated.
        // Incorrectly multiplying sensor z by .75 would yield .255 and allow a tap.
        assertFalse(interpreter.processFrame(separated, 1100L, aspectRatio = .75f, depthScale = 1f)!!.isClickEngaged)
    }

    @Test fun allEightRecordedProjectedContactRatiosRemainEligible() {
        // Rounded aggregate ratios, not private landmarks. Each pair is image/depth
        // separation in projected-palm units from the earlier CLICK segment.
        for ((image, depth) in listOf(.120f to .166f, .149f to .170f,
            .166f to .192f, .137f to .219f, .092f to .269f,
            .170f to .250f, .145f to .307f, .128f to .283f)) {
            val interpreter = GestureInterpreter()
            interpreter.processFrame(pose(false), 1000L)
            val contact = pose(true).apply {
                this[4] = Point3D(.52f - image * .2f, .4f, -depth * .2f)
            }
            assertTrue("image=$image, depth=$depth",
                interpreter.processFrame(contact, 1100L)!!.isClickEngaged)
            assertFalse(interpreter.processFrame(contact, 1200L)!!.isClickEngaged)
        }
    }

    @Test fun invalidDepthUnitsCannotCarryOpeningEvidenceIntoLaterContact() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(false), 1000L)
        assertFalse(interpreter.processFrame(pose(true), 1100L, depthScale = Float.NaN)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(pose(true), 1200L)!!.isClickEngaged)
    }
}
