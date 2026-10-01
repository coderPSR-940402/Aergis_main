package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandOwnershipTrackerTest {
    @Test
    fun ownershipFollowsTheSameHandWhenDetectionOrderChanges() {
        val tracker = HandOwnershipTracker()
        val first = tracker.select(
            listOf(observation(0, "Left", 0.2f, 0.4f), observation(1, "Right", 0.8f, 0.4f)),
            ControlHandPreference.EITHER
        )
        val second = tracker.select(
            listOf(observation(0, "Right", 0.8f, 0.4f), observation(1, "Left", 0.21f, 0.4f)),
            ControlHandPreference.EITHER
        )

        assertEquals(0, first?.index)
        assertEquals(1, second?.index)
        assertEquals(first?.ownerId, second?.ownerId)
    }

    @Test
    fun configuredHandPreferenceFiltersCandidates() {
        val tracker = HandOwnershipTracker()
        val selection = tracker.select(
            listOf(observation(0, "Left", 0.2f, 0.4f), observation(1, "Right", 0.8f, 0.4f)),
            ControlHandPreference.RIGHT
        )

        assertEquals(1, selection?.index)
    }

    @Test
    fun distantReplacementIsRejectedInsteadOfTakingOwnership() {
        val tracker = HandOwnershipTracker()
        assertEquals(
            0,
            tracker.select(
                listOf(observation(0, "Left", 0.2f, 0.4f)),
                ControlHandPreference.EITHER
            )?.index
        )

        assertNull(
            tracker.select(
                listOf(observation(1, "Right", 0.85f, 0.4f)),
                ControlHandPreference.EITHER
            )
        )
    }

    private fun observation(index: Int, handedness: String, x: Float, y: Float) =
        HandObservation(index, handedness, x, y, palmSize = 0.12f)
}
