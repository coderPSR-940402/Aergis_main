package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin
import kotlin.math.sqrt

class PrecisionMotionNoiseTest {
    @Test fun travelOnOneAxisDoesNotOpenTheNoiseGateOnTheOtherAxis() {
        for (fps in listOf(12, 15, 30, 60)) for (swap in listOf(false, true)) {
            val filter = PrecisionPointerFilter()
            var rawEnergy = 0.0
            var filteredEnergy = 0.0
            var travelError = 0.0
            var samples = 0
            for (i in 0..fps * 4) {
                val travel = .3f + .06f * i / fps
                val noise = .006f * sin(i * 1.7f)
                val point = if (swap) filter.update(.5f + noise, travel, 1000L + i * 1000L / fps)
                    else filter.update(travel, .5f + noise, 1000L + i * 1000L / fps)
                if (i > fps) {
                    val quiet = if (swap) point.x else point.y
                    val moving = if (swap) point.y else point.x
                    rawEnergy += noise.toDouble() * noise
                    filteredEnergy += (quiet - .5).let { it * it }
                    travelError += kotlin.math.abs(moving - travel)
                    samples++
                }
            }
            assertTrue("$fps fps, swap=$swap transverse RMS ratio ${sqrt(filteredEnergy / rawEnergy)}",
                sqrt(filteredEnergy / rawEnergy) < .45)
            assertTrue("Fine travel must stay responsive", travelError / samples < .012)
        }
    }

    @Test fun filteredRefitsCannotMoveAnApproachingOrConsumedClickAnchor() {
        val filter = PrecisionPointerFilter()
        val anchor = PointerPinchController()
        val first = filter.update(.3f, .5f, 1000)
        anchor.update(first, false, false, 1000)
        for (i in 1..30) {
            val time = 1000L + i * 33
            val candidate = filter.update(.3f + i * .01f, .5f + .006f * sin(i * 1.7f), time)
            assertEquals(first, anchor.update(candidate, true, i > 3, time).point)
        }
    }
}
