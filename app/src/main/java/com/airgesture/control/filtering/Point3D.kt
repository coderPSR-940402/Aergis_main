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
