package com.airgesture.control

import java.util.Locale

/**
 * Bounded, allocation-light timing statistics for the tracking pipeline. Each series is a
 * fixed ring; percentiles are computed only when a snapshot is requested (diagnostics),
 * never on the per-frame path. Times are milliseconds.
 */
internal class PipelineMetrics(private val window: Int = 240) {
    data class Stat(val p50: Long, val p95: Long, val max: Long)

    data class Snapshot(
        val frameAgeAtStart: Stat, val preprocess: Stat, val inference: Stat,
        val publish: Stat, val frameToPointer: Stat,
        val analyzerHz: Float, val inferenceHz: Float, val acceptedPointerHz: Float, val cursorUpdateHz: Float,
        val outOfOrderSkipped: Long, val rejections: Map<String, Long>
    ) {
        fun summary(): String = String.format(Locale.US,
            "frame→pointer p50/p95/max %d/%d/%d ms · infer %d/%d/%d · age@start %d/%d/%d · " +
                "Hz analyzer %.1f infer %.1f pointer %.1f cursor %.1f",
            frameToPointer.p50, frameToPointer.p95, frameToPointer.max,
            inference.p50, inference.p95, inference.max,
            frameAgeAtStart.p50, frameAgeAtStart.p95, frameAgeAtStart.max,
            analyzerHz, inferenceHz, acceptedPointerHz, cursorUpdateHz)
    }

    private class Series(size: Int) {
        private val values = LongArray(size)
        private var next = 0
        private var count = 0
        fun add(v: Long) { values[next] = v; next = (next + 1) % values.size; if (count < values.size) count++ }
        fun stat(): Stat {
            if (count == 0) return Stat(0, 0, 0)
            val copy = values.copyOf(count).also { it.sort() }
            return Stat(copy[(count - 1) / 2], copy[((count - 1) * 95) / 100], copy[count - 1])
        }
        fun clear() { next = 0; count = 0 }
    }

    /** Event-rate estimate from the timestamps of the most recent events. */
    private class Rate(size: Int) {
        private val times = LongArray(size)
        private var next = 0
        private var count = 0
        fun mark(nowMs: Long) { times[next] = nowMs; next = (next + 1) % times.size; if (count < times.size) count++ }
        fun hz(nowMs: Long): Float {
            if (count < 2) return 0f
            val newest = times[(next - 1 + times.size) % times.size]
            if (nowMs - newest > STALE_AFTER_MS) return 0f
            val oldest = times[(next - count + times.size) % times.size]
            val span = newest - oldest
            return if (span <= 0L) 0f else (count - 1) * 1000f / span
        }
        fun clear() { next = 0; count = 0 }
    }

    private val frameAge = Series(window)
    private val preprocess = Series(window)
    private val inference = Series(window)
    private val publish = Series(window)
    private val total = Series(window)
    private val analyzer = Rate(64)
    private val results = Rate(64)
    private val accepted = Rate(64)
    private val cursor = Rate(64)
    private val rejections = HashMap<String, Long>()
    private var outOfOrder = 0L

    @Synchronized fun recordAnalyzerFrame(nowMs: Long) = analyzer.mark(nowMs)

    @Synchronized fun recordFrame(nowMs: Long, frameAgeAtStartMs: Long, preprocessMs: Long,
        inferenceMs: Long, publishMs: Long, frameToPointerMs: Long) {
        results.mark(nowMs)
        frameAge.add(frameAgeAtStartMs.coerceAtLeast(0L))
        preprocess.add(preprocessMs.coerceAtLeast(0L))
        inference.add(inferenceMs.coerceAtLeast(0L))
        publish.add(publishMs.coerceAtLeast(0L))
        total.add(frameToPointerMs.coerceAtLeast(0L))
    }

    @Synchronized fun recordAcceptedPointer(nowMs: Long) = accepted.mark(nowMs)
    @Synchronized fun recordCursorUpdate(nowMs: Long) = cursor.mark(nowMs)
    @Synchronized fun recordOutOfOrderSkip() { outOfOrder++ }
    @Synchronized fun recordRejection(reason: String) { rejections[reason] = (rejections[reason] ?: 0L) + 1L }

    @Synchronized fun snapshot(nowMs: Long) = Snapshot(
        frameAge.stat(), preprocess.stat(), inference.stat(), publish.stat(), total.stat(),
        analyzer.hz(nowMs), results.hz(nowMs), accepted.hz(nowMs), cursor.hz(nowMs),
        outOfOrder, HashMap(rejections))

    @Synchronized fun reset() {
        frameAge.clear(); preprocess.clear(); inference.clear(); publish.clear(); total.clear()
        analyzer.clear(); results.clear(); accepted.clear(); cursor.clear()
        rejections.clear(); outOfOrder = 0L
    }

    companion object {
        private const val STALE_AFTER_MS = 2_000L
        /** Shared by the single analysis pipeline; diagnostics read snapshots from it. */
        val shared = PipelineMetrics()
    }
}
