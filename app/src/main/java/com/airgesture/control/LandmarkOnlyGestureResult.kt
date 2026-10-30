package com.airgesture.control

import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/** Zero-copy adapter. No classification was performed, so there is no neutral category. */
internal class LandmarkOnlyGestureResult(private val source: HandLandmarkerResult) : GestureRecognizerResult() {
    override fun timestampMs() = source.timestampMs()
    override fun landmarks() = source.landmarks()
    override fun worldLandmarks() = source.worldLandmarks()
    override fun handedness() = source.handedness()
    override fun gestures(): List<List<Category>> = emptyList()
}
