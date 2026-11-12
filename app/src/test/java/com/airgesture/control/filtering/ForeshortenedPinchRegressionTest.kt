package com.airgesture.control.filtering

import com.airgesture.control.GestureInterpreter
import org.junit.Assert.*
import org.junit.Test

/** The long palm axis may remain measurable when a sideways hand is narrow. */
class ForeshortenedPinchRegressionTest {
    private fun pose(open: Boolean) = MutableList(21) { Point3D(.5f, .5f) }.apply {
        this[0] = Point3D(.5f, .9f) // wrist
        this[9] = Point3D(.5f, .5f) // middle MCP
        this[5] = Point3D(.49f, .5f)
        this[17] = Point3D(.51f, .5f)
        this[8] = Point3D(.5f, .3f)
        this[4] = Point3D(if (open) .2f else .5f, .4f)
        this[12] = Point3D(.54f, .4f)
    }

    @Test fun longitudinalPalmAxisRecoversVisibleForeshortenedContact() {
        val validator = KinematicValidator()
        val closed = pose(false)
        assertTrue(validator.projectedPalmScale(closed) > .25f)
        assertTrue(validator.calculateNormalizedFingerDistance(closed) < .18f)
        assertTrue(validator.calculateNormalizedFingerDistance(pose(true)) > .32f)
        assertTrue(PoseGeometryEvidenceEvaluator().evaluatePointer(closed).accepted)
    }

    @Test fun observedOpenContactAndReleaseStillProduceOnlyOneTap() {
        val interpreter = GestureInterpreter()
        assertFalse(interpreter.processFrame(pose(true), 1000L)!!.isClickEngaged)
        assertTrue(interpreter.processFrame(pose(false), 1100L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(pose(false), 1200L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(pose(false), 1300L)!!.isClickEngaged)
        interpreter.processFrame(pose(true), 1400L)
        assertTrue(interpreter.processFrame(pose(false), 1500L)!!.isClickEngaged)
    }

    @Test fun invalidOrOutOfViewLongitudinalAnchorsCannotInventContact() {
        val validator = KinematicValidator()
        for (wrist in listOf(Point3D(Float.NaN, .9f),
            Point3D(.5f, -1f), Point3D(.5f, .9f, Float.NaN))) {
            val p = pose(false).apply { this[0] = wrist }
            assertEquals(.02f, validator.projectedPalmScale(p), .0001f)
            assertTrue(validator.calculateNormalizedFingerDistance(p) > .32f)
        }
    }

    @Test fun depthSeparatedOverlapStillCannotClickWhenPalmIsNarrow() {
        val interpreter = GestureInterpreter()
        interpreter.processFrame(pose(true), 1000L)
        val separated = pose(false).apply { this[4] = Point3D(.5f, .4f, -.02f) }
        assertFalse(interpreter.processFrame(separated, 1100L)!!.isClickEngaged)
        assertFalse(interpreter.processFrame(separated, 1200L)!!.isClickEngaged)
    }

    @Test fun collapsedTransverseAnchorsRemainInvalidForPinchActions() {
        val p = pose(false).apply { this[17] = this[5] }
        assertFalse(PoseGeometryEvidenceEvaluator().evaluatePointer(p).accepted)
    }
}
