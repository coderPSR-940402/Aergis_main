package com.airgesture.control
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PrecisionDragControllerTest {
    @Test fun rejectedCancellationKeepsNativeOwnershipAndLateCallbacksCannotRestart() {
        val calls=mutableListOf<Pair<GestureDescription,AccessibilityService.GestureResultCallback>>()
        val outcomes=mutableListOf<ActionDispatchOutcome>()
        var accept=true
        val c=PrecisionDragController(Handler(Looper.getMainLooper()), {g,cb->calls.add(g to cb);accept}, {true}, outcomes::add)
        val point=PointerCoordinateMapper.Point(50f,50f)
        c.update(point,true,0,SystemClock.uptimeMillis())
        val down=calls.single(); down.second.onCompleted(down.first)
        accept=false; c.cancel()
        assertTrue(c.isBusy)
        c.update(point,false,0,SystemClock.uptimeMillis())
        c.update(point,true,0,SystemClock.uptimeMillis())
        assertEquals(2,calls.size)
        down.second.onCompleted(down.first)
        assertTrue(c.isBusy)
        assertFalse(outcomes.contains(ActionDispatchOutcome.CANCELLED))
    }
    @Test fun acknowledgedCancellationIsIdempotent() {
        val calls=mutableListOf<Pair<GestureDescription,AccessibilityService.GestureResultCallback>>()
        val outcomes=mutableListOf<ActionDispatchOutcome>()
        val c=PrecisionDragController(Handler(Looper.getMainLooper()), {g,cb->calls.add(g to cb);true}, {true}, outcomes::add)
        c.update(PointerCoordinateMapper.Point(50f,50f),true,0,SystemClock.uptimeMillis())
        c.cancel()
        val cancel=calls.last()
        cancel.second.onCancelled(cancel.first); cancel.second.onCompleted(cancel.first)
        assertEquals(listOf(ActionDispatchOutcome.SUBMITTED,ActionDispatchOutcome.CANCELLED),outcomes)
        assertFalse(c.isBusy)
    }
}
