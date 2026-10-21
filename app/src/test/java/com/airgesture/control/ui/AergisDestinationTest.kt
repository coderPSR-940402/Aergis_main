package com.airgesture.control.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AergisDestinationTest {
    @Test fun primaryNavigationIsControlGesturesSettings() {
        assertEquals(listOf("CONTROL", "GESTURES", "SETTINGS"), AergisDestination.entries.filter { it.root }.map { it.name })
    }
}
