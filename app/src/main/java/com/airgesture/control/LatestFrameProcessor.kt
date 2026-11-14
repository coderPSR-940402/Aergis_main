package com.airgesture.control

import java.util.concurrent.Executor

/** One active frame and one replaceable waiting frame; native work stays on one executor. */
internal class LatestFrameProcessor<T : AutoCloseable>(
    private val executor: Executor,
    private val consume: (T) -> Unit,
    private val onFailure: (Throwable) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private var pending: T? = null
    private var scheduled = false
    private var closed = false

    fun offer(frame: T) {
        val schedule = synchronized(lock) {
            if (closed) {
                frame.close()
                return
            }
            pending?.close()
            pending = frame
            if (scheduled) false else { scheduled = true; true }
        }
        if (schedule) {
            try { executor.execute(::drain) }
            catch (failure: RuntimeException) {
                close()
                onFailure(failure)
            }
        }
    }

    private fun drain() {
        while (true) {
            val frame = synchronized(lock) {
                val next = pending
                pending = null
                if (next == null) {
                    scheduled = false
                    return
                }
                next
            }
            try { consume(frame) }
            catch (failure: Exception) {
                // Diagnostic reporting must not strand a queued camera frame if
                // the reporter itself fails. The frame is still closed below.
                try { onFailure(failure) } catch (_: Exception) { /* keep draining */ }
            }
            finally { frame.close() }
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            pending?.close()
            pending = null
        }
    }
}
