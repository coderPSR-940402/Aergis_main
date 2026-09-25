package com.airgesture.control.filtering

import kotlin.math.sqrt

data class Point3D(val x: Float, val y: Float, val z: Float = 0f) {
    fun distanceTo(other: Point3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun distance2DTo(other: Point3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }
}

class KinematicValidator {

    companion object {
        const val WRIST = 0
        const val INDEX_MCP = 5
        const val INDEX_TIP = 8
        const val MIDDLE_TIP = 12
        const val PINKY_MCP = 17

        private const val MAX_BONE_TO_PALM_RATIO = 1.35f
        private const val MIN_PALM_WIDTH_PX = 0.001f
    }

    fun calculateNormalizedFingerDistance(landmarks: List<Point3D>): Float {
        if (landmarks.size <= PINKY_MCP) return 1.0f

        val indexMcp = landmarks[INDEX_MCP]
        val pinkyMcp = landmarks[PINKY_MCP]
        val indexTip = landmarks[INDEX_TIP]
        val middleTip = landmarks[MIDDLE_TIP]

        val palmWidth = indexMcp.distance2DTo(pinkyMcp).coerceAtLeast(MIN_PALM_WIDTH_PX)
        val fingertipDistance = indexTip.distance2DTo(middleTip)

        return fingertipDistance / palmWidth
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
