package com.airgesture.control.gaze

/** Use exposure age, including hand inference/copy, not the later worker submission time. */
internal object GazeSampleClock {
    fun capturedAt(nowUptimeMs: Long, frameAgeMs: Long): Long? =
        if (frameAgeMs !in 0L..350L || nowUptimeMs < frameAgeMs) null else nowUptimeMs-frameAgeMs
}
