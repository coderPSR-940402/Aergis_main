package com.airgesture.control.filtering

/** Reasons why a pose cannot be treated as geometrically valid evidence. */
enum class PoseGeometryRejectionReason {
    NONE,
    INSUFFICIENT_LANDMARKS,
    NON_FINITE_LANDMARK,
    OUT_OF_RANGE_LANDMARK,
    INVALID_PALM_WIDTH,
    EXCESSIVE_INDEX_BONE
}

/**
 * Privacy-safe aggregate pose evidence.
 *
 * This type deliberately contains no landmark coordinates or frame data. It is
 * suitable for diagnostics and replay reports because it retains only bounded
 * counts, ratios, and a named rejection reason.
 */
data class PoseGeometryEvidence(
    val landmarkCount: Int,
    val finiteLandmarkCount: Int,
    val normalizedPalmWidth: Float?,
    val indexBoneToPalmRatio: Float?,
    val fingertipToPalmRatio: Float?,
    val accepted: Boolean,
    val rejectionReason: PoseGeometryRejectionReason
)

class PoseGeometryEvidenceEvaluator(
    private val minimumLandmarkCount: Int = KinematicValidator.PINKY_MCP + 1,
    private val minimumPalmWidth: Float = 0.001f,
    private val maximumIndexBoneToPalmRatio: Float = 1.35f
) {
    fun evaluate(landmarks: List<Point3D>, aspectRatio: Float = 1f): PoseGeometryEvidence =
        evaluate(landmarks, aspectRatio, pointerOnly = false)

    /** Pointer pinch needs its fingertips and palm anchors visible, not the wrist or little tip. */
    fun evaluatePointer(landmarks: List<Point3D>, aspectRatio: Float = 1f): PoseGeometryEvidence =
        evaluate(landmarks, aspectRatio, pointerOnly = true)

    private fun evaluate(landmarks: List<Point3D>, aspectRatio: Float, pointerOnly: Boolean): PoseGeometryEvidence {
        val finiteCount = landmarks.count { point ->
            point.x.isFinite() && point.y.isFinite() && point.z.isFinite()
        }
        if (!aspectRatio.isFinite() || aspectRatio <= 0f) {
            return rejected(landmarks.size, finiteCount, PoseGeometryRejectionReason.INVALID_PALM_WIDTH)
        }
        if (landmarks.size < minimumLandmarkCount) {
            return rejected(landmarks.size, finiteCount, PoseGeometryRejectionReason.INSUFFICIENT_LANDMARKS)
        }
        if (!pointerOnly && finiteCount != landmarks.size) {
            return rejected(landmarks.size, finiteCount, PoseGeometryRejectionReason.NON_FINITE_LANDMARK)
        }
        val visiblePoints = if (pointerOnly) listOf(KinematicValidator.THUMB_TIP,
            KinematicValidator.INDEX_MCP, KinematicValidator.INDEX_TIP,
            KinematicValidator.MIDDLE_TIP, KinematicValidator.PINKY_MCP).map(landmarks::get) else landmarks
        if (visiblePoints.any { !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() }) {
            return rejected(landmarks.size, finiteCount, PoseGeometryRejectionReason.NON_FINITE_LANDMARK)
        }
        if (visiblePoints.any { point -> point.x !in 0f..1f || point.y !in 0f..1f }) {
            return rejected(landmarks.size, finiteCount, PoseGeometryRejectionReason.OUT_OF_RANGE_LANDMARK)
        }

        val indexMcp = landmarks[KinematicValidator.INDEX_MCP]
        val pinkyMcp = landmarks[KinematicValidator.PINKY_MCP]
        val indexTip = landmarks[KinematicValidator.INDEX_TIP]
        val middleTip = landmarks[KinematicValidator.MIDDLE_TIP]
        val palmWidth = indexMcp.distance2DTo(pinkyMcp, aspectRatio)
        if (palmWidth < minimumPalmWidth) {
            return rejected(
                landmarks.size,
                finiteCount,
                PoseGeometryRejectionReason.INVALID_PALM_WIDTH,
                normalizedPalmWidth = palmWidth.coerceIn(0f, 1f)
            )
        }

        val indexBoneRatio = indexMcp.distance2DTo(indexTip, aspectRatio) / palmWidth
        val fingertipRatio = indexTip.distance2DTo(middleTip, aspectRatio) / palmWidth
        val boundedPalmWidth = palmWidth.coerceIn(0f, 1f)
        val boundedBoneRatio = indexBoneRatio.coerceIn(0f, maximumIndexBoneToPalmRatio * 2f)
        val boundedFingertipRatio = fingertipRatio.coerceIn(0f, 2f)
        // Projected palm width shrinks when a hand turns, while an extended index may
        // remain long. That ratio is not evidence against thumb-middle contact. The
        // pointer path already requires visible finite landmarks, nonzero palm width,
        // trusted cursor motion and an observed open/contact/release sequence.
        // Keep the legacy whole-hand constraint for classifier commands only.
        if (!pointerOnly && indexBoneRatio > maximumIndexBoneToPalmRatio) {
            return PoseGeometryEvidence(
                landmarkCount = landmarks.size,
                finiteLandmarkCount = finiteCount,
                normalizedPalmWidth = boundedPalmWidth,
                indexBoneToPalmRatio = boundedBoneRatio,
                fingertipToPalmRatio = boundedFingertipRatio,
                accepted = false,
                rejectionReason = PoseGeometryRejectionReason.EXCESSIVE_INDEX_BONE
            )
        }
        return PoseGeometryEvidence(
            landmarkCount = landmarks.size,
            finiteLandmarkCount = finiteCount,
            normalizedPalmWidth = boundedPalmWidth,
            indexBoneToPalmRatio = boundedBoneRatio,
            fingertipToPalmRatio = boundedFingertipRatio,
            accepted = true,
            rejectionReason = PoseGeometryRejectionReason.NONE
        )
    }

    private fun rejected(
        landmarkCount: Int,
        finiteLandmarkCount: Int,
        reason: PoseGeometryRejectionReason,
        normalizedPalmWidth: Float? = null
    ): PoseGeometryEvidence = PoseGeometryEvidence(
        landmarkCount = landmarkCount,
        finiteLandmarkCount = finiteLandmarkCount,
        normalizedPalmWidth = normalizedPalmWidth?.coerceIn(0f, 1f),
        indexBoneToPalmRatio = null,
        fingertipToPalmRatio = null,
        accepted = false,
        rejectionReason = reason
    )
}
