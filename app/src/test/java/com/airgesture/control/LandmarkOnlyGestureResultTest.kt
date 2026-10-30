package com.airgesture.control

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import org.junit.Assert.*
import org.junit.Test

class LandmarkOnlyGestureResultTest {
    @Test fun handOnlyInferenceCannotInventNeutralClassifierEvidence() {
        val source = object : HandLandmarkerResult() {
            override fun timestampMs() = 1000L
            override fun landmarks(): List<List<NormalizedLandmark>> = listOf(listOf(NormalizedLandmark.create(.5f, .5f, 0f)))
            override fun worldLandmarks(): List<List<Landmark>> = emptyList()
            override fun handedness(): List<List<Category>> = listOf(listOf(Category.create(.99f, 0, "Left", "")))
        }
        val result = LandmarkOnlyGestureResult(source)
        assertEquals(1, result.landmarks().size)
        assertEquals(1000L, result.timestampMs())
        assertTrue(result.gestures().isEmpty())
        assertNull(OwnedGestureEvidenceSelector.select(result.gestures(), 0))
    }
}
