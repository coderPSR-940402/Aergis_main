package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Scenario tests for the failure modes listed in DEVELOPMENT_STATUS.md:
 * occlusion, rapid crossings, and large hand-scale changes.
 * Pure JVM tests; each scenario replays a scripted frame sequence.
 */
class HandOwnershipScenarioTest {

    @Test
    fun occlusionStartsNewOwnershipGeneration() {
        val tracker = HandOwnershipTracker()
        val before = tracker.select(listOf(hand(0, "Left", 0.4f, 0.4f)), EITHER, 0L)
        assertNull(tracker.select(emptyList(), EITHER, 33L))
        val after = tracker.select(listOf(hand(0, "Left", 0.4f, 0.4f)), EITHER, 66L)

        assertNotNull(before)
        assertNotNull(after)
        // A reappearing hand must not inherit the pre-occlusion owner id.
        assertNotEquals(before?.ownerId, after?.ownerId)
    }

    @Test
    fun outOfOrderOrDuplicateTimestampsAreRejectedWithoutLosingOwner() {
        val tracker = HandOwnershipTracker()
        val first = tracker.select(listOf(hand(0, "Left", 0.4f, 0.4f)), EITHER, 100L)

        assertNull(tracker.select(listOf(hand(0, "Left", 0.4f, 0.4f)), EITHER, 100L))
        assertNull(tracker.select(listOf(hand(0, "Left", 0.4f, 0.4f)), EITHER, 50L))

        val next = tracker.select(listOf(hand(0, "Left", 0.41f, 0.4f)), EITHER, 133L)
        assertEquals(first?.ownerId, next?.ownerId)
    }

    @Test
    fun mislabeledHandednessNearCrossingFailsClosed() {
        val tracker = HandOwnershipTracker()
        tracker.select(listOf(hand(0, "Left", 0.4f, 0.4f)), EITHER, 0L)

        // Other hand is labeled "Right" but sits closer than the true owner.
        // Handedness penalty alone is not enough to separate them: reject both.
        val selection = tracker.select(
            listOf(hand(0, "Right", 0.42f, 0.4f), hand(1, "Left", 0.50f, 0.4f)),
            EITHER,
            33L
        )

        assertNull(selection)
    }

    @Test
    fun matchingHandednessWinsWhenCandidatesAreClearlySeparated() {
        val tracker = HandOwnershipTracker()
        val first = tracker.select(listOf(hand(0, "Left", 0.4f, 0.4f)), EITHER, 0L)

        val second = tracker.select(
            listOf(hand(0, "Right", 0.47f, 0.4f), hand(1, "Left", 0.45f, 0.4f)),
            EITHER,
            33L
        )

        assertEquals(1, second?.index)
        assertEquals(first?.ownerId, second?.ownerId)
    }

    @Test
    fun gradualScaleGrowthKeepsOwnershipAcrossFrames() {
        val tracker = HandOwnershipTracker()
        val palms = listOf(0.12f, 0.14f, 0.17f, 0.20f, 0.24f)
        val ids = palms.mapIndexed { i, palm ->
            tracker.select(
                listOf(HandObservation(0, "Left", 0.5f, 0.5f, palm)),
                EITHER,
                i * 33L
            )?.ownerId
        }

        assertNotNull(ids.first())
        assertEquals(1, ids.toSet().size)
    }

    @Test
    fun abruptScaleJumpIsRejectedAndClearsOwnership() {
        val tracker = HandOwnershipTracker()
        val first = tracker.select(
            listOf(HandObservation(0, "Left", 0.5f, 0.5f, 0.12f)), EITHER, 0L
        )
        val jump = tracker.select(
            listOf(HandObservation(0, "Left", 0.5f, 0.5f, 0.20f)), EITHER, 33L
        )
        val after = tracker.select(
            listOf(HandObservation(0, "Left", 0.5f, 0.5f, 0.20f)), EITHER, 66L
        )

        assertNull(jump)
        assertNotEquals(first?.ownerId, after?.ownerId)
    }

    @Test
    fun longFrameGapDoesNotExtrapolateVelocityUnbounded() {
        val tracker = HandOwnershipTracker()
        tracker.select(listOf(hand(0, "Left", 0.1f, 0.4f)), EITHER, 0L)
        tracker.select(listOf(hand(0, "Left", 0.2f, 0.4f)), EITHER, 100L)

        // Prediction is capped at 250 ms, so a hand "teleporting" to where an
        // unbounded extrapolation would put it must be rejected.
        val selection = tracker.select(listOf(hand(0, "Left", 0.70f, 0.4f)), EITHER, 1100L)

        assertNull(selection)
    }

    private fun hand(index: Int, handedness: String, x: Float, y: Float) =
        HandObservation(index, handedness, x, y, palmSize = 0.12f)

    private companion object {
        val EITHER = ControlHandPreference.EITHER
    }
}
