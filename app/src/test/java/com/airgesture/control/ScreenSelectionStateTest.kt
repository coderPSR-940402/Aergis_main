package com.airgesture.control
import org.junit.Assert.*
import org.junit.Test
class ScreenSelectionStateTest {
    @Test fun unrelatedUpdatesRetainSelectionButChangedIdentityOrEpochRevokesIt() {
        val state = ScreenSelectionState()
        state.move(listOf("target"), "window:1", true)
        repeat(3) { assertEquals("target", state.retain(listOf("target", "other"), "window:1")) }
        assertEquals("target", state.confirm(listOf("target"), "window:1"))
        assertNull(state.confirm(listOf("target"), "window:1"))
        state.move(listOf("target"), "window:1", true)
        assertNull(state.retain(listOf("replacement"), "window:1"))
        assertNull(state.confirm(listOf("target"), "window:1"))
        state.move(listOf("target"), "window:1", true)
        assertNull(state.retain(listOf("target"), "window:2"))
    }

    @Test fun navigationHighlightsOneItemAndConfirmationConsumesIt() {
        val state = ScreenSelectionState(); val keys = listOf("a", "b", "c")
        assertEquals("a", state.move(keys, "window:epoch", true))
        assertEquals("b", state.move(keys, "window:epoch", true))
        assertEquals("a", state.move(keys, "window:epoch", false))
        assertEquals("a", state.confirm(keys, "window:epoch"))
        assertNull(state.confirm(keys, "window:epoch"))
    }
    @Test fun staleMissingAndChangedWindowsCannotBeConfirmed() {
        val state = ScreenSelectionState()
        for (context in listOf("newWindow", "newEpoch")) {
            state.move(listOf("a"), "old", true)
            assertNull(state.confirm(listOf("a"), context))
        }
        state.move(listOf("a"), "old", true)
        assertNull(state.confirm(listOf("b"), "old"))
        assertNull(state.move(emptyList(), "old", false))
    }
    @Test fun duplicateIdentityCannotBeRetainedOrConfirmed() {
        val state = ScreenSelectionState()
        state.move(listOf("unique"), "window", true)
        org.junit.Assert.assertNull(state.retain(listOf("unique", "unique"), "window"))
        org.junit.Assert.assertNull(state.confirm(listOf("unique"), "window"))
    }
    @Test fun navigationSkipsAmbiguousRowsAndCanReachUniqueTargets() {
        val state = ScreenSelectionState()
        val keys = listOf("duplicate","duplicate","safe A","safe B")
        assertEquals("safe A",state.move(keys,"window",true))
        assertEquals("safe B",state.move(keys,"window",true))
        assertEquals("safe A",state.move(keys,"window",false))
        assertEquals("safe A",state.confirm(keys,"window"))
        assertNull(state.move(listOf("duplicate","duplicate"),"window",false))
    }
}
