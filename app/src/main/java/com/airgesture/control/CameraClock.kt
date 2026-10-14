package com.airgesture.control

/** Camera2 guarantees cross-clock comparison only for a declared REALTIME source. */
internal enum class FrameTimeSource {
    /** Camera timestamp, elapsed-realtime domain. */
    CAMERA_ELAPSED_REALTIME,

    /** Legacy recordings only; UNKNOWN sensor clocks must not be guessed as uptime. */
    CAMERA_UPTIME,

    /** Frame age since capture callback, not sensor exposure; sensor intervals are retained. */
    ANALYZER_UPTIME
}

internal object CameraClock {
    @Suppress("UNUSED_PARAMETER")
    fun detect(cameraTimestampNs: Long, uptimeMs: Long, elapsedRealtimeMs: Long,
        realtimeSource: Boolean = false): FrameTimeSource =
        if (cameraTimestampNs > 0L && realtimeSource) FrameTimeSource.CAMERA_ELAPSED_REALTIME
        else FrameTimeSource.ANALYZER_UPTIME

    fun sampleTimestampMs(cameraTimestampNs: Long, receivedUptimeMs: Long): Long =
        if (cameraTimestampNs > 0L) cameraTimestampNs / 1_000_000L else receivedUptimeMs

    fun ageMs(source: FrameTimeSource, cameraTimestampNs: Long, receivedUptimeMs: Long,
        nowUptimeMs: Long, nowElapsedMs: Long): Long = when (source) {
        FrameTimeSource.CAMERA_ELAPSED_REALTIME -> nowElapsedMs - cameraTimestampNs / 1_000_000L
        else -> nowUptimeMs - receivedUptimeMs
    }
}
