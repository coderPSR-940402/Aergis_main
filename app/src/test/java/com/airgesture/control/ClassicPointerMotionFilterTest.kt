package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class ClassicPointerMotionFilterTest {
    private fun filter(): Any {
        val type = runCatching { Class.forName("com.airgesture.control.ClassicPointerMotionFilter") }.getOrNull()
        assertNotNull("The actual recovery-branch smoothing must be available", type)
        return type!!.getDeclaredConstructor().newInstance()
    }
    @Suppress("UNCHECKED_CAST")
    private fun update(filter: Any, x: Float, y: Float, at: Long, age: Long = 0L): Pair<Float, Float> =
        filter.javaClass.getDeclaredMethod("update", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Long::class.javaPrimitiveType, Float::class.javaPrimitiveType, Long::class.javaPrimitiveType)
            .invoke(filter, x, y, at, 1f, age) as Pair<Float, Float>

    @Test fun stationaryNoiseIsDampedButDeliberateTravelResponds() {
        val f = filter()
        update(f, .5f, .5f, 1000L)
        assertTrue(kotlin.math.abs(update(f, .501f, .5f, 1033L).first - .5f) < .0003f)
        assertTrue(update(f, .7f, .5f, 1066L).first > .68f)
    }
    @Test fun invalidAndOutOfOrderSamplesCannotCorruptOutput() {
        val f = filter()
        val initial = update(f, .4f, .5f, 1000L)
        assertEquals(initial, update(f, .9f, .8f, 1000L))
        assertEquals(initial, update(f, Float.NaN, Float.POSITIVE_INFINITY, 1033L))
        val next = update(f, .42f, .5f, 1066L, 300L)
        assertTrue(next.first.isFinite() && next.first in 0f..1f && next.second in 0f..1f)
        assertTrue(next.first <= .455f)
    }
    @Test fun classicCanBeSelectedWithoutChangingPrecisionDefault() {
        assertNotNull(PointerFilterMode.entries.firstOrNull { it.name == "CLASSIC" })
        val f = filter()
        update(f, .9f, .8f, 1000L)
        f.javaClass.getDeclaredMethod("reset").invoke(f)
        assertEquals(.2f to .3f, update(f, .2f, .3f, 2000L))
    }
}
