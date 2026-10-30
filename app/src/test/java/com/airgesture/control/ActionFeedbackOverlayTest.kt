package com.airgesture.control

import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionFeedbackOverlayTest {
    @Test fun outcomesStayVisibleOutsideActivityContentAndExpire() {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        val overlay = ActionFeedbackOverlay(context)
        val viewField = ActionFeedbackOverlay::class.java.getDeclaredField("view").apply { isAccessible = true }
        val requested = ActionFeedback(1, AirAction.NEXT_ITEM, updatedAtMs = SystemClock.uptimeMillis())
        try {
            overlay.show(requested)
            val view = viewField.get(overlay) as TextView
            assertTrue(view.isAttachedToWindow)
            assertEquals(requested.message(context), view.text.toString())
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, view.importantForAccessibility)
            val params = view.layoutParams as WindowManager.LayoutParams
            assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
            assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            val completed = requested.result(1, ActionDispatchOutcome.COMPLETED, SystemClock.uptimeMillis())
            overlay.show(completed)
            assertSame(view, viewField.get(overlay))
            assertEquals(completed.message(context), view.text.toString())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4100L))
            assertNull(viewField.get(overlay))
            assertFalse(view.isAttachedToWindow)
        } finally { overlay.close() }
    }
}
