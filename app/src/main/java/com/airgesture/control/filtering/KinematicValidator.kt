package com.airgesture.control.filtering

import kotlin.math.abs

class KinematicValidator {

    companion object {
        const val WRIST = 0
        const val THUMB_TIP = 4
        const val INDEX_MCP = 5
        const val INDEX_TIP = 8
        const val MIDDLE_MCP = 9
        const val MIDDLE_TIP = 12
        const val PINKY_MCP = 17

        private const val MAX_BONE_TO_PALM_RATIO = 1.35f
        private const val MIN_PALM_WIDTH_PX = 0.001f
    }

    fun calculateNormalizedFingerDistance(landmarks: List<Point3D>, aspectRatio: Float = 1f): Float {
        if (!aspectRatio.isFinite() || aspectRatio <= 0f) return Float.NaN
        if (landmarks.size <= PINKY_MCP) return 1.0f

        val thumbTip = landmarks[THUMB_TIP]
        val middleTip = landmarks[MIDDLE_TIP]

        // A turned hand can collapse the projected index-to-pinky width although
        // the wrist-to-middle-base axis remains visible. Use the larger of two
        // independent palm axes so a real thumb-middle contact does not require
        // physically pushing the fingertips beyond each other. The fingertip
        // positions never contribute to their own normalization.
        //
        // Keep the separate depth-distance veto based on transverse palm width:
        // estimated z must not turn projected overlap into an unintended click.
        val palmScale = projectedPalmScale(landmarks, aspectRatio)
            .coerceAtLeast(MIN_PALM_WIDTH_PX)
        return thumbTip.distance2DTo(middleTip, aspectRatio) / palmScale
    }

    /** Palm scale derived only from stable anchors, independent of finger closure.
     * Unreliable, out-of-frame longitudinal anchors cannot enlarge the click range.
     */
    fun projectedPalmScale(landmarks: List<Point3D>, aspectRatio: Float = 1f): Float {
        if (landmarks.size <= PINKY_MCP || !aspectRatio.isFinite() || aspectRatio <= 0f) return Float.NaN
        val transverse = landmarks[INDEX_MCP].distance2DTo(landmarks[PINKY_MCP], aspectRatio)
        val wrist = landmarks[WRIST]
        val middleBase = landmarks[MIDDLE_MCP]
        fun usable(p: Point3D): Boolean = p.x.isFinite() && p.y.isFinite() && p.z.isFinite() &&
            p.x in -.25f..1.25f && p.y in -.25f..1.25f
        val longitudinal = if (usable(wrist) && usable(middleBase))
            wrist.distance2DTo(middleBase, aspectRatio) * .7f else 0f
        return maxOf(transverse, longitudinal)
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
