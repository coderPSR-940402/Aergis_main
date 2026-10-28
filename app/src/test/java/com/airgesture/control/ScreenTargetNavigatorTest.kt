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
        var lastBounds: Rect? = null
        override var isVisible = false
        override fun show(target: Rect): Boolean { lastBounds=Rect(target); isVisible=true; return true }
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
    private fun container() = target("") { }.apply { isClickable = false }

    @Test fun replacingSiblingLabelRevokesTheSelectedRowButton() {
        var clicks = 0
        val row = container()
        val label = target("Original item") { }.apply { isClickable = false }
        val button = target("Delete") { clicks++ }
        shadowOf(row).addChild(label); shadowOf(row).addChild(button)
        val highlight = VisibleHighlight()
        val navigator = ScreenTargetNavigator(highlight)
        assertTrue(navigator.perform(row, AirAction.NEXT_ITEM, 1L))
        label.text = "Replacement item"
        assertFalse(navigator.refresh(row, 1L))
        assertFalse(highlight.isVisible)
        assertFalse(navigator.perform(row, AirAction.SELECT_ITEM, 1L))
        assertEquals(0, clicks)
    }

    @Test fun confirmationRechecksSiblingLabelEvenWithoutAContentEvent() {
        var clicks = 0
        val row = container()
        val label = target("Original item") { }.apply { isClickable = false }
        shadowOf(row).addChild(label)
        shadowOf(row).addChild(target("Delete") { clicks++ })
        val navigator = ScreenTargetNavigator(VisibleHighlight())
        assertTrue(navigator.perform(row, AirAction.NEXT_ITEM, 1L))
        label.text = "Replacement item"
        assertFalse(navigator.perform(row, AirAction.SELECT_ITEM, 1L))
        assertEquals(0, clicks)
    }

    @Suppress("DEPRECATION")
    @Test fun nestedButtonUsesItsContainingCollectionItemIdentity() {
        var clicks = 0
        val root = container()
        val row = container().apply {
            collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, 0, 1, false)
        }
        val label = target("Original item") { }.apply { isClickable = false }
        val actions = container()
        shadowOf(root).addChild(row)
        shadowOf(row).addChild(label); shadowOf(row).addChild(actions)
        shadowOf(actions).addChild(target("Delete") { clicks++ })
        val navigator = ScreenTargetNavigator(VisibleHighlight())
        assertTrue(navigator.perform(root, AirAction.NEXT_ITEM, 1L))
        label.text = "Replacement item"
        assertFalse(navigator.refresh(root, 1L))
        assertFalse(navigator.perform(root, AirAction.SELECT_ITEM, 1L))
        assertEquals(0, clicks)
    }

    @Suppress("DEPRECATION")
    @Test fun updatingAnotherCollectionRowRetainsTheSelectedButtonAndConsumesItOnce() {
        var clicks = 0
        val root = container()
        val selectedRow = container().apply {
            collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0, 1, 0, 1, false)
        }
        val otherRow = container()
        val otherLabel = target("Other item") { }.apply { isClickable = false }
        shadowOf(root).addChild(selectedRow); shadowOf(root).addChild(otherRow)
        shadowOf(selectedRow).addChild(target("Selected item") { }.apply { isClickable = false })
        shadowOf(selectedRow).addChild(target("Delete") { clicks++ })
        shadowOf(otherRow).addChild(otherLabel)
        val highlight = VisibleHighlight()
        val navigator = ScreenTargetNavigator(highlight)
        assertTrue(navigator.perform(root, AirAction.NEXT_ITEM, 1L))
        otherLabel.text = "Updated other item"
        assertTrue(navigator.refresh(root, 1L))
        assertTrue(highlight.isVisible)
        assertTrue(navigator.perform(root, AirAction.SELECT_ITEM, 1L))
        assertFalse(navigator.perform(root, AirAction.SELECT_ITEM, 1L))
        assertEquals(1, clicks)
    }

    @Test fun nestedButtonWithoutCollectionMetadataRevokesOnRowReplacement() {
        assertNestedUnlabelledRowRevoked(refreshFirst = true)
    }

    @Test fun nestedButtonWithoutCollectionMetadataRechecksBeforeClick() {
        assertNestedUnlabelledRowRevoked(refreshFirst = false)
    }

    private fun assertNestedUnlabelledRowRevoked(refreshFirst: Boolean) {
        var clicks = 0
        val root = container()
        val row = container()
        val label = target("Original item") { }.apply { isClickable = false }
        val actions = container()
        shadowOf(root).addChild(row)
        shadowOf(row).addChild(label); shadowOf(row).addChild(actions)
        shadowOf(actions).addChild(target("Delete") { clicks++ })
        val highlight = VisibleHighlight()
        val navigator = ScreenTargetNavigator(highlight)
        assertTrue(navigator.perform(root, AirAction.NEXT_ITEM, 1L))
        assertTrue(navigator.refresh(root, 1L))
        label.text = "Replacement item"
        if (refreshFirst) {
            assertFalse(navigator.refresh(root, 1L))
            assertFalse(highlight.isVisible)
        }
        assertFalse(navigator.perform(root, AirAction.SELECT_ITEM, 1L))
        assertEquals(0, clicks)
    }

    @Test fun recycledClickableRowCannotRetainSelectionAfterItsChildLabelChanges() {
        var clicks = 0
        val row = target("") { clicks++ }
        val child = target("Original app") { }.apply { isClickable = false }
        shadowOf(row).addChild(child)
        val navigator = ScreenTargetNavigator(VisibleHighlight())
        assertTrue(navigator.perform(row, AirAction.NEXT_ITEM, 1L))
        assertTrue(navigator.refresh(row, 1L))
        child.text = "Different app"
        assertFalse(navigator.refresh(row, 1L))
        assertFalse(navigator.perform(row, AirAction.SELECT_ITEM, 1L))
        assertEquals(0, clicks)
    }

    @Test fun contentRefreshRetainsOnlyTheExactVisibleTarget() {
        var clicks = 0
        val node = target("Open") { clicks++ }
        val highlight = VisibleHighlight()
        val navigator = ScreenTargetNavigator(highlight)
        assertTrue(navigator.perform(node, AirAction.NEXT_ITEM, 1L))
        assertTrue(navigator.refresh(node, 1L))
        assertTrue(highlight.isVisible)
        assertEquals(0, clicks)
        assertTrue(navigator.perform(node, AirAction.SELECT_ITEM, 1L))
        assertEquals(1, clicks)
        assertTrue(navigator.perform(node, AirAction.NEXT_ITEM, 1L))
        node.text = "Replacement"
        assertFalse(navigator.refresh(node, 1L))
        assertFalse(highlight.isVisible)
        assertFalse(navigator.perform(node, AirAction.SELECT_ITEM, 1L))
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
    @Suppress("DEPRECATION")
    @Test fun uniqueCollectionRowFollowsAnInsertionAndUpdatesHighlightBounds() {
        var clicks = 0
        val original = target("Account A") { clicks++ }.apply {
            collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0,1,0,1,false)
        }
        val firstRoot = container()
        shadowOf(firstRoot).addChild(original)
        val highlight = VisibleHighlight()
        val navigator = ScreenTargetNavigator(highlight)
        assertTrue(navigator.perform(firstRoot, AirAction.NEXT_ITEM, 1L))
        val movedRoot = container()
        shadowOf(movedRoot).addChild(target("Account B") { fail("Wrong row clicked") }.apply {
            collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0,1,0,1,false)
        })
        val moved = target("Account A") { clicks++ }.apply {
            setBoundsInScreen(Rect(10,120,100,210))
            collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(1,1,0,1,false)
        }
        shadowOf(movedRoot).addChild(moved)
        assertTrue(navigator.refresh(movedRoot,1L))
        assertEquals(Rect(10,120,100,210),highlight.lastBounds)
        assertTrue(navigator.perform(movedRoot,AirAction.SELECT_ITEM,1L))
        assertEquals(1,clicks)
    }

    @Suppress("DEPRECATION")
    @Test fun duplicateCollectionRowsCannotBeConfirmed() {
        val root = container()
        val row = target("Same account") { fail("Ambiguous row clicked") }.apply {
            collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0,1,0,1,false)
        }
        shadowOf(root).addChild(row)
        val navigator = ScreenTargetNavigator(VisibleHighlight())
        assertTrue(navigator.perform(root,AirAction.NEXT_ITEM,1L))
        shadowOf(root).addChild(target("Same account") { fail("Ambiguous row clicked") }.apply {
            collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(1,1,0,1,false)
        })
        assertFalse(navigator.perform(root,AirAction.SELECT_ITEM,1L))
    }
    @Suppress("DEPRECATION")
    @Test fun replacingCollectionContainerRevokesIdenticalRowContent() {
        var clicks = 0
        val root = container()
        val list = container().apply { viewIdResourceName = "com.example.safe:id/inbox" }
        val row = target("Delete account A") { clicks++ }.apply {
            collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(0,1,0,1,false)
        }
        shadowOf(root).addChild(list); shadowOf(list).addChild(row)
        val navigator = ScreenTargetNavigator(VisibleHighlight())
        assertTrue(navigator.perform(root,AirAction.NEXT_ITEM,1L))
        list.viewIdResourceName = "com.example.safe:id/archive"
        assertFalse(navigator.perform(root,AirAction.SELECT_ITEM,1L))
        assertEquals(0,clicks)
    }
}
