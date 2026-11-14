package com.airgesture.control

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/** At most three pixel buffers: active inference, newest pending frame, and capture copy. */
internal class FrameBufferPool(private val direct: Boolean = true) {
    private val free = ArrayDeque<ByteBuffer>()
    private var leased = 0
    private val maxCachedBuffers = 3

    @Synchronized fun acquire(size: Int): Lease? {
        require(size > 0)
        if (leased == 3) return null
        // Match the requested resolution rather than discarding a different-sized
        // buffer. Camera rebinds can alternate resolutions; retain both for reuse.
        val reusable = free.firstOrNull { it.capacity() == size }
        if (reusable != null) free.remove(reusable)
        val buffer = reusable
            ?: if (direct) ByteBuffer.allocateDirect(size) else ByteBuffer.allocate(size)
        buffer.clear()
        leased++
        return Lease(buffer) { synchronized(this) {
            leased--
            free.addLast(it)
            // Repeated camera rebinds at different resolutions must not retain
            // an unbounded number of direct buffers after their leases end.
            while (free.size > maxCachedBuffers) free.removeFirst()
        } }
    }

    class Lease(val buffer: ByteBuffer, private val release: (ByteBuffer) -> Unit) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        override fun close() { if (closed.compareAndSet(false, true)) release(buffer) }
    }
}
