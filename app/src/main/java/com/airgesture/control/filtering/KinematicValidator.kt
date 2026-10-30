package com.airgesture.control.filtering

import kotlin.math.abs

class KinematicValidator {

    companion object {
        const val WRIST = 0
        const val THUMB_TIP = 4
        const val INDEX_MCP = 5
        const val INDEX_TIP = 8
        const val MIDDLE_TIP = 12
        const val PINKY_MCP = 17

        private const val MAX_BONE_TO_PALM_RATIO = 1.35f
        private const val MIN_PALM_WIDTH_PX = 0.001f
    }

    fun calculateNormalizedFingerDistance(landmarks: List<Point3D>, aspectRatio: Float = 1f): Float {
        if (!aspectRatio.isFinite() || aspectRatio <= 0f) return Float.NaN
        if (landmarks.size <= PINKY_MCP) return 1.0f

        val indexMcp = landmarks[INDEX_MCP]
        val pinkyMcp = landmarks[PINKY_MCP]
        val thumbTip = landmarks[THUMB_TIP]
        val middleTip = landmarks[MIDDLE_TIP]

        // Compare both segments in the upright image plane, with the same aspect
        // correction. Estimated z can disagree at the tips: tip-depth disagreement
        // can veto visible contact, while a palm-depth refit can invent contact.
        // Do not mix projected fingertips with a depth-expanded palm denominator.
        val palmWidth = indexMcp.distance2DTo(pinkyMcp, aspectRatio)
            .coerceAtLeast(MIN_PALM_WIDTH_PX)
        val fingertipDistance = thumbTip.distance2DTo(middleTip, aspectRatio)

        return fingertipDistance / palmWidth
    }

    /** Depth can veto clearly separated fingertips; it never expands the palm scale.
     * [depthScale] converts unchanged sensor-width z into upright image-height units.
     * This differs from [aspectRatio] when the camera buffer is rotated 90/270 degrees.
     */
    fun calculateNormalizedFingerDepthDistance(landmarks: List<Point3D>, aspectRatio: Float = 1f,
        depthScale: Float = aspectRatio): Float {
        if (landmarks.size <= PINKY_MCP || !aspectRatio.isFinite() || aspectRatio <= 0f ||
            !depthScale.isFinite() || depthScale <= 0f) return Float.NaN
        val palmWidth = landmarks[INDEX_MCP].distance2DTo(landmarks[PINKY_MCP], aspectRatio)
            .coerceAtLeast(MIN_PALM_WIDTH_PX)
        return abs(landmarks[THUMB_TIP].z - landmarks[MIDDLE_TIP].z) * depthScale / palmWidth
    }

    fun validateAndConstrainIndexTip(
        landmarks: List<Point3D>,
        previousIndexTip: Point3D?
    ): Point3D {
        if (landmarks.size <= INDEX_TIP) return Point3D(0f, 0f, 0f)
        if (landmarks.size <= PINKY_MCP) return landmarks[INDEX_TIP]

        val indexMcp = landmarks[INDEX_MCP]
        val pinkyMcp = landmarks[PINKY_MCP]
        val currentTip = landmarks[INDEX_TIP]
        val palmWidth = indexMcp.distance2DTo(pinkyMcp).coerceAtLeast(MIN_PALM_WIDTH_PX)
        val boneLength = indexMcp.distance2DTo(currentTip)
        val maxAllowedBoneLength = palmWidth * MAX_BONE_TO_PALM_RATIO

        if (boneLength <= maxAllowedBoneLength) return currentTip
        if (boneLength <= MIN_PALM_WIDTH_PX) return previousIndexTip ?: indexMcp

        val dirX = (currentTip.x - indexMcp.x) / boneLength
        val dirY = (currentTip.y - indexMcp.y) / boneLength
        return Point3D(
            x = indexMcp.x + dirX * maxAllowedBoneLength,
            y = indexMcp.y + dirY * maxAllowedBoneLength,
            z = currentTip.z
        )
    }
}
