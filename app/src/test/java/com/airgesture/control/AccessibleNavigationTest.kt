package com.airgesture.control
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.accessibility.AccessibilityNodeInfo
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class AccessibleNavigationTest {
    @Test fun absentWindowOrFocusNeverClicksAnArbitraryTarget() {
        for(action in listOf(AirAction.FOCUS_NEXT,AirAction.FOCUS_PREVIOUS,AirAction.ACTIVATE_FOCUSED)) {
            assertFalse(AccessibleNavigation.perform(null,action))
            @Suppress("DEPRECATION") val empty=AccessibilityNodeInfo.obtain()
            assertFalse(AccessibleNavigation.perform(empty,action))
        }
    }
}
