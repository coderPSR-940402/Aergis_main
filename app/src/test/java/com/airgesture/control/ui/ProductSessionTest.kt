package com.airgesture.control.ui

import com.airgesture.control.*
import org.junit.Assert.*
import org.junit.Test

class ProductSessionTest {
    private fun state() = AirRuntime.uiStateSnapshot().copy(running = true, cameraReady = true,
        visionReady = true, visionError = null, controlMode = ControlMode.ARMED,
        foregroundSafety = ForegroundSafety.SAFE, pointerTracking = false,
        pointerFeedback = PointerFeedback.DISABLED)
    @Test fun pointerOffDoesNotPauseAnArmedGestureSession() {
        val shown = ProductSession.from(state(), true, true, false, false)
        assertEquals(ProductActionState.ARMED, shown.actions)
        assertTrue(shown.canToggleArm)
    }
    @Test fun pointerClicksRemainAnIndependentInputChannel() {
        assertEquals(ProductActionState.ARMED, ProductSession.from(state(), true, false, true, true).actions)
        assertEquals(ProductActionState.DISABLED, ProductSession.from(state(), true, false, true, false).actions)
    }
    @Test fun hiddenCursorDoesNotDisableTheSelectionPinchChannel() {
        val shown = ProductSession.from(state(), true, false, false, true)
        assertEquals(ProductActionState.ARMED, shown.actions)
        assertTrue(shown.canToggleArm)
    }
    @Test fun disarmAlwaysRemainsAvailableWhenArmedButUnsafe() {
        val unsafe = state().copy(foregroundSafety = ForegroundSafety.PROTECTED)
        val shown = ProductSession.from(unsafe, true, true, false, false)
        assertEquals(ProductActionState.PAUSED, shown.actions)
        assertTrue(shown.canToggleArm)
    }
    @Test fun sessionNotReadyCannotOfferArming() {
        val ready = state().copy(controlMode = ControlMode.READY)
        for (runtime in listOf(ready.copy(running = false), ready.copy(cameraReady = false),
            ready.copy(visionReady = false), ready.copy(visionError = "failed"))) {
            assertFalse(ProductSession.from(runtime, true, true, false, false).canToggleArm)
        }
        assertFalse(ProductSession.from(ready, false, true, false, false).canToggleArm)
    }
}
