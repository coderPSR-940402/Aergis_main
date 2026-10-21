package com.airgesture.control

import com.airgesture.control.gaze.GazeSampleClock
import org.junit.Assert.*
import org.junit.Test

class AuditRegressionTest {
    private fun key(text: CharSequence) = ScreenTargetKey.of("root/0", "button", "10,10,100,100", text, null)
    @Test fun changedTextWithTheSameHashCannotConfirmOldSelection() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val state=ScreenSelectionState()
        state.move(listOf(key("Aa")), "window:epoch", true)
        assertNull(state.confirm(listOf(key("BB")), "window:epoch"))
    }
    @Test fun freshTextObjectsWithSameContentRetainTargetIdentity() {
        val state=ScreenSelectionState()
        val original=key(StringBuilder("same label"))
        state.move(listOf(original), "window:epoch", true)
        assertEquals(original,state.confirm(listOf(key(StringBuilder("same label"))),"window:epoch"))
    }
    @Test fun gazeAgeIncludesHandInferenceAndFrameCopy() {
        assertEquals(700L,GazeSampleClock.capturedAt(1000L,300L))
        assertNull(GazeSampleClock.capturedAt(1000L,351L))
        assertNull(GazeSampleClock.capturedAt(1000L,-1L))
        assertNull(GazeSampleClock.capturedAt(100L,150L))
    }
}
