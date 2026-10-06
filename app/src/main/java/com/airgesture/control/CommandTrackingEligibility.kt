package com.airgesture.control

import com.airgesture.control.filtering.PoseGeometryEvidence

/** Fail-closed eligibility for evidence that may advance consequential command state. */
internal object CommandTrackingEligibility {
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
