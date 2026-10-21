package com.airgesture.control.filtering

import kotlin.math.sqrt

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

        // MediaPipe x and z are expressed in image-width units while y is in
        // image-height units. Convert all three axes to the same upright physical
        // scale before comparing contact distance with palm width. Using 2D for the
        // fingertips but depth for the palm makes an unchanged hand appear to pinch
        // merely because the palm rolls toward profile.
        val palmWidth = physicalDistance(indexMcp, pinkyMcp, aspectRatio)
            .coerceAtLeast(MIN_PALM_WIDTH_PX)
        val fingertipDistance = physicalDistance(thumbTip, middleTip, aspectRatio)

        return fingertipDistance / palmWidth
    }

    private fun physicalDistance(a: Point3D, b: Point3D, aspectRatio: Float): Float {
        val dx = (a.x - b.x) * aspectRatio
        val dy = a.y - b.y
        val dz = (a.z - b.z) * aspectRatio
        return sqrt(dx * dx + dy * dy + dz * dz)
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
