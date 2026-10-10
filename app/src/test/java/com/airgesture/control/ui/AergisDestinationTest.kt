package com.airgesture.control.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AergisDestinationTest {
    @Test fun primaryNavigationIsHomeLiveControlsMore() {
        assertEquals(listOf("HOME", "LIVE", "CONTROLS", "MORE"), AergisDestination.entries.map { it.name })
    }
}
