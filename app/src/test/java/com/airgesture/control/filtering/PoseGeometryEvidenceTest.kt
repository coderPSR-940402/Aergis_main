package com.airgesture.control.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseGeometryEvidenceTest {
    @Test
    fun extendedIndexDoesNotVetoThumbMiddleContactWhenPalmIsForeshortened() {
        val evaluator = PoseGeometryEvidenceEvaluator()
        for (width in listOf(.20f, .12f, .06f)) {
            val pose = validLandmarks().toMutableList().apply {
                this[5] = Point3D(.5f - width / 2, .6f)
                this[17] = Point3D(.5f + width / 2, .6f)
                this[8] = Point3D(.5f - width / 2, .35f)
                this[4] = Point3D(.5f, .55f)
                this[12] = Point3D(.505f, .55f)
            }
            val pointer = evaluator.evaluatePointer(pose, .75f)
            assertTrue("Visible pinch must not depend on index-to-palm ratio at width $width", pointer.accepted)
            assertFalse("Classifier geometry remains strict", evaluator.evaluate(pose, .75f).accepted)
            assertTrue(KinematicValidator().calculateNormalizedFingerDistance(pose, .75f) < .18f)
        }
    }

    @Test
    fun pointerContactStillRejectsMissingInvisibleAndDegenerateEvidence() {
        val evaluator = PoseGeometryEvidenceEvaluator()
        assertFalse(evaluator.evaluatePointer(validLandmarks().take(12)).accepted)
        for (index in listOf(4, 5, 8, 12, 17)) {
            for (bad in listOf(Point3D(Float.NaN, .5f), Point3D(1.01f, .5f))) {
                val pose = validLandmarks().toMutableList().apply { this[index] = bad }
                assertFalse("Required landmark $index must be usable", evaluator.evaluatePointer(pose).accepted)
            }
        }
        val collapsed = validLandmarks().toMutableList().apply { this[17] = this[5] }
        assertFalse(evaluator.evaluatePointer(collapsed).accepted)
    }

    @Test
    fun validPoseProducesOnlyBoundedAggregateMetrics() {
        val evidence = PoseGeometryEvidenceEvaluator().evaluate(validLandmarks())

        assertTrue(evidence.accepted)
        assertEquals(PoseGeometryRejectionReason.NONE, evidence.rejectionReason)
        assertEquals(21, evidence.landmarkCount)
        assertEquals(21, evidence.finiteLandmarkCount)
        assertNotNull(evidence.normalizedPalmWidth)
        assertTrue(evidence.indexBoneToPalmRatio!! in 0f..2.7f)
        assertTrue(evidence.fingertipToPalmRatio!! in 0f..2f)
    }

    @Test
    fun insufficientPoseFailsClosedWithoutCoordinates() {
        val evidence = PoseGeometryEvidenceEvaluator().evaluate(List(8) { Point3D(0.5f, 0.5f) })

        assertFalse(evidence.accepted)
        assertEquals(PoseGeometryRejectionReason.INSUFFICIENT_LANDMARKS, evidence.rejectionReason)
        assertEquals(null, evidence.indexBoneToPalmRatio)
        assertEquals(null, evidence.fingertipToPalmRatio)
    }

    @Test
    fun nonFinitePoseFailsClosed() {
        val landmarks = validLandmarks().toMutableList()
        landmarks[KinematicValidator.INDEX_TIP] = Point3D(Float.NaN, 0.5f)

        val evidence = PoseGeometryEvidenceEvaluator().evaluate(landmarks)

        assertFalse(evidence.accepted)
        assertEquals(PoseGeometryRejectionReason.NON_FINITE_LANDMARK, evidence.rejectionReason)
        assertEquals(20, evidence.finiteLandmarkCount)
    }

    @Test
    fun excessiveIndexBoneIsNamedAndBounded() {
        val landmarks = validLandmarks().toMutableList()
        landmarks[KinematicValidator.INDEX_TIP] = Point3D(1f, 0.5f)

        val evidence = PoseGeometryEvidenceEvaluator().evaluate(landmarks)

        assertFalse(evidence.accepted)
        assertEquals(PoseGeometryRejectionReason.EXCESSIVE_INDEX_BONE, evidence.rejectionReason)
        assertTrue(evidence.indexBoneToPalmRatio!! <= 2.7f)
    }

    @Test
    fun outOfRangePoseFailsClosed() {
        val landmarks = validLandmarks().toMutableList()
        landmarks[KinematicValidator.INDEX_MCP] = Point3D(1.1f, 0.5f)

        val evidence = PoseGeometryEvidenceEvaluator().evaluate(landmarks)

        assertFalse(evidence.accepted)
        assertEquals(PoseGeometryRejectionReason.OUT_OF_RANGE_LANDMARK, evidence.rejectionReason)
    }

    private fun validLandmarks(): List<Point3D> = MutableList(21) { Point3D(0.5f, 0.5f) }.apply {
        this[KinematicValidator.INDEX_MCP] = Point3D(0.4f, 0.5f)
        this[KinematicValidator.PINKY_MCP] = Point3D(0.6f, 0.5f)
        this[KinematicValidator.INDEX_TIP] = Point3D(0.55f, 0.5f)
        this[KinematicValidator.MIDDLE_TIP] = Point3D(0.56f, 0.5f)
    }
}
