package com.airgesture.control

/**
 * Bounded, monotonic in-memory telemetry timeline.
 *
 * This class performs no disk, network, logcat or Android service work. Callers can therefore
 * add compact samples from latency-sensitive paths without granting diagnostics any control
 * authority or introducing blocking I/O.
 */
internal class LiveDiagnosticBuffer(
    private val maxSamples: Int,
    private val retentionMs: Long
) {
    private val samples = ArrayDeque<LiveDiagnosticSample>(maxSamples.coerceAtLeast(1))

    init {
        require(maxSamples > 0) { "maxSamples must be positive" }
        require(retentionMs >= 0L) { "retentionMs must be non-negative" }
    }

    fun add(sample: LiveDiagnosticSample) {
        val last = samples.lastOrNull()
        require(last == null || sample.uptimeMs >= last.uptimeMs) {
            "live diagnostic samples must be monotonic"
        }

        samples.addLast(sample)
        evictOlderThan(sample.uptimeMs - retentionMs)
        while (samples.size > maxSamples) samples.removeFirst()
    }

    fun snapshotBefore(eventUptimeMs: Long, windowMs: Long): List<LiveDiagnosticSample> {
        require(windowMs >= 0L) { "windowMs must be non-negative" }
        val start = eventUptimeMs - windowMs
        return samples.filter { it.uptimeMs in start..eventUptimeMs }
    }

    fun snapshotAfter(eventUptimeMs: Long, windowMs: Long): List<LiveDiagnosticSample> {
        require(windowMs >= 0L) { "windowMs must be non-negative" }
        val end = eventUptimeMs + windowMs
        return samples.filter { it.uptimeMs > eventUptimeMs && it.uptimeMs <= end }
    }

    fun all(): List<LiveDiagnosticSample> = samples.toList()

    private fun evictOlderThan(oldestAllowedUptimeMs: Long) {
        while (samples.firstOrNull()?.uptimeMs?.let { it < oldestAllowedUptimeMs } == true) {
            samples.removeFirst()
        }
    }
}
