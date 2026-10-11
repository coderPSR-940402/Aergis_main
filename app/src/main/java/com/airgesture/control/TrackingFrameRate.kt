package com.airgesture.control

import android.util.Range

/** Choose only ranges advertised for the actual analysis session. */
internal object TrackingFrameRate {
    fun select(supported: Set<Range<Int>>): Range<Int>? = supported
        .filter { it.lower > 0 && it.upper <= 60 }
        .maxWithOrNull(compareBy<Range<Int>> { it.upper }.thenBy { it.lower })
}
