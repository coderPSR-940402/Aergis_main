package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test

class LandmarkGestureRecognizerTest {
    @Test fun allElevenCustomPosesSurviveScaleMirrorAndInPlaneRotation() {
        for (gesture in RegisteredGesture.entries.filter { it.isLandmarkPose && !it.isDirectionalGun }) {
            for (scale in listOf(.5f, 1f)) for (mirror in listOf(-1f, 1f)) for (turn in 0..3) {
                val points = pose(gesture).map {
                    var x = (it.x - .5f) * scale * mirror
                    var y = (it.y - .5f) * scale
                    repeat(turn) { val old = x; x = -y; y = old }
                    Point3D(.5f + x, .5f + y)
                }
                assertEquals("$gesture scale=$scale mirror=$mirror turn=$turn", gesture.signalName,
                    LandmarkGestureRecognizer.recognize(points, 1f)?.name)
            }
        }
    }

    @Test fun modestYawAndPitchPreserveDistinctCandidatePoses() {
        val angle = Math.toRadians(20.0)
        for (gesture in RegisteredGesture.entries.filter { it.isLandmarkPose && !it.isDirectionalGun }) {
            for (yaw in listOf(false, true)) for (sign in listOf(-1f, 1f)) {
                val points = pose(gesture).map {
                    val x=it.x-.5f; val y=it.y-.5f
                    if(yaw) Point3D(.5f+x*kotlin.math.cos(angle).toFloat(),it.y,sign*x*kotlin.math.sin(angle).toFloat())
                    else Point3D(it.x,.5f+y*kotlin.math.cos(angle).toFloat(),sign*y*kotlin.math.sin(angle).toFloat())
                }
                assertEquals("$gesture yaw=$yaw sign=$sign",gesture.signalName,LandmarkGestureRecognizer.recognize(points,1f)?.name)
            }
        }
    }

    @Test fun malformedAndAmbiguousHandsDoNotCreateCustomCommands() {
        val valid = pose(RegisteredGesture.FOUR_FINGERS)
        assertNull(LandmarkGestureRecognizer.recognize(valid.take(20), 1f))
        assertNull(LandmarkGestureRecognizer.recognize(valid, Float.NaN))
        assertNull(LandmarkGestureRecognizer.recognize(List(21) { Point3D(.5f, .5f) }, 1f))
        for (bad in listOf(Point3D(Float.NaN, .5f), Point3D(1.1f, .5f), Point3D(.5f, .5f, Float.NaN))) {
            assertNull(LandmarkGestureRecognizer.recognize(valid.toMutableList().apply { this[12] = bad }, 1f))
        }
        // A pointing finger or closed fist remains the built-in classifier's responsibility.
        assertNull(LandmarkGestureRecognizer.recognize(pose(null), 1f))
    }

    @Test fun physicalAspectCorrectionPreservesPose() {
        val p = pose(RegisteredGesture.SHAKA)
        val wide = p.map { Point3D(.5f + (it.x - .5f) / 2f, it.y) }
        assertEquals(RegisteredGesture.SHAKA.signalName, LandmarkGestureRecognizer.recognize(wide, 2f)?.name)
    }

    @Test fun unusedDistalJointsDoNotVetoAnyCustomPose() {
        for (gesture in RegisteredGesture.entries.filter { it.isLandmarkPose && !it.isDirectionalGun }) {
            val points = pose(gesture).toMutableList()
            for (joint in listOf(3, 7, 11, 15, 19)) points[joint] = Point3D(Float.NaN, Float.NaN, Float.NaN)
            assertEquals(gesture.name, gesture.signalName, LandmarkGestureRecognizer.recognize(points, 1f)?.name)
        }
    }

    @Test fun tuckedThumbPoseDoesNotRequireUnusedThumbBaseAngles() {
        val points = pose(RegisteredGesture.HORNS).toMutableList()
        points[1] = Point3D(1.1f, .5f)
        points[2] = Point3D(Float.NaN, .5f)
        assertEquals(RegisteredGesture.HORNS.signalName, LandmarkGestureRecognizer.recognize(points, 1f)?.name)
    }

    companion object {
        fun pose(gesture: RegisteredGesture?): List<Point3D> {
            val p = MutableList(21) { Point3D(.5f, .75f) }
            p[0] = Point3D(.5f, .85f)
            val up = when (gesture) {
                RegisteredGesture.OK, RegisteredGesture.THREE_OUTER_FINGERS, RegisteredGesture.FOUR_INDEX_FOLDED -> listOf(false, true, true, true)
                RegisteredGesture.L_SHAPE -> listOf(true, false, false, false)
                RegisteredGesture.THUMB_INDEX_MIDDLE -> listOf(true, true, false, false)
                RegisteredGesture.HORNS -> listOf(true, false, false, true)
                RegisteredGesture.FOUR_LITTLE_FOLDED -> listOf(true, true, true, false)
                RegisteredGesture.THREE_FINGERS -> listOf(true, true, true, false)
                RegisteredGesture.FOUR_FINGERS -> listOf(true, true, true, true)
                RegisteredGesture.SHAKA, RegisteredGesture.PINKY_ONLY -> listOf(false, false, false, true)
                else -> listOf(false, false, false, false)
            }
            for ((i, mcp) in listOf(5, 9, 13, 17).withIndex()) {
                val x = .32f + .12f * i
                p[mcp] = Point3D(x, .6f)
                p[mcp + 1] = Point3D(x, .45f)
                p[mcp + 2] = Point3D(x, if (up[i]) .35f else .55f)
                p[mcp + 3] = Point3D(x, if (up[i]) .25f else .68f)
            }
            p[1] = Point3D(.3f, .75f); p[2] = Point3D(.23f, .65f); p[3] = Point3D(.2f, .58f)
            p[4] = when (gesture) {
                RegisteredGesture.SHAKA, RegisteredGesture.L_SHAPE, RegisteredGesture.THUMB_INDEX_MIDDLE,
                RegisteredGesture.FOUR_LITTLE_FOLDED, RegisteredGesture.FOUR_INDEX_FOLDED -> Point3D(.08f, .46f)
                RegisteredGesture.OK -> p[8]
                else -> Point3D(.46f, .62f)
            }
            return p
        }
    }
}
