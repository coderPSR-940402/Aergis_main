package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class LatestFrameProcessorTest {
    private class Frame(val id: Int, val releaseFailure: Boolean = false) : AutoCloseable {
        var closes = 0
        override fun close() { closes++; if (releaseFailure) error("pixel release failed") }
    }
    private class Worker : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
    private class Processor(executor: Executor, consume: (Frame) -> Unit, failure: (Throwable) -> Unit = {}) {
        private val type = runCatching { Class.forName("com.airgesture.control.LatestFrameProcessor") }.getOrNull()
        init { assertNotNull("Camera capture needs a bounded latest-frame handoff independent of inference", type) }
        private val instance = type!!.constructors.single().newInstance(executor, consume, failure)
        fun offer(frame: Frame) { type!!.getMethod("offer", AutoCloseable::class.java).invoke(instance, frame) }
        fun close() { type!!.getMethod("close").invoke(instance) }
    }

    @Test fun busyWorkerConsumesOnlyNewestWaitingFrame() {
        val worker = Worker(); val seen = mutableListOf<Int>()
        val processor = Processor(worker, { seen.add(it.id) })
        val frames = (1..100).map(::Frame)
        frames.forEach(processor::offer)
        assertEquals("Only one drain task may be queued", 1, worker.tasks.size)
        assertTrue(frames.dropLast(1).all { it.closes == 1 })
        assertEquals(0, frames.last().closes)
        worker.drain()
        assertEquals(listOf(100), seen)
        assertTrue(frames.all { it.closes == 1 })
    }

    @Test fun activeFrameRemainsOwnedWhileNewerFramesReplacePending() {
        val worker = Worker(); val seen = mutableListOf<Int>()
        val first = Frame(1); val second = Frame(2); val third = Frame(3)
        lateinit var processor: Processor
        processor = Processor(worker, {
            seen.add(it.id)
            if (it === first) {
                processor.offer(second); processor.offer(third)
                assertEquals(0, first.closes)
                assertEquals(1, second.closes)
                assertEquals(0, third.closes)
            }
        })
        processor.offer(first); worker.drain()
        assertEquals(listOf(1, 3), seen)
        assertEquals(listOf(1, 1, 1), listOf(first.closes, second.closes, third.closes))
    }

    @Test fun stoppingDiscardsWaitingAndFutureFrames() {
        val worker = Worker(); val seen = mutableListOf<Int>()
        val processor = Processor(worker, { seen.add(it.id) })
        val waiting = Frame(1); val late = Frame(2)
        processor.offer(waiting); processor.close(); processor.close(); processor.offer(late)
        worker.drain()
        assertTrue(seen.isEmpty())
        assertEquals(1, waiting.closes); assertEquals(1, late.closes)
    }

    @Test fun stopDoesNotReleaseActivePixelsUntilInferenceReturns() {
        val worker = Worker(); val first = Frame(1); val waiting = Frame(2)
        lateinit var processor: Processor
        processor = Processor(worker, {
            processor.offer(waiting); processor.close()
            assertEquals(0, first.closes)
            assertEquals(1, waiting.closes)
        })
        processor.offer(first); worker.drain()
        assertEquals(1, first.closes)
    }

    @Test fun inferenceFailureReleasesPixelsAndAllowsRecovery() {
        val worker = Worker(); val failures = mutableListOf<Throwable>(); val seen = mutableListOf<Int>()
        val processor = Processor(worker, { if (it.id == 1) error("inference failed") else seen.add(it.id) }, failures::add)
        val bad = Frame(1); processor.offer(bad); worker.drain()
        val good = Frame(2); processor.offer(good); worker.drain()
        assertEquals(1, failures.size); assertEquals(listOf(2), seen)
        assertEquals(1, bad.closes); assertEquals(1, good.closes)
    }

    @Test fun failureReporterExceptionDoesNotStrandNextFrame() {
        val worker = Worker()
        val seen = mutableListOf<Int>()
        lateinit var processor: Processor
        val first = Frame(1)
        val next = Frame(2)
        processor = Processor(worker, {
            if (it.id == 1) {
                processor.offer(next)
                error("inference failure")
            }
            seen.add(it.id)
        }, { throw IllegalStateException("reporter failure") })
        processor.offer(first)
        worker.drain()
        assertEquals(listOf(2), seen)
        assertEquals(1, first.closes)
        assertEquals(1, next.closes)
    }

    @Test fun unexpectedExecutorFailureReleasesWaitingFrame() {
        val failures = mutableListOf<Throwable>()
        val processor = Processor(Executor { throw IllegalStateException("worker unavailable") },
            { fail("Must not run") }, failures::add)
        val frame = Frame(1)
        processor.offer(frame)
        processor.close()
        assertEquals(1, frame.closes)
        assertEquals(1, failures.size)
        assertTrue(failures.single() is IllegalStateException)
    }

    @Test fun rejectedExecutorReleasesWaitingFrame() {
        val failures = mutableListOf<Throwable>()
        val processor = Processor(Executor { throw RejectedExecutionException("stopped") }, { fail("Must not run") }, failures::add)
        val frame = Frame(1); processor.offer(frame); processor.close()
        assertEquals(1, frame.closes); assertEquals(1, failures.size)
    }

    @Test fun activeReleaseFailureCannotStrandNewestWaitingFrame() {
        val worker = Worker(); val seen = mutableListOf<Int>(); val failures = mutableListOf<Throwable>()
        val first = Frame(1, releaseFailure = true); val next = Frame(2)
        lateinit var processor: Processor
        processor = Processor(worker, {
            seen += it.id
            if (it === first) processor.offer(next)
        }, failures::add)
        processor.offer(first); worker.drain()
        assertEquals(listOf(1, 2), seen)
        assertEquals(1, failures.size)
        assertEquals(1, first.closes); assertEquals(1, next.closes)
    }

    @Test fun replacedReleaseFailureCannotRejectNewerFrame() {
        val worker = Worker(); val seen = mutableListOf<Int>(); val failures = mutableListOf<Throwable>()
        val processor = Processor(worker, { seen += it.id }, failures::add)
        val old = Frame(1, releaseFailure = true); val newest = Frame(2)
        processor.offer(old); processor.offer(newest); worker.drain()
        assertEquals(listOf(2), seen)
        assertEquals(1, old.closes); assertEquals(1, newest.closes)
        assertEquals(1, failures.size)
    }

    @Test fun shutdownDetachesFailingPendingFrameBeforeReleasingIt() {
        val worker = Worker(); val failures = mutableListOf<Throwable>()
        val processor = Processor(worker, { fail("Stopped work cannot be consumed") }, failures::add)
        val pending = Frame(1, releaseFailure = true)
        processor.offer(pending); processor.close(); processor.close(); worker.drain()
        assertEquals(1, pending.closes)
        assertEquals(1, failures.size)
        val late = Frame(2, releaseFailure = true)
        processor.offer(late)
        assertEquals(1, late.closes)
        assertEquals(2, failures.size)
    }

    @Test fun workerRejectionCannotEscapeThroughTheFailureReporter() {
        val processor = Processor(Executor { throw RejectedExecutionException("worker stopped") },
            { fail("Must not consume") }, { error("reporter unavailable") })
        val first = Frame(1); val late = Frame(2)
        processor.offer(first); processor.offer(late); processor.close()
        assertEquals(1, first.closes); assertEquals(1, late.closes)
    }

    @Test fun releaseAndReporterFailuresTogetherCannotKillTheDrain() {
        val worker = Worker(); val seen = mutableListOf<Int>()
        val first = Frame(1, releaseFailure = true); val next = Frame(2)
        lateinit var processor: Processor
        processor = Processor(worker, {
            seen += it.id
            if (it === first) processor.offer(next)
        }, { error("reporter unavailable") })
        processor.offer(first); worker.drain()
        assertEquals(listOf(1, 2), seen)
        assertEquals(1, first.closes); assertEquals(1, next.closes)
    }

}
