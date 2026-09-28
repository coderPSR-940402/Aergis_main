package com.airgesture.control

import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.LandmarkSmoother2D
import com.airgesture.control.filtering.Point3D
import com.airgesture.control.pointer.ClickHysteresisStateMachine
import com.airgesture.control.pointer.SwipeDirection
import com.airgesture.control.pointer.SwipeGestureEngine

data class ProcessedGestureResult(
    val smoothedX: Float,
    val smoothedY: Float,
    val isClickEngaged: Boolean,
    val detectedSwipe: SwipeDirection,
    val normalizedDistance: Float
)

class GestureInterpreter(
    private val mappings: ActionMappingStore? = null,
    private val smoother: LandmarkSmoother2D = LandmarkSmoother2D(),
    private val validator: KinematicValidator = KinematicValidator(),
    private val clickStateMachine: ClickHysteresisStateMachine = ClickHysteresisStateMachine(),
    private val swipeEngine: SwipeGestureEngine = SwipeGestureEngine()
) {
    fun interpret(signal: GestureSignal): GestureDecision {
        val source = when (signal.name.lowercase()) {
            "thumb_up", "thumbs_up" -> AirAction.TAP
            "victory", "peace" -> AirAction.BACK
            "open_palm", "open hand" -> AirAction.HOME
            "closed_fist", "fist" -> AirAction.RECENTS
            "pointing_up", "point" -> AirAction.DOUBLE_TAP
            else -> AirAction.NONE
        }
        val mappedAction = mappings?.mapping(source) ?: source
        return GestureDecision(mappedAction, signal.score)
    }

    fun processFrame(landmarks: List<Point3D>, timestampMs: Long): ProcessedGestureResult? {
        if (landmarks.size <= KinematicValidator.INDEX_TIP) {
            reset()
            return null
        }

        // Pointer position must follow the actual recognized index fingertip.
        // Kinematic validation remains responsible for gesture metrics, but must
        // not relocate the pointer when a fingertip is temporarily extended.
        val indexTip = landmarks[KinematicValidator.INDEX_TIP]
        val smoothedPoint = smoother.filter(indexTip.x, indexTip.y, timestampMs)
        val dNorm = validator.calculateNormalizedFingerDistance(landmarks)
        val clickState = clickStateMachine.processFrame(dNorm, timestampMs)
        val swipeState = swipeEngine.processFrame(
            Point3D(smoothedPoint.x, smoothedPoint.y),
            timestampMs
        )

        return ProcessedGestureResult(
            smoothedX = smoothedPoint.x,
            smoothedY = smoothedPoint.y,
            isClickEngaged = clickState,
            detectedSwipe = swipeState,
            normalizedDistance = dNorm
        )
    }

    fun reset() {
        smoother.reset()
        clickStateMachine.reset()
        swipeEngine.reset()
    }
}
