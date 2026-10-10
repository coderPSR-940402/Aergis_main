package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs

class PrecisionPointerFilterTest {
    private fun filter(): (Float, Float, Long) -> PointerCoordinateMapper.Point {
        val type = runCatching { Class.forName("com.airgesture.control.PrecisionPointerFilter") }.getOrNull()
        assertNotNull("Missing precision pointer filter", type)
        val instance = type!!.getDeclaredConstructor().newInstance()
        val update = type.getDeclaredMethod("update", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Long::class.javaPrimitiveType)
        return { x, y, time -> update.invoke(instance, x, y, time) as PointerCoordinateMapper.Point }
    }

    @Test fun isolatedLargeSpikeDoesNotMoveThePointerButConfirmedTravelRecovers() {
        val update = filter()
        update(.2f, .5f, 1000)
        assertEquals(.2f, update(.85f, .9f, 1033).x, .0001f)
        assertEquals(.2f, update(.2f, .5f, 1066).x, .0001f)
        update(.8f, .5f, 1099)
        assertTrue(update(.8f, .5f, 1132).x > .7f)
        assertTrue(update(.8f, .5f, 1165).x > .76f)
    }

    @Test fun invalidAndOutOfOrderInputsCannotPoisonOrMoveThePointer() {
        val update = filter()
        update(.3f, .6f, 1000)
        for ((x, time) in listOf(Float.NaN to 1033L, Float.POSITIVE_INFINITY to 1066L,
            .9f to 1000L, .9f to 900L)) {
            val result = update(x, .6f, time)
            assertEquals(.3f, result.x, .0001f)
            assertTrue(result.y.isFinite())
        }
        assertEquals(.8f, update(.8f, .4f, 2000).x, .0001f)
    }

    @Test fun replayImprovesSteadyAimAndTravelResponseAcrossCameraRates() {
        val evidence = mutableListOf<String>()
        for (fps in listOf(15, 30, 60)) {
            val update = filter()
            val legacy = GestureInterpreter()
            var currentSquared = 0.0; var precisionSquared = 0.0; var n = 0
            for (i in 0..fps * 4) {
                val t = 1000L + i * 1000L / fps
                val measured = .5f + .006f * sin(i * 1.7f)
                val current = legacy.processFrame(hand(measured), t, false)!!
                val candidate = update(measured, .5f, t)
                if (i > fps) {
                    currentSquared += (current.smoothedX - .5).let { it * it }
                    precisionSquared += (candidate.x - .5).let { it * it }; n++
                }
            }
            val jitterRatio = sqrt(precisionSquared / n) / sqrt(currentSquared / n)
            assertTrue("$fps fps stationary jitter ratio $jitterRatio", jitterRatio < .90)
            val travelUpdate = filter()
            val travelLegacy = GestureInterpreter()
            var currentError = 0.0; var precisionError = 0.0; var samples = 0
            for (i in 0..fps * 2) {
                val measured = .15f + .7f * i / (fps * 2)
                val t = 1000L + i * 1000L / fps
                val current = travelLegacy.processFrame(hand(measured), t, false)!!
                val candidate = travelUpdate(measured, .5f, t)
                if (i > fps / 2) {
                    currentError += abs(current.smoothedX - measured)
                    precisionError += abs(candidate.x - measured); samples++
                }
                assertTrue("No prediction overshoot", candidate.x <= measured + .00001f)
            }
            val responseRatio = precisionError / currentError
            assertTrue("$fps fps travel error ratio $responseRatio", responseRatio < .55)
            evidence += "{\"fps\":$fps,\"stationaryJitterRatio\":$jitterRatio,\"meanCurrentTravelError\":${currentError / samples},\"meanPrecisionTravelError\":${precisionError / samples}}"
        }
        val file = java.io.File("build/reports/testing-tools/precision-benchmark.json")
        file.parentFile!!.mkdirs()
        file.writeText("""{"sourceCommit":"${BuildConfig.SOURCE_COMMIT}","evidenceType":"REPOSITORY_COMPLETE","scope":"Synthetic target replay, not camera/display latency","runs":[${evidence.joinToString()}]}""")
    }

    @Test fun coherentFineAimMustNotPayForStrongStationarySmoothing() {
        for (fps in listOf(15, 30, 60)) {
            val update = filter()
            val legacy = GestureInterpreter()
            var currentError = 0.0; var precisionError = 0.0
            for (i in 0..fps * 4) {
                val measured = .3f + .03f * i / fps
                val time = 1000L + i * 1000L / fps
                val current = legacy.processFrame(hand(measured), time, false)!!
                val candidate = update(measured, .5f, time)
                if (i > fps) {
                    currentError += abs(current.smoothedX - measured)
                    precisionError += abs(candidate.x - measured)
                }
            }
            assertTrue("$fps fps fine aiming ratio ${precisionError / currentError}", precisionError < currentError * .75)
        }
    }

    @Test fun fastContinuousMovementCannotRemainTrappedBehindJumpGuard() {
        val update = filter()
        update(.1f, .5f, 1000)
        update(.4f, .5f, 1033)
        assertTrue(update(.7f, .5f, 1066).x > .6f)
    }

    @Test fun reversingMotionAndUnevenFramesNeverExtrapolatePastTheMeasuredTarget() {
        val update = filter()
        var previous = update(.2f, .5f, 1000)
        var time = 1000L
        for (i in 1..100) {
            time += listOf(16L, 33L, 67L, 24L)[i % 4]
            val target = if (i < 50) .2f + i * .01f else .7f - (i - 50) * .01f
            val output = update(target, .5f, time)
            assertTrue(output.x in minOf(previous.x, target)..maxOf(previous.x, target))
            previous = output
        }
    }

    private fun hand(x: Float) = MutableList(21) { Point3D(.5f, .5f) }.apply { this[8] = Point3D(x, .5f) }
}
