package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandOwnershipTrackerTest {
    @Test
    fun ownershipFollowsTheSameHandWhenDetectionOrderChanges() {
        val tracker = HandOwnershipTracker()
        val first = tracker.select(
            listOf(observation(0, "Left", 0.2f, 0.4f)),
            ControlHandPreference.EITHER,
            timestampMs = 0L
        )
        val second = tracker.select(
            listOf(observation(0, "Right", 0.8f, 0.4f), observation(1, "Left", 0.21f, 0.4f)),
            ControlHandPreference.EITHER,
            timestampMs = 33L
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
            ControlHandPreference.RIGHT,
            timestampMs = 0L
        )

        assertEquals(1, selection?.index)
    }

    @Test
    fun velocityPredictionKeepsOwnershipWithMovingHand() {
        val tracker = HandOwnershipTracker()
        tracker.select(
            listOf(observation(0, "Left", 0.1f, 0.4f)),
            ControlHandPreference.EITHER,
            timestampMs = 0L
        )
        tracker.select(
            listOf(observation(0, "Left", 0.2f, 0.4f)),
            ControlHandPreference.EITHER,
            timestampMs = 100L
        )

        val selection = tracker.select(
            listOf(
                observation(0, "Left", 0.25f, 0.4f),
                observation(1, "Right", 0.2f, 0.4f)
            ),
            ControlHandPreference.EITHER,
            timestampMs = 200L
        )

        assertEquals(0, selection?.index)
    }

    @Test
    fun ambiguousCloseCandidatesAreRejected() {
        val tracker = HandOwnershipTracker()
        tracker.select(
            listOf(observation(0, "Left", 0.2f, 0.4f)),
            ControlHandPreference.EITHER,
            timestampMs = 0L
        )

        val selection = tracker.select(
            listOf(
                observation(0, "Left", 0.25f, 0.4f),
                observation(1, "Left", 0.26f, 0.4f)
            ),
            ControlHandPreference.EITHER,
            timestampMs = 33L
        )

        assertNull(selection)
    }

    @Test
    fun distantReplacementIsRejectedInsteadOfTakingOwnership() {
        val tracker = HandOwnershipTracker()
        assertEquals(
            0,
            tracker.select(
                listOf(observation(0, "Left", 0.2f, 0.4f)),
                ControlHandPreference.EITHER,
                timestampMs = 0L
            )?.index
        )

        assertNull(
            tracker.select(
                listOf(observation(1, "Right", 0.85f, 0.4f)),
                ControlHandPreference.EITHER,
                timestampMs = 33L
            )
        )
    }

    @Test fun briefMissingHandPreservesIdentityButNeverSelectsAMissingHand() {
        val tracker = HandOwnershipTracker()
        val first = tracker.select(listOf(observation(0,"Left",.2f,.4f)), ControlHandPreference.EITHER,0L)
        assertNull(tracker.select(emptyList(), ControlHandPreference.EITHER,100L))
        assertNull(tracker.select(emptyList(), ControlHandPreference.EITHER,200L))
        val recovered = tracker.select(listOf(observation(0,"Left",.21f,.4f)), ControlHandPreference.EITHER,300L)
        assertEquals(first?.ownerId, recovered?.ownerId)
    }

    @Test fun missingFramesDoNotExtendOwnershipAndOppositeHandCannotRecoverIt() {
        val tracker = HandOwnershipTracker()
        val first = tracker.select(listOf(observation(0,"Left",.2f,.4f)), ControlHandPreference.EITHER,0L)
        tracker.select(emptyList(), ControlHandPreference.EITHER,100L)
        assertNull(tracker.select(listOf(observation(0,"Right",.2f,.4f)), ControlHandPreference.EITHER,200L))
        val next = tracker.select(listOf(observation(0,"Left",.2f,.4f)), ControlHandPreference.EITHER,300L)
        org.junit.Assert.assertNotEquals(first?.ownerId, next?.ownerId)
        tracker.select(emptyList(), ControlHandPreference.EITHER,400L)
        val expired = tracker.select(listOf(observation(0,"Left",.2f,.4f)), ControlHandPreference.EITHER,900L)
        org.junit.Assert.assertNotEquals(next?.ownerId, expired?.ownerId)
    }

    private fun observation(index: Int, handedness: String, x: Float, y: Float) =
        HandObservation(index, handedness, x, y, palmSize = 0.12f)
}
