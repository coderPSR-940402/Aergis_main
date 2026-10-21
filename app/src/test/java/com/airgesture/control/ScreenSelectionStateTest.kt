package com.airgesture.control
import org.junit.Assert.*
import org.junit.Test
class ScreenSelectionStateTest {
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
}
