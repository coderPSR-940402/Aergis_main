package com.airgesture.control

import com.airgesture.control.filtering.PoseGeometryEvidence
import com.airgesture.control.filtering.PoseGeometryRejectionReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandTrackingEligibilityTest {
    @Test
    fun invalidPoseCannotQualifyForCommandTracking() {
        assertFalse(
            CommandTrackingEligibility.isEligible(
                controlSafe = true,
                handSelected = true,
                pointerEnabled = false,
                indexTipPresent = false,
                poseEvidence = rejectedPose(PoseGeometryRejectionReason.NON_FINITE_LANDMARK)
            )
        )
    }

    @Test
    fun validPoseCanQualifyWhenPointerIsDisabled() {
        assertTrue(
            CommandTrackingEligibility.isEligible(
                controlSafe = true,
                handSelected = true,
                pointerEnabled = false,
                indexTipPresent = false,
                poseEvidence = acceptedPose()
            )
        )
    }

    @Test
    fun pointerEnabledRequiresIndexTipEvenWithValidPose() {
        assertFalse(
            CommandTrackingEligibility.isEligible(
                controlSafe = true,
                handSelected = true,
                pointerEnabled = true,
                indexTipPresent = false,
                poseEvidence = acceptedPose()
            )
        )
    }

    @Test
    fun missingPoseEvidenceFailsClosed() {
        assertFalse(
            CommandTrackingEligibility.isEligible(
                controlSafe = true,
                handSelected = true,
                pointerEnabled = false,
                indexTipPresent = false,
                poseEvidence = null
            )
        )
    }

    private fun acceptedPose() = PoseGeometryEvidence(
        landmarkCount = 21,
        finiteLandmarkCount = 21,
        normalizedPalmWidth = 0.2f,
        indexBoneToPalmRatio = 0.8f,
        fingertipToPalmRatio = 0.1f,
        accepted = true,
        rejectionReason = PoseGeometryRejectionReason.NONE
    )

    private fun rejectedPose(reason: PoseGeometryRejectionReason) = PoseGeometryEvidence(
        landmarkCount = 21,
        finiteLandmarkCount = 20,
        normalizedPalmWidth = null,
        indexBoneToPalmRatio = null,
        fingertipToPalmRatio = null,
        accepted = false,
        rejectionReason = reason
    )
}
