package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnedGestureEvidenceSelectorTest {
    @Test
    fun selectedOwnerUsesGestureFromSameDetectionIndex() {
        val perHandGestures = listOf(
            listOf("Victory"),
            listOf("Open_Palm")
        )

        val selected = OwnedGestureEvidenceSelector.select(perHandGestures, ownerIndex = 1)

        assertEquals("Open_Palm", selected)
    }

    @Test
    fun nonOwnerFirstDetectionCannotOverrideSelectedOwner() {
        val perHandGestures = listOf(
            listOf("Victory"),
            listOf("Thumb_Up")
        )

        val selected = OwnedGestureEvidenceSelector.select(perHandGestures, ownerIndex = 1)

        assertEquals("Thumb_Up", selected)
    }

    @Test
    fun missingOwnerFailsClosed() {
        val perHandGestures = listOf(listOf("Victory"))

        assertNull(OwnedGestureEvidenceSelector.select(perHandGestures, ownerIndex = null))
    }

    @Test
    fun missingEvidenceForSelectedOwnerFailsClosed() {
        val perHandGestures = listOf(listOf("Victory"), emptyList())

        assertNull(OwnedGestureEvidenceSelector.select(perHandGestures, ownerIndex = 1))
        assertNull(OwnedGestureEvidenceSelector.select(perHandGestures, ownerIndex = 2))
    }
}
