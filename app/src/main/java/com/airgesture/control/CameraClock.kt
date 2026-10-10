package com.airgesture.control

import kotlin.math.abs

/**
 * Which clock domain a camera frame timestamp (ImageInfo.timestamp, nanoseconds) is in.
 *
 * CameraX reports sensor timestamps either on the elapsed-realtime clock or on the
 * uptime clock depending on the device. Frame age and inter-frame gaps are only valid
 * when "now" is read from the SAME clock, so the domain is detected once per session.
 */
internal enum class FrameTimeSource {
    /** Camera timestamp, elapsed-realtime domain. */
    CAMERA_ELAPSED_REALTIME,

    /** Camera timestamp, uptime domain. */
    CAMERA_UPTIME,

    /** No usable camera timestamp: analyzer-start uptime is used (legacy behaviour). */
    ANALYZER_UPTIME
}

internal object CameraClock {
    /** A camera timestamp further than this from both clocks is not trusted. */
    const val MAX_PLAUSIBLE_AGE_MS = 10_000L

    fun detect(cameraTimestampNs: Long, uptimeMs: Long, elapsedRealtimeMs: Long): FrameTimeSource {
        if (cameraTimestampNs <= 0L) return FrameTimeSource.ANALYZER_UPTIME
        val cameraMs = cameraTimestampNs / 1_000_000L
        val elapsedDistance = abs(elapsedRealtimeMs - cameraMs)
        val uptimeDistance = abs(uptimeMs - cameraMs)
        return when {
            minOf(elapsedDistance, uptimeDistance) > MAX_PLAUSIBLE_AGE_MS -> FrameTimeSource.ANALYZER_UPTIME
            elapsedDistance <= uptimeDistance -> FrameTimeSource.CAMERA_ELAPSED_REALTIME
            else -> FrameTimeSource.CAMERA_UPTIME
        }
    }
}
