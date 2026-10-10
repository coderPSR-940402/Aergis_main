package com.airgesture.control.filtering

import org.junit.Assert.*
import org.junit.Test

class AspectCorrectPinchTest {
    @Test fun cameraAspectFollowsRotationAndPoseEvidenceUsesPhysicalGeometry() {
        assertEquals(.75f, com.airgesture.control.CameraCoordinateTransform.uprightAspectRatio(640, 480, 90), 0f)
        assertEquals(4f / 3f, com.airgesture.control.CameraCoordinateTransform.uprightAspectRatio(640, 480, 180), 0f)
        val pose = MutableList(21) { Point3D(.5f, .5f) }.apply {
            this[5] = Point3D(.4f, .5f); this[17] = Point3D(.6f, .5f)
            this[8] = Point3D(.4f, .36f)
        }
        val evidence = PoseGeometryEvidenceEvaluator().evaluate(pose, .5f)
        assertFalse(evidence.accepted)
        assertEquals(PoseGeometryRejectionReason.EXCESSIVE_INDEX_BONE, evidence.rejectionReason)
        assertFalse(PoseGeometryEvidenceEvaluator().evaluate(pose, Float.NaN).accepted)
    }

    @Test fun identicalPhysicalPinchHasSameDistanceInPortraitAndLandscapeFrames() {
        val portrait = MutableList(21) { Point3D(.5f, .5f) }.apply {
            this[5] = Point3D(.4f, .5f); this[17] = Point3D(.6f, .5f)
            this[4] = Point3D(.5f, .4f); this[12] = Point3D(.5f, .415f)
            this[8] = Point3D(.5f, .43f)
        }
        val landscape = portrait.map { Point3D(1f - it.y, it.x) }
        val validator = KinematicValidator()
        val method = runCatching { KinematicValidator::class.java.getMethod("calculateNormalizedFingerDistance",
            List::class.java, Float::class.javaPrimitiveType) }.getOrNull()
        fun distance(points: List<Point3D>, aspect: Float): Float = if (method == null)
            validator.calculateNormalizedFingerDistance(points) else method.invoke(validator, points, aspect) as Float
        assertEquals(.15f, distance(portrait, .5f), .0001f)
        assertEquals(.15f, distance(landscape, 2f), .0001f)
    }
}
