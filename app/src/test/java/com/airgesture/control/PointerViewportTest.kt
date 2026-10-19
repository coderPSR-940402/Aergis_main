package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class PointerViewportTest {
    @Test fun recordedA54TopEdgeIsNotVisibleInInsetWindow() {
        assertFalse(PointerViewport.contains(678.4373f, 0f, 0, 80, 1080, 2260))
        assertFalse(PointerViewport.contains(678f, 79.99f, 0, 80, 1080, 2260))
        assertTrue(PointerViewport.contains(678f, 80f, 0, 80, 1080, 2260))
    }
    @Test fun fullscreenPreservesExactEdgeCoordinatesWithoutClamping() {
        for (p in listOf(0f to 0f, 1079f to 0f, 0f to 2339f, 1079f to 2339f))
            assertTrue(PointerViewport.contains(p.first, p.second, 0, 0, 1080, 2340))
        assertFalse(PointerViewport.contains(1080f, 100f, 0, 0, 1080, 2340))
        assertFalse(PointerViewport.contains(100f, 2340f, 0, 0, 1080, 2340))
    }
    @Test fun landscapeRelayoutUsesBothOriginAxes() {
        assertFalse(PointerViewport.contains(79f, 100f, 80, 0, 2260, 1080))
        assertTrue(PointerViewport.contains(80f, 100f, 80, 0, 2260, 1080))
        assertTrue(PointerViewport.contains(2339f, 1079f, 80, 0, 2260, 1080))
        assertFalse(PointerViewport.contains(2340f, 1079f, 80, 0, 2260, 1080))
    }
    @Test fun invalidOrUnlaidOutGeometryCannotAuthorizeInput() {
        for (n in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(PointerViewport.contains(n, 10f, 0, 0, 1080, 2340))
            assertFalse(PointerViewport.contains(10f, n, 0, 0, 1080, 2340))
        }
        assertFalse(PointerViewport.contains(0f, 0f, 0, 0, 0, 0))
        assertFalse(PointerViewport.contains(-1f, 10f, 0, 0, 1080, 2340))
    }
}
