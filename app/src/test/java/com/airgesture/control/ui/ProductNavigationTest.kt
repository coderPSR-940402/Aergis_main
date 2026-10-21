package com.airgesture.control.ui

import org.junit.Assert.*
import org.junit.Test

class ProductNavigationTest {
    @Test fun supportingPagesReturnToTheirOrigin() {
        val start = ProductNavigation(listOf(AergisDestination.SETTINGS.name))
        val precision = start.open(AergisDestination.PRECISION)
        val calibration = precision.open(AergisDestination.CALIBRATION)
        assertEquals(AergisDestination.PRECISION, calibration.back().current)
        assertEquals(AergisDestination.SETTINGS, calibration.back().back().current)
    }
    @Test fun primaryTabClearsNestedHistory() {
        val state = ProductNavigation().open(AergisDestination.PRECISION).open(AergisDestination.CALIBRATION)
            .open(AergisDestination.GESTURES)
        assertEquals(listOf(AergisDestination.GESTURES.name), state.routes)
        assertFalse(state.canGoBack)
    }
    @Test fun invalidRestoredRoutesFallBackToControl() {
        assertEquals(AergisDestination.CONTROL, ProductNavigation(listOf("retired-page")).current)
        assertEquals(AergisDestination.CONTROL, ProductNavigation(emptyList()).back().current)
    }
    @Test fun repeatedNavigationDoesNotDuplicateCurrentPage() {
        val state = ProductNavigation().open(AergisDestination.MOTION)
        assertEquals(state, state.open(AergisDestination.MOTION))
    }
}
