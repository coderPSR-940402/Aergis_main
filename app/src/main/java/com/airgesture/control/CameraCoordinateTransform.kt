package com.airgesture.control

import com.airgesture.control.filtering.Point3D

/** Coordinate boundary between MediaPipe's image-space output and upright app geometry. */
internal object CameraCoordinateTransform {
    fun toUpright(x: Float, y: Float, z: Float, rotationDegrees: Int): Point3D =
        Point3D(x, y, z)
}
