package com.airgesture.control

import java.io.File
import java.util.UUID
import java.util.concurrent.Future
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Bounded non-blocking observation boundary; no caller executes storage or relay work. */
internal class DevLoopWorker(
    private val root: File,
    private val buildId: String,
    private val uptime: () -> Long,
    private val wallClock: () -> Long,
    private val relay: (String) -> Unit
) {
    private var session: DevLoopSession? = null // only touched by the shared serial worker
    private var accepting = false
    private val dropped = AtomicLong()
    private var timer: Future<*>? = null

    @Synchronized fun start() {
        if (accepting) return
        accepting = true
        executor.execute {
            runCatching {
                session = DevLoopSession(root, buildId, UUID.randomUUID().toString(), relay)
                session?.start(uptime(), wallClock())
            }
        }
        timer = executor.scheduleWithFixedDelay({
            runCatching {
                val lost = dropped.getAndSet(0)
                if (lost > 0) session?.invalidateEvidence(lost)
                session?.tick(uptime(), wallClock())
            }
        }, 100, 100, TimeUnit.MILLISECONDS)
    }

    @Synchronized fun observe(block: DevLoopSession.() -> Unit) {
        if (!accepting) return
        if (pending.incrementAndGet() > 1024) {
            pending.decrementAndGet()
            dropped.incrementAndGet()
            return
        }
        try {
            executor.execute {
                try {
                    val lost = dropped.getAndSet(0)
                    if (lost > 0) session?.invalidateEvidence(lost)
                    runCatching { session?.block() }
                } finally { pending.decrementAndGet() }
            }
        } catch (_: RuntimeException) {
            pending.decrementAndGet()
            dropped.incrementAndGet()
        }
    }

    @Synchronized fun stop(): Future<*> {
        accepting = false
        timer?.cancel(false)
        timer = null
        // Lifecycle work always follows already accepted observations, including callbacks.
        return executor.submit {
            runCatching {
                val lost = dropped.getAndSet(0)
                if (lost > 0) session?.invalidateEvidence(lost)
                session?.stop(uptime(), wallClock())
            }
            session = null
        }
    }

    companion object {
        // Shared across capture-service restarts: the previous finalization precedes a new start.
        private val pending = AtomicInteger()
        private val executor = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "aergis-devloop").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
