package com.airgesture.control

/**
 * Short temporal confirmation gate for classifier/landmark command poses.
 *
 * This gate exists only to reject transition poses and one-frame classifier spikes.
 * It deliberately does not reintroduce legacy gesture hold delays; long holdMs values
 * remain persistence/rollback data and do not define confirmation latency.
 */
internal class StaticPoseConfirmation {
    private data class Candidate(
        val id: String,
        val owner: String,
        val epoch: Long,
        val firstTimestamp: Long,
        val lastTimestamp: Long,
        val frameCount: Int
    )

    companion object {
        private const val MIN_CONSECUTIVE_FRAMES = 3
        private const val MIN_TWO_FRAME_WINDOW_MS = 80L
        private const val MAX_SAMPLE_GAP_MS = 350L
    }

    private var candidate: Candidate? = null

    @Suppress("UNUSED_PARAMETER")
    fun accept(
        id: String?,
        owner: String?,
        epoch: Long,
        timestamp: Long,
        valid: Boolean,
        holdMs: Long
    ): Boolean {
        val poseId = id?.takeIf { it.isNotBlank() }
        val ownerId = owner?.takeIf { it.isNotBlank() }
        if (!valid || poseId == null || ownerId == null || timestamp < 0L) {
            reset()
            return false
        }

        val current = candidate
        val sameCandidate = current != null &&
            current.id == poseId &&
            current.owner == ownerId &&
            current.epoch == epoch

        if (!sameCandidate || current == null || timestamp < current.lastTimestamp ||
            timestamp - current.lastTimestamp > MAX_SAMPLE_GAP_MS) {
            candidate = Candidate(
                id = poseId,
                owner = ownerId,
                epoch = epoch,
                firstTimestamp = timestamp,
                lastTimestamp = timestamp,
                frameCount = 1
            )
            return false
        }

        // A replayed result is not another independent observation.
        if (timestamp == current.lastTimestamp) return false

        val updated = current.copy(
            lastTimestamp = timestamp,
            frameCount = current.frameCount + 1
        )
        candidate = updated

        val elapsed = timestamp - updated.firstTimestamp
        return updated.frameCount >= MIN_CONSECUTIVE_FRAMES ||
            (updated.frameCount >= 2 && elapsed >= MIN_TWO_FRAME_WINDOW_MS)
    }

    fun reset() {
        candidate = null
    }
}
