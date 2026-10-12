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

    @Test fun newResolutionGetsCorrectSizedStorageWithoutChangingOldLease() {
        val pool = pool(); val old = acquire(pool, 8)!!
        buffer(old).put(0, 42)
        val resized = acquire(pool, 16)!!
        assertEquals(16, buffer(resized).capacity())
        assertEquals(8, buffer(old).capacity()); assertEquals(42, buffer(old).get(0).toInt())
        (old as AutoCloseable).close(); (resized as AutoCloseable).close()
    }
}
