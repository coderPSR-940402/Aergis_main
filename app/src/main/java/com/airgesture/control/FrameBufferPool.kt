package com.airgesture.control

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** At most three pixel buffers: active inference, newest pending frame, and capture copy. */
internal class FrameBufferPool {
    private val free = ArrayDeque<ByteBuffer>()
    private var leased = 0

    @Synchronized fun acquire(size: Int): Lease? {
        require(size > 0)
        if (leased == 3) return null
        val reusable = if (free.isEmpty()) null else free.removeFirst()
        val buffer = reusable?.takeIf { it.capacity() == size } ?: ByteBuffer.allocateDirect(size)
        buffer.clear()
        leased++
        return Lease(buffer) { synchronized(this) { leased--; free.addLast(it) } }
    }

    class Lease(val buffer: ByteBuffer, private val release: (ByteBuffer) -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true)) release(buffer) }
    }
}
