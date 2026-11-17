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
        val replaced: T?
        val schedule: Boolean
        synchronized(lock) {
            if (closed) {
                replaced = frame
                schedule = false
            } else {
                replaced = pending
                pending = frame
                schedule = !scheduled
                scheduled = true
            }
        }
        // Detach first. Release/report callbacks must not hold the queue lock or
        // prevent the newest frame from replacing one that is no longer needed.
        release(replaced)
        if (schedule) {
            try { executor.execute(::drain) }
            catch (failure: RuntimeException) {
                close()
                reportFailure(failure)
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
            catch (failure: Exception) { reportFailure(failure) }
            finally { release(frame) }
        }
    }

    private fun release(frame: T?) {
        try { frame?.close() }
        catch (failure: Exception) { reportFailure(failure) }
    }

    private fun reportFailure(failure: Throwable) {
        // A diagnostic failure cannot disable capture or strand owned pixels.
        try { onFailure(failure) } catch (_: Exception) { /* keep draining */ }
    }

    override fun close() {
        val frame = synchronized(lock) {
            closed = true
            pending.also { pending = null }
        }
        release(frame)
    }
}
