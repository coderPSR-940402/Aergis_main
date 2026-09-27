package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlHandPreferenceTest {
    @Test
    fun enumValuesContainExpectedPreferences() {
        val values = ControlHandPreference.entries
        assertEquals(3, values.size)
        assertEquals(ControlHandPreference.LEFT, values[0])
        assertEquals(ControlHandPreference.RIGHT, values[1])
        assertEquals(ControlHandPreference.EITHER, values[2])
    }

    @Test
    fun enumValuesContainExpectedSelections() {
        val values = ControlHandSelection.entries
        assertEquals(2, values.size)
        assertEquals(ControlHandSelection.LEFT, values[0])
        assertEquals(ControlHandSelection.RIGHT, values[1])
    }
}
