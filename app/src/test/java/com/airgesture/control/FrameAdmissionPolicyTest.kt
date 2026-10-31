package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class FrameAdmissionPolicyTest {
    @Test fun oldInputCannotBecomeCurrentAfterAnAuthorityRoundTrip() {
        assertEquals(FrameAdmissionPolicy.Rejection.SOURCE_CONTEXT_CHANGED,
            FrameAdmissionPolicy.rejection(10L,12L,20L,true,ControlMode.ARMED))
        assertNull(FrameAdmissionPolicy.rejection(12L,12L,20L,true,ControlMode.ARMED))
    }

    @Test fun hiddenPointerWorkStopsAtTheExistingCommandAgeBoundary() {
        assertNull(FrameAdmissionPolicy.rejection(7L,7L,350L,false,ControlMode.ARMED))
        assertEquals(FrameAdmissionPolicy.Rejection.STALE_BEFORE_INFERENCE,
            FrameAdmissionPolicy.rejection(7L,7L,351L,false,ControlMode.ARMED))
    }

    @Test fun visiblePointerAndReadOnlyPreviewKeepTheirExistingAllowance() {
        for ((pointer,mode) in listOf(true to ControlMode.ARMED,false to ControlMode.READY)) {
            assertNull(FrameAdmissionPolicy.rejection(7L,7L,600L,pointer,mode))
            assertEquals(FrameAdmissionPolicy.Rejection.STALE_BEFORE_INFERENCE,
                FrameAdmissionPolicy.rejection(7L,7L,601L,pointer,mode))
        }
    }

    @Test fun futureExposureCannotSpendNativeWorkOrGainActionAuthority() {
        assertEquals(FrameAdmissionPolicy.Rejection.INVALID_CLOCK_ORDER,
            FrameAdmissionPolicy.rejection(7L,7L,-1L,true,ControlMode.ARMED))
        assertNull(FrameAdmissionPolicy.rejection(7L,7L,0L,true,ControlMode.ARMED))
    }
}
