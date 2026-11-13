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
    // A confirmation is a one-shot event. Keep it briefly if the actual visible
    // pointer hotspot was not dispatchable in the confirmation frame.
    private var pendingTapAt: Long? = null
    private var lastEvidenceAt: Long? = null

    val hasPendingTap: Boolean get() = pendingTapAt != null

    /** A confirmed contact is a one-shot tap even if it is released before the overlay catches up.
     * Never create a tap from release: only a prior observed and confirmed contact can be pending.
     * The engine independently requires the same visible frozen hotspot and live action authority.
     */
    fun takePendingTap(timestampMs: Long, targetDispatchable: Boolean): Boolean {
        val confirmedAt = pendingTapAt ?: return false
        if (timestampMs < confirmedAt || timestampMs - confirmedAt > PENDING_TAP_MAX_MS) {
            pendingTapAt = null
            return false
        }
        if (!targetDispatchable) return false
        pendingTapAt = null
        return true
    }

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
        // A duplicate, regressing, or discontinuous camera timestamp cannot carry a
        // confirmed tap forward into a new inference sequence.
        val previousEvidence = lastEvidenceAt
        if (timestampMs < 0L || (previousEvidence != null &&
                (timestampMs <= previousEvidence || timestampMs - previousEvidence > 350L)))
            resetActions()
        lastEvidenceAt = timestampMs
        val poseEvidence = poseEvidenceEvaluator.evaluatePointer(landmarks, aspectRatio, requireIndexTip = !allowIndexRefit)
        val rawIndexTip = landmarks[KinematicValidator.INDEX_TIP]
        val tipInFrame = rawIndexTip.x in 0f..1f && rawIndexTip.y in 0f..1f
        val refitting = allowIndexRefit && !tipInFrame
        // Position authority is exactly landmark 8; anatomy belongs to the action channel.
        // Finite off-frame refits are invalid measurements too. Reject them before
        // either filter learns a bad position/velocity and moves the next valid tip.
        if (!tipInFrame && (!refitting || lastStablePointer == null)) {
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
            pendingTapAt = null
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
        if (clickState) pendingTapAt = timestampMs
        else if (pendingTapAt?.let { timestampMs < it || timestampMs - it > PENDING_TAP_MAX_MS } == true) {
            // Keep a previously CONFIRMED click across an ordinary release, never
            // across invalid evidence, a timestamp discontinuity, or the delivery budget.
            pendingTapAt = null
        }
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
        pendingTapAt = null
        lastEvidenceAt = null
        swipeEngine.reset()
    }

    private companion object {
        const val PENDING_TAP_MAX_MS = 250L
    }
}

