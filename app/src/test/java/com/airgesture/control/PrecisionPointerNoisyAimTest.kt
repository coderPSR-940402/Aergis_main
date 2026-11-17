package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Synthetic position-error regressions; these do not measure camera or display latency. */
class PrecisionPointerNoisyAimTest {
    @Test fun fineAimRemainsResponsiveWhenCameraNoiseBreaksPerFrameDirection() {
        val failures = mutableListOf<String>()
        val evidence = mutableListOf<String>()
        for (fps in listOf(15, 30, 60)) for (speed in listOf(.01f, .03f)) {
            val filter = PrecisionPointerFilter()
            var squaredError = 0.0
            var samples = 0
            for (i in 0..fps * 4) {
                val target = .3f + speed * i / fps
                val measured = target + .002f * sin(i * 1.7f)
                val point = filter.update(measured, .5f, 1000L + i * 1000L / fps)
                if (i > fps) {
                    squaredError += (point.x - target).toDouble().let { it * it }
                    samples++
                }
            }
            val rms = sqrt(squaredError / samples)
            evidence += "{\"fps\":$fps,\"speed\":$speed,\"targetErrorRms\":$rms}"
            if (rms >= .0035) failures += "$fps fps / $speed screens per second: RMS error $rms"
        }
        val report = java.io.File("build/reports/testing-tools/noisy-fine-aim.json")
        report.parentFile!!.mkdirs()
        report.writeText("""{"sourceCommit":"${BuildConfig.SOURCE_COMMIT}","scope":"Synthetic noisy-target replay; not physical-device performance","runs":[${evidence.joinToString()}]}""")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun horizontalFineAimDoesNotOpenVerticalStationaryNoise() {
        for (fps in listOf(15, 30, 60)) {
            val filter = PrecisionPointerFilter()
            var inputEnergy = 0.0
            var outputEnergy = 0.0
            for (i in 0..fps * 5) {
                val noise = .006f * sin(i * 1.7f)
                val point = filter.update(.3f + .03f * i / fps + .002f * sin(i * 2.3f),
                    .5f + noise, 1000L + i * 1000L / fps)
                if (i > fps) {
                    inputEnergy += noise.toDouble() * noise
                    outputEnergy += (point.y - .5).let { it * it }
                }
            }
            assertTrue("$fps fps orthogonal jitter ratio ${sqrt(outputEnergy / inputEnergy)}",
                sqrt(outputEnergy / inputEnergy) < .45)
        }
    }

    @Test fun noisyFineReversalsAtUnevenCadenceStayWithinTheObservedSegment() {
        val filter = PrecisionPointerFilter()
        var timestamp = 1000L
        var previous = filter.update(.3f, .5f, timestamp)
        for (i in 1..180) {
            timestamp += listOf(16L, 24L, 50L, 83L)[i % 4]
            val elapsed = (timestamp - 1000L) / 1000f
            val target = .3f + .03f * (if (elapsed < 4f) elapsed else 8f - elapsed)
            val measured = target + .002f * sin(i * 1.7f)
            val point = filter.update(measured, .5f, timestamp)
            assertTrue("No prediction overshoot on noisy reversals",
                point.x in minOf(previous.x, measured)..maxOf(previous.x, measured))
            if (elapsed in 1f..3.5f || elapsed > 5f)
                assertTrue("Fine aim must recover after reversal, error=${abs(point.x - target)}",
                    abs(point.x - target) < .006f)
            previous = point
        }
    }

    @Test fun stationaryRandomNoiseDoesNotBecomeIntentionalMotion() {
        for (fps in listOf(15, 30, 60)) {
            val filter = PrecisionPointerFilter()
            val random = java.util.Random(17L)
            var inputEnergy = 0.0
            var outputEnergy = 0.0
            for (i in 0..fps * 20) {
                val noise = (random.nextFloat() * 2f - 1f) * .006f
                val point = filter.update(.5f + noise, .5f, 1000L + i * 1000L / fps)
                if (i > fps * 2) {
                    inputEnergy += noise.toDouble() * noise
                    outputEnergy += (point.x - .5).let { it * it }
                }
            }
            assertTrue("$fps fps random stationary jitter ratio ${sqrt(outputEnergy / inputEnergy)}",
                sqrt(outputEnergy / inputEnergy) < .45)
        }
    }
}
