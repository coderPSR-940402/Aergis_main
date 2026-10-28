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
    val poseEvidence: PoseGeometryEvidence,
    val isPinchApproaching: Boolean = false,
    val isPinchPressed: Boolean = false
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
    private var lastStablePointer: Point3D? = null

    fun interpret(signal: GestureSignal): GestureDecision {
        val gesture = RegisteredGesture.fromSignal(signal.name)
            ?: return GestureDecision(AirAction.NONE, signal.score)
        return GestureDecision(mappings?.mapping(gesture) ?: gesture.defaultAction, signal.score)
    }

    fun processFrame(landmarks: List<Point3D>, timestampMs: Long, actionsAllowed: Boolean = true,
        aspectRatio: Float = 1f, depthScale: Float = aspectRatio, allowIndexRefit: Boolean = false): ProcessedGestureResult? {
        if (landmarks.size <= KinematicValidator.INDEX_TIP) {
            resetActions()
            return null
        }
        val poseEvidence = poseEvidenceEvaluator.evaluatePointer(landmarks, aspectRatio, requireIndexTip = !allowIndexRefit)
        val rawIndexTip = landmarks[KinematicValidator.INDEX_TIP]
        val refitting = allowIndexRefit && (rawIndexTip.x !in 0f..1f || rawIndexTip.y !in 0f..1f)
        // Position authority is exactly landmark 8; anatomy belongs to the action channel.
        if ((!rawIndexTip.x.isFinite() || !rawIndexTip.y.isFinite()) && (!refitting || lastStablePointer == null)) {
            resetActions()
            return null
        }
        val stabilized = if (refitting && lastStablePointer != null) lastStablePointer!! else {
            val smoothedPoint = smoother.filter(rawIndexTip.x, rawIndexTip.y, timestampMs)
            pointerFilter.filter(smoothedPoint.x, smoothedPoint.y, timestampMs).also { lastStablePointer = it }
        }
        val dNorm = validator.calculateNormalizedFingerDistance(landmarks, aspectRatio).coerceIn(0f, 1.5f)
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
        val depthSeparation = validator.calculateNormalizedFingerDepthDistance(landmarks, aspectRatio, depthScale)
        val clickState = clickStateMachine.processFrame(dNorm, timestampMs, depthSeparation)
        val approaching = clickStateMachine.isPointerLocked
        val pressed = clickStateMachine.getCurrentState() == ClickHysteresisStateMachine.State.CLICKED
        val swipeState = if (approaching) {
            swipeEngine.reset()
            SwipeDirection.NONE
        } else swipeEngine.processFrame(Point3D(stabilized.x, stabilized.y, rawIndexTip.z), timestampMs)
        return ProcessedGestureResult(stabilized.x, stabilized.y, clickState, swipeState, dNorm,
            poseEvidence, approaching, pressed)
    }

    fun reset() {
        lastStablePointer = null
        smoother.reset()
        resetActions()
        pointerFilter.reset()
    }

    fun resetActions() {
        clickStateMachine.reset()
        swipeEngine.reset()
    }
}

