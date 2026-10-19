package com.airgesture.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BoundedGestureDispatcherTest {
    private val calls = mutableListOf<Pair<GestureDescription, AccessibilityService.GestureResultCallback>>()
    private val outcomes = mutableListOf<ActionDispatchOutcome>()
    private var epoch = 1L
    private var accepted = true
    private var permissionFailure = false
    private val input = BoundedGestureDispatcher(Handler(Looper.getMainLooper()),
        dispatch = { gesture, callback ->
            if (permissionFailure) throw SecurityException("Accessibility permission revoked")
            calls.add(gesture to callback); accepted },
        permitted = { it == epoch }, record = outcomes::add)
    private fun gesture() = GestureDescription.Builder().addStroke(
        GestureDescription.StrokeDescription(Path().apply { moveTo(50f, 60f) }, 0L, 1200L)).build()

    @Test fun completionIsExactlyOnceAndConcurrentCommandsAreNotQueued() {
        input.submit(gesture(), epoch)
        input.submit(gesture(), epoch)
        assertEquals(1, calls.size)
        assertEquals(listOf(ActionDispatchOutcome.PLATFORM_REJECTED), outcomes)
        calls[0].second.onCompleted(calls[0].first)
        calls[0].second.onCancelled(calls[0].first)
        assertEquals(listOf(ActionDispatchOutcome.PLATFORM_REJECTED, ActionDispatchOutcome.COMPLETED), outcomes)
    }

    @Test fun epochLossRequestsNativeCancellationAndLateCallbackCannotReplay() {
        input.submit(gesture(), epoch)
        epoch++
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        assertEquals(2, calls.size)
        assertFalse(calls[1].first.getStroke(0).willContinue())
        calls[1].second.onCancelled(calls[1].first)
        calls[0].second.onCompleted(calls[0].first)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(ActionDispatchOutcome.CANCELLED), outcomes)
        assertEquals(2, calls.size)
    }

    @Test fun cancellationRejectionDoesNotPretendOriginalInputEnded() {
        input.submit(gesture(), epoch)
        accepted = false
        input.cancel()
        accepted = true
        input.submit(gesture(), epoch)
        assertEquals("No overlapping command after rejected cancellation", 2, calls.size)
        calls[0].second.onCompleted(calls[0].first)
        input.submit(gesture(), epoch)
        assertEquals(3, calls.size)
        calls[2].second.onCompleted(calls[2].first)
    }

    @Test fun staleRequestAndImmediateRejectionNeverRetry() {
        input.submit(gesture(), epoch - 1)
        assertTrue(calls.isEmpty())
        accepted = false
        input.submit(gesture(), epoch)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(1, calls.size)
        assertEquals(listOf(ActionDispatchOutcome.POLICY_DENIED, ActionDispatchOutcome.PLATFORM_REJECTED), outcomes)
    }

    @Test fun permissionExceptionFailsClearlyWithoutRetry() {
        permissionFailure = true
        input.submit(gesture(), epoch)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(calls.isEmpty())
        assertEquals(listOf(ActionDispatchOutcome.PLATFORM_REJECTED), outcomes)
    }

    @Test fun completionDeadlineRequestsCancellationOnlyOnce() {
        input.submit(gesture(), epoch)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        input.cancel()
        assertEquals(2, calls.size)
        assertTrue("Local timeout is not native cancellation evidence", outcomes.isEmpty())
        calls[0].second.onCancelled(calls[0].first)
        assertEquals(listOf(ActionDispatchOutcome.CANCELLED), outcomes)
    }
}
