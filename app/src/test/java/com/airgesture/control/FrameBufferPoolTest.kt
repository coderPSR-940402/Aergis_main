package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class FrameBufferPoolTest {
    private fun pool(): Any {
        val type = runCatching { Class.forName("com.airgesture.control.FrameBufferPool") }.getOrNull()
        assertNotNull("Capture needs bounded reusable storage independent of active inference", type)
        return type!!.getConstructor().newInstance()
    }
    private fun acquire(pool: Any, size: Int): Any? = pool.javaClass.getMethod("acquire", Int::class.javaPrimitiveType).invoke(pool, size)
    private fun buffer(lease: Any): ByteBuffer = lease.javaClass.getMethod("getBuffer").invoke(lease) as ByteBuffer

    @Test fun threeLeasesBoundMemoryAndNeverOverwriteActivePixels() {
        val pool = pool()
        val active = acquire(pool, 8)!!; val waiting = acquire(pool, 8)!!; val copying = acquire(pool, 8)!!
        buffer(active).put(0, 42)
        assertNull(acquire(pool, 8))
        (waiting as AutoCloseable).close(); waiting.close()
        val replacement = acquire(pool, 8)!!
        assertSame(buffer(waiting), buffer(replacement))
        assertNull("Double release must not create extra capacity", acquire(pool, 8))
        buffer(replacement).put(0, 7)
        assertEquals(42, buffer(active).get(0).toInt())
        (active as AutoCloseable).close(); (copying as AutoCloseable).close(); (replacement as AutoCloseable).close()
    }

    @Test fun alternatingResolutionsReuseMatchingFreeBuffers() {
        val pool = pool()
        val small = acquire(pool, 8)!!
        val large = acquire(pool, 16)!!
        val smallBuffer = buffer(small)
        val largeBuffer = buffer(large)
        (small as AutoCloseable).close()
        (large as AutoCloseable).close()

        val reusedLarge = acquire(pool, 16)!!
        val reusedSmall = acquire(pool, 8)!!
        assertSame(largeBuffer, buffer(reusedLarge))
        assertSame(smallBuffer, buffer(reusedSmall))
        val third = acquire(pool, 8)!!
        assertNull(acquire(pool, 8))
        (third as AutoCloseable).close()
        (reusedLarge as AutoCloseable).close()
        (reusedSmall as AutoCloseable).close()
    }

    @Test fun repeatedResolutionChangesDoNotRetainUnlimitedFreeBuffers() {
        val pool = pool()
        val first = acquire(pool, 8)!!
        val second = acquire(pool, 16)!!
        val third = acquire(pool, 24)!!
        val firstBuffer = buffer(first)
        (first as AutoCloseable).close()
        (second as AutoCloseable).close()
        (third as AutoCloseable).close()

        val fourth = acquire(pool, 32)!!
        (fourth as AutoCloseable).close()
        // The oldest free buffer must have been evicted after a fourth size.
        val replacement = acquire(pool, 8)!!
        assertTrue(firstBuffer !== buffer(replacement))
        (replacement as AutoCloseable).close()
    }

    @Test fun newResolutionGetsCorrectSizedStorageWithoutChangingOldLease() {
        val pool = pool(); val old = acquire(pool, 8)!!
        buffer(old).put(0, 42)
        val resized = acquire(pool, 16)!!
        assertEquals(16, buffer(resized).capacity())
        assertEquals(8, buffer(old).capacity()); assertEquals(42, buffer(old).get(0).toInt())
        (old as AutoCloseable).close(); (resized as AutoCloseable).close()
    }

    @Test fun equalContentsAtDifferentResolutionsDoNotAliasActiveBuffers() {
        verifyDistinctLeasesForEqualBuffers(exhausted = false)
    }

    @Test fun exhaustedBuffersAtDifferentResolutionsDoNotAliasActiveBuffers() {
        verifyDistinctLeasesForEqualBuffers(exhausted = true)
    }

    private fun verifyDistinctLeasesForEqualBuffers(exhausted: Boolean) {
        val pool = pool()
        val small = acquire(pool, 8)!!
        val large = acquire(pool, 16)!!
        val smallBuffer = buffer(small)
        val largeBuffer = buffer(large)
        // ByteBuffer equality compares remaining bytes, not object identity or capacity.
        if (exhausted) {
            smallBuffer.position(smallBuffer.limit())
            largeBuffer.position(largeBuffer.limit())
        } else {
            smallBuffer.put(ByteArray(8)).flip()
            largeBuffer.put(ByteArray(8)).flip()
        }
        assertEquals("Reproduce equal buffers with different capacities", smallBuffer, largeBuffer)
        (small as AutoCloseable).close()
        (large as AutoCloseable).close()

        val first = acquire(pool, 16)!!
        val second = acquire(pool, 16)!!
        val third = acquire(pool, 8)!!
        try {
            assertSame("Reuse the matching resolution", largeBuffer, buffer(first))
            assertNotSame("Active camera pixels must never be leased twice", buffer(first), buffer(second))
            assertSame("Keep the unmatched resolution available for reuse", smallBuffer, buffer(third))
            buffer(first).put(0, 42)
            buffer(second).put(0, 7)
            assertEquals("Another lease must not overwrite active pixels", 42, buffer(first).get(0).toInt())
            assertNull("The existing three-lease bound must hold", acquire(pool, 8))
        } finally {
            (first as AutoCloseable).close()
            (second as AutoCloseable).close()
            (third as AutoCloseable).close()
        }
    }

    @Test fun cachedAndLeasedStorageShareOneThreeBufferBudget() {
        val pool = pool()
        val original = listOf(8, 16, 24).map { acquire(pool, it)!! }
        original.forEach { (it as AutoCloseable).close() }
        val active = mutableListOf<Any>()
        try {
            for (size in listOf(32, 40, 48)) {
                active += acquire(pool, size)!!
                val free = pool.javaClass.getDeclaredField("free").apply { isAccessible = true }
                    .get(pool) as Collection<*>
                assertTrue("Resolution changes must not retain three cached buffers plus active storage",
                    free.size + active.size <= 3)
            }
            assertNull(acquire(pool, 64))
        } finally {
            active.forEach { (it as AutoCloseable).close() }
        }
    }

}
