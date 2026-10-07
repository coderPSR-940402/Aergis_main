package com.airgesture.control

import com.airgesture.control.filtering.AdaptiveKalmanFilter
import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.LandmarkSmoother2D
import com.airgesture.control.filtering.Point3D
import com.airgesture.control.filtering.PoseGeometryEvidence
import com.airgesture.control.filtering.PoseGeometryEvidenceEvaluator
import com.airgesture.control.pointer.ClickHysteresisStateMachine
import com.airgesture.control.pointer.SwipeDirection
import com.airgesture.control.pointer.SwipeGestureEngine

data class ProcessedGestureResult(
    val smoothedX: Float,
    val smoothedY: Float,
    val isClickEngaged: Boolean,
    val detectedSwipe: SwipeDirection,
    val normalizedDistance: Float,
    val poseEvidence: PoseGeometryEvidence
)

class GestureInterpreter(
    private val mappings: ActionMappingStore? = null,
    private val smoother: LandmarkSmoother2D = LandmarkSmoother2D(),
    private val validator: KinematicValidator = KinematicValidator(),
    private val clickStateMachine: ClickHysteresisStateMachine = ClickHysteresisStateMachine(),
    private val swipeEngine: SwipeGestureEngine = SwipeGestureEngine(),
    private val pointerFilter: AdaptiveKalmanFilter = AdaptiveKalmanFilter(),
    private val poseEvidenceEvaluator: PoseGeometryEvidenceEvaluator = PoseGeometryEvidenceEvaluator()
) {
    private var previousIndexTip: Point3D? = null

    fun interpret(signal: GestureSignal): GestureDecision {
        val source = when (signal.name.lowercase()) {
            "thumb_up", "thumbs_up" -> AirAction.TAP
            "victory", "peace" -> AirAction.BACK
            "open_palm", "open hand" -> AirAction.HOME
            "closed_fist", "fist" -> AirAction.RECENTS
            "pointing_up", "point" -> AirAction.DOUBLE_TAP
            else -> AirAction.NONE
        }
        return GestureDecision(mappings?.mapping(source) ?: source, signal.score)
    }

    fun processFrame(landmarks: List<Point3D>, timestampMs: Long, actionsAllowed: Boolean = true): ProcessedGestureResult? {
        if (landmarks.size <= KinematicValidator.INDEX_TIP) {
            reset()
            return null
        }
        val poseEvidence = poseEvidenceEvaluator.evaluate(landmarks)
        val rawIndexTip = landmarks[KinematicValidator.INDEX_TIP]
        // Invalid palm/pose geometry is unsuitable for constraining a valid fingertip.
        // Keep pointer feedback independent from the stronger evidence needed for actions.
        val constrainedTip = if (poseEvidence.accepted) {
            validator.validateAndConstrainIndexTip(landmarks, previousIndexTip)
        } else {
            rawIndexTip
        }
        if (!rawIndexTip.x.isFinite() || !rawIndexTip.y.isFinite() ||
            !constrainedTip.x.isFinite() || !constrainedTip.y.isFinite()
        ) {
            // NaN survives clamping and would poison both pointer filters across valid frames.
            reset()
            return null
        }
        val smoothedPoint = smoother.filter(constrainedTip.x, constrainedTip.y, timestampMs)
        val stabilized = pointerFilter.filter(smoothedPoint.x, smoothedPoint.y, timestampMs)
        previousIndexTip = constrainedTip
        val dNorm = validator.calculateNormalizedFingerDistance(landmarks).coerceIn(0f, 1.5f)
        if (!poseEvidence.accepted || !actionsAllowed) {
            // Rejected geometry or blocked actions must not carry dwell/swipe history forward.
            clickStateMachine.reset()
            swipeEngine.reset()
            return ProcessedGestureResult(
                stabilized.x,
                stabilized.y,
                false,
                SwipeDirection.NONE,
                dNorm.takeIf { it.isFinite() } ?: 1.5f,
                poseEvidence
            )
        }
        val clickState = clickStateMachine.processFrame(dNorm, timestampMs)
        val swipeState = swipeEngine.processFrame(Point3D(stabilized.x, stabilized.y, rawIndexTip.z), timestampMs)
        return ProcessedGestureResult(stabilized.x, stabilized.y, clickState, swipeState, dNorm, poseEvidence)
    }

    fun reset() {
        previousIndexTip = null
        smoother.reset()
        resetActions()
        pointerFilter.reset()
    }

    fun resetActions() {
        clickStateMachine.reset()
        swipeEngine.reset()
    }
}

