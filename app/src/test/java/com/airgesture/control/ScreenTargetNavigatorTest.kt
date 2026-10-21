package com.airgesture.control

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ScreenTargetNavigatorTest {
    private class VisibleHighlight : ScreenTargetHighlight {
        override var isVisible = false
        override fun show(target: Rect): Boolean { isVisible=true; return true }
        override fun hide() { isVisible=false }
    }
    @Suppress("DEPRECATION")
    private fun target(text: String, click: () -> Unit) = AccessibilityNodeInfo.obtain().apply {
        isVisibleToUser=true; isEnabled=true; isClickable=true
        packageName="com.example.safe"; this.text=text
        setBoundsInScreen(Rect(10,10,100,100))
        shadowOf(this).setOnPerformActionListener { action, _ ->
            if (action == AccessibilityNodeInfo.ACTION_CLICK) { click(); true } else false
        }
    }
    @Test fun actualNodeClickOccursOnlyOnceAfterVisibleSelection() {
        var clicked=0; val node=target("Open") { clicked++ }
        val highlight=VisibleHighlight(); val navigator=ScreenTargetNavigator(highlight)
        assertFalse(navigator.perform(node,AirAction.SELECT_ITEM,1L))
        assertTrue(navigator.perform(node,AirAction.NEXT_ITEM,1L))
        assertTrue(navigator.perform(node,AirAction.SELECT_ITEM,1L))
        assertFalse(navigator.perform(node,AirAction.SELECT_ITEM,1L))
        assertEquals(1,clicked)
    }
    @Test fun changedCollidingLabelOrInvisibleHighlightCannotClick() {
        var clicked=0; val node=target("Aa") { clicked++ }
        val highlight=VisibleHighlight(); val navigator=ScreenTargetNavigator(highlight)
        assertTrue(navigator.perform(node,AirAction.NEXT_ITEM,1L))
        node.text="BB"
        assertFalse(navigator.perform(node,AirAction.SELECT_ITEM,1L))
        assertTrue(navigator.perform(node,AirAction.NEXT_ITEM,1L))
        highlight.isVisible=false
        assertFalse(navigator.perform(node,AirAction.SELECT_ITEM,1L))
        assertEquals(0,clicked)
    }
    @Test fun disabledOrPasswordControlsCannotBeConfirmed() {
        var clicked=0; val node=target("Open") { clicked++ }
        val navigator=ScreenTargetNavigator(VisibleHighlight())
        assertTrue(navigator.perform(node,AirAction.NEXT_ITEM,1L))
        node.isEnabled=false
        assertFalse(navigator.perform(node,AirAction.SELECT_ITEM,1L))
        node.isEnabled=true; node.isPassword=true
        assertFalse(navigator.perform(node,AirAction.NEXT_ITEM,1L))
        assertEquals(0,clicked)
    }
}
