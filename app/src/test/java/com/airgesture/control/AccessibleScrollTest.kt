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
@Config(sdk = [35])
class AccessibleScrollTest {
    @Suppress("DEPRECATION")
    private fun node(bounds: Rect = Rect(0, 0, 100, 200)) = AccessibilityNodeInfo.obtain().apply {
        isVisibleToUser = true; isEnabled = true; setBoundsInScreen(bounds)
    }

    @Test fun upwardStrokeAdvancesSoleOffCentreRegionWithoutTouch() {
        val region = node(Rect(400, 300, 600, 900))
        region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
        val actions = ArrayList<Int>()
        shadowOf(region).setOnPerformActionListener { action, _ -> actions.add(action); true }
        assertTrue(AccessibleScroll.perform(region, true))
        assertEquals(listOf(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id), actions)
        assertFalse(AccessibleScroll.perform(region, false))
    }

    @Test fun legacyRegionSupportsBothDirectionsAndReportsRejectionForSwipeFallback() {
        val region = node()
        region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        val actions = ArrayList<Int>()
        shadowOf(region).setOnPerformActionListener { action, _ -> actions.add(action); false }
        assertFalse(AccessibleScroll.perform(region, true))
        assertFalse(AccessibleScroll.perform(region, false))
        assertEquals(listOf(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD), actions)
    }

    @Test fun multiplePanesRequireFocusAndChooseTheContainingRegion() {
        val root = node(Rect(0, 0, 1000, 1000))
        val left = node(Rect(0, 0, 400, 900))
        val right = node(Rect(500, 0, 1000, 900))
        val focused = node(Rect(550, 100, 650, 200))
        shadowOf(root).addChild(left); shadowOf(root).addChild(right)
        shadowOf(right).addChild(focused)
        left.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
        right.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
        var leftCalls = 0; var rightCalls = 0
        shadowOf(left).setOnPerformActionListener { _, _ -> leftCalls++; true }
        shadowOf(right).setOnPerformActionListener { _, _ -> rightCalls++; true }
        assertFalse(AccessibleScroll.perform(root, true))
        assertEquals(0, leftCalls + rightCalls)
        focused.isAccessibilityFocused = true
        assertTrue(AccessibleScroll.perform(root, true))
        assertEquals(0, leftCalls)
        assertEquals(1, rightCalls)
    }

    @Test fun horizontalOnlyCarouselCannotConsumeAVerticalScroll() {
        val region = node()
        region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT)
        region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT)
        region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        var calls = 0
        shadowOf(region).setOnPerformActionListener { _, _ -> calls++; true }
        assertFalse(AccessibleScroll.perform(region, true))
        assertFalse(AccessibleScroll.perform(region, false))
        assertEquals(0, calls)
    }

    @Test fun equallySpecificOverlappingRegionsCannotBeChosenByTraversalOrder() {
        val root = node(Rect(0, 0, 1000, 1000))
        val first = node(Rect(100, 100, 800, 900))
        val second = node(Rect(100, 100, 800, 900))
        val focused = node(Rect(200, 200, 300, 300)).apply { isAccessibilityFocused = true }
        shadowOf(root).addChild(first); shadowOf(root).addChild(second)
        shadowOf(first).addChild(focused)
        var calls = 0
        for (region in listOf(first, second)) {
            region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
            shadowOf(region).setOnPerformActionListener { _, _ -> calls++; true }
        }
        assertFalse(AccessibleScroll.perform(root, true))
        assertEquals(0, calls)
    }

    @Test fun missingHiddenDisabledOrPasswordRegionsCannotScroll() {
        assertFalse(AccessibleScroll.perform(null, true))
        for (blocked in listOf("hidden", "disabled", "password")) {
            val region = node()
            region.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
            when (blocked) {
                "hidden" -> region.isVisibleToUser = false
                "disabled" -> region.isEnabled = false
                else -> region.isPassword = true
            }
            var calls = 0
            shadowOf(region).setOnPerformActionListener { _, _ -> calls++; true }
            assertFalse(AccessibleScroll.perform(region, true))
            assertEquals(0, calls)
        }
    }
}
