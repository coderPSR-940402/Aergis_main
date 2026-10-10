package com.airgesture.control

import com.airgesture.control.filtering.Point3D

/**
 * MediaPipe projects task landmarks back into the unrotated input image.
 * CameraX rotation is clockwise; apply it before filtering or interpreting motion.
 * Front-camera mirroring belongs to PointerCoordinateMapper, after this conversion.
 */
internal object CameraCoordinateTransform {
    fun uprightAspectRatio(width: Int, height: Int, rotationDegrees: Int): Float {
        require(width > 0 && height > 0)
        return when (rotationDegrees) {
            0, 180 -> width.toFloat() / height
            90, 270 -> height.toFloat() / width
            else -> throw IllegalArgumentException("Unsupported camera rotation: $rotationDegrees")
        }
    }

    fun toUpright(x: Float, y: Float, z: Float, rotationDegrees: Int): Point3D =
        when (rotationDegrees) {
            0 -> Point3D(x, y, z)
            90 -> Point3D(1f - y, x, z)
            180 -> Point3D(1f - x, 1f - y, z)
            270 -> Point3D(y, 1f - x, z)
            else -> throw IllegalArgumentException("Unsupported camera rotation: $rotationDegrees")
        }
}
