package com.airgesture.control

import com.airgesture.control.filtering.PoseGeometryEvidence
import com.airgesture.control.filtering.PoseGeometryRejectionReason

/** Fail-closed eligibility for evidence that may advance consequential command state. */
internal object CommandTrackingEligibility {
    // An in-bounds, finite hand can momentarily fail the projected index/palm ratio
    // during a pose transition. This preserves continuity only, never action evidence.
    fun canRetainReadiness(
        controlSafe: Boolean,
        handSelected: Boolean,
        pointerEnabled: Boolean,
        indexTipPresent: Boolean,
        poseEvidence: PoseGeometryEvidence?
    ): Boolean = controlSafe && handSelected && (!pointerEnabled || indexTipPresent) &&
        poseEvidence?.rejectionReason == PoseGeometryRejectionReason.EXCESSIVE_INDEX_BONE

    fun isEligible(
        controlSafe: Boolean,
        handSelected: Boolean,
        pointerEnabled: Boolean,
        indexTipPresent: Boolean,
        poseEvidence: PoseGeometryEvidence?
    ): Boolean {
        return controlSafe &&
            handSelected &&
            (!pointerEnabled || indexTipPresent) &&
            poseEvidence?.accepted == true
    }
}
