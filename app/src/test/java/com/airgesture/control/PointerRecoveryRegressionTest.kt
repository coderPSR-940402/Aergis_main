package com.airgesture.control

import com.airgesture.control.pointer.ClickHysteresisStateMachine
import org.junit.Assert.*
import com.airgesture.control.filtering.Point3D
import com.airgesture.control.filtering.PoseGeometryEvidenceEvaluator
import org.junit.Test

class PointerRecoveryRegressionTest {
    @Test fun lowCadenceCursorSurvivesOneMissingResultButCannotPersistIndefinitely() {
        val visibility = PointerVisibilityGrace()
        val point = PointerCoordinateMapper.Point(.4f, .6f)
        visibility.record(point, 1000L)
        visibility.record(point, 1200L)
        assertEquals("A missing 5 fps sample is already 200 ms old", point, visibility.heldAt(1400L))
        assertEquals(point, visibility.heldAt(1600L))
        assertNull("Retention is bounded", visibility.heldAt(1701L))
        visibility.reset()
        assertNull(visibility.heldAt(1400L))
        visibility.record(point, 2000L)
        assertNull("New sessions must not inherit a slow cadence", visibility.heldAt(2131L))
    }
    @Test fun croppedWristDoesNotInvalidateVisiblePinchFingers() {
        val evaluator = PoseGeometryEvidenceEvaluator()
        val pose = MutableList(21) { Point3D(.5f, .5f) }.apply {
            this[0] = Point3D(.5f, 1.02f)
            this[5] = Point3D(.4f, .5f); this[17] = Point3D(.6f, .5f)
            this[8] = Point3D(.5f, .4f)
            this[4] = Point3D(.5f, .5f); this[12] = Point3D(.51f, .5f)
        }
        fun pointerEvidence() = evaluator.evaluatePointer(pose)
        assertFalse("Classifier still requires a whole hand", evaluator.evaluate(pose).accepted)
        assertTrue("Visible thumb/middle/index must remain usable", pointerEvidence().accepted)
        pose[4] = Point3D(1.02f, .5f)
        assertFalse("An off-camera thumb cannot click", pointerEvidence().accepted)
    }

    @Test fun narrowCalibrationDoesNotMultiplySmallMotionSixfold() {
        val saved = PointerCalibrationProfile(left = .43303388f, right = .591645f,
            top = .6092281f, bottom = .7685115f)
        val mapper = LivePointerMapper { saved }
        val centerX = 1f - (saved.left + saved.right) / 2f
        val centerY = (saved.top + saved.bottom) / 2f
        val center = mapper.map(centerX, centerY)
        val moved = mapper.map(centerX + .02f, centerY + .02f)
        assertEquals(.5f, center.x, .0001f)
        assertEquals(.5f, center.y, .0001f)
        assertTrue("2% camera movement must not become 12% screen movement", center.x - moved.x <= .06f)
        assertTrue(moved.y - center.y <= .06f)
        assertEquals("Reading must not overwrite saved calibration", .43303388f, saved.left, 0f)
    }

    @Test fun slowCadenceDoesNotResetPrecisionToRawPosition() {
        val filter = PrecisionPointerFilter()
        filter.update(.5f, .5f, 1000L)
        val next = filter.update(.52f, .5f, 1267L)
        assertTrue(filter.measurementTrusted)
        assertTrue("267 ms cadence must retain smoothing", next.x < .519f)
        assertTrue(next.x > .5f)
    }

    @Test fun singleContactBetweenSlowOpenSamplesProducesOneReleaseClick() {
        val click = ClickHysteresisStateMachine()
        assertFalse(click.processFrame(.8f, 1000L))
        assertFalse(click.processFrame(.1f, 1200L))
        assertTrue("A complete slow-cadence pinch must click", click.processFrame(.8f, 1400L))
        assertFalse(click.processFrame(.8f, 1600L))
        assertFalse(click.processFrame(.1f, 1800L))
        assertTrue(click.processFrame(.8f, 2000L))
    }

    @Test fun oneHighRateContactGlitchStillCannotClick() {
        val click = ClickHysteresisStateMachine()
        click.processFrame(.8f, 1000L)
        assertFalse(click.processFrame(.1f, 1033L))
        assertFalse(click.processFrame(.8f, 1066L))
    }

    @Test fun lowCadenceHeldPinchSurvives267msAndReleasesWithoutSecondClick() {
        val click = ClickHysteresisStateMachine()
        click.processFrame(.8f, 1000L)
        click.processFrame(.1f, 1267L)
        assertTrue(click.processFrame(.1f, 1534L))
        assertEquals(ClickHysteresisStateMachine.State.CLICKED, click.getCurrentState())
        assertFalse(click.processFrame(.8f, 1801L))
    }
}
