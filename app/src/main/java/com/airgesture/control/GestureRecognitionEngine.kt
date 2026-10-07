package com.airgesture.control

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import com.airgesture.control.filtering.PoseGeometryEvidenceEvaluator
import com.airgesture.control.pointer.SwipeDirection
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer.GestureRecognizerOptions
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.util.concurrent.atomic.AtomicBoolean

/** On-device MediaPipe gesture and hand-landmark inference. */
class GestureRecognitionEngine(private val context: Context) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val mappings = ActionMappingStore(context)
    private val calibrationStore = PointerCalibrationStore(context)
    private val pointerMapper = LivePointerMapper {
        calibrationStore.activeProfile(
            AirRuntime.handPreference,
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        )
    }
    private val pointerVisibility = PointerVisibilityGrace()
    private var lastAppliedCalibration: PointerCalibrationProfile? = null
    private var lastCalibrationContext: Pair<ControlHandPreference, Boolean>? = null
    private val interpreter = GestureInterpreter(mappings)
    private val poseEvidenceEvaluator = PoseGeometryEvidenceEvaluator()
    private var recognizer: GestureRecognizer? = null
    private var activeDelegate = "UNAVAILABLE"
    private val lineageComparison = PointerLineageComparison()
    private var lastPointerOwnerId: String? = null
    private var lastPointerHandedness: String? = null
    private var lastFilterMode = PointerFilterMode.CURRENT
    private var lastPointerAt = 0L
    private var lastFrameRotationDegrees: Int? = null
    private val handOwnership = HandOwnershipTracker()
    private val gestureTransaction = GestureTransactionStateMachine()
    private val freshnessPolicy = VisionResultFreshnessPolicy()
    private var recognizerFailureCount = 0
    private var nextRecognizerRetryAt = 0L
    private var diagnosticTrace: org.json.JSONObject? = null

    // Reusable buffers to avoid heap allocations per frame
    private val reusablePointsList = ArrayList<Point3D>(21)
    private val handednessList = ArrayList<String>(2)

    init {
        AirRuntime.visionReady = false
        AirRuntime.visionError = null
        AirRuntime.resetVisionTelemetry()
    }

    @ExperimentalGetImage
    fun analyze(image: ImageProxy) {
        if (closed.get()) return
        val diagnosticStartedAt = SystemClock.uptimeMillis()
        var diagnosticResult: GestureRecognizerResult? = null
        var diagnosticError: String? = null
        diagnosticTrace = if (TestingTools.needsFrames()) org.json.JSONObject() else null
        try {
            val timestamp = SystemClock.uptimeMillis()
            ensureRecognizer(timestamp)
            val currentRecognizer = recognizer ?: return
            val mediaImage = image.image ?: return
            val mpImage = MediaImageBuilder(mediaImage).build()
            try {
                val imageProcessingOptions = ImageProcessingOptions.builder()
                    .setRotationDegrees(image.imageInfo.rotationDegrees)
                    .build()
                val result = currentRecognizer.recognizeForVideo(
                    mpImage,
                    imageProcessingOptions,
                    timestamp
                )
                diagnosticResult = result
                publish(result, timestamp, SystemClock.uptimeMillis(), image.imageInfo.rotationDegrees)
            } finally {
                mpImage.close()
            }
        } catch (t: Throwable) {
            diagnosticError = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "Gesture recognition failed for frame", t)
            AirRuntime.visionError = t.message ?: t.javaClass.simpleName
            AirRuntime.visionReady = false
            runCatching { recognizer?.close() }
            recognizer = null
            recognizerFailureCount = (recognizerFailureCount + 1).coerceAtMost(MAX_RETRY_FAILURES)
            nextRecognizerRetryAt = SystemClock.uptimeMillis() +
                VisionRetryPolicy.delayForFailure(recognizerFailureCount)
            resetTrackingState()
        } finally {
            if (TestingTools.needsFrames()) runCatching {
                TestingTools.onFrame(context, image, diagnosticResult, diagnosticStartedAt,
                    SystemClock.uptimeMillis() - diagnosticStartedAt, diagnosticTrace, diagnosticError)
            }.onFailure { Log.w(TAG, "Testing frame capture failed", it) }
            diagnosticTrace = null
        }
    }

    private fun ensureRecognizer(now: Long) {
        if (recognizer != null || closed.get() || now < nextRecognizerRetryAt) return
        recognizer = createRecognizerWithGpuFallback()
        recognizerFailureCount = 0
        nextRecognizerRetryAt = 0L
        AirRuntime.visionReady = true
        AirRuntime.visionError = null
    }

    private fun createRecognizerWithGpuFallback(): GestureRecognizer {
        return runCatching { createRecognizer(Delegate.GPU) }
            .onFailure { Log.w(TAG, "GPU delegate unavailable; falling back to CPU", it) }
            .getOrElse { createRecognizer(Delegate.CPU) }
    }

    private fun createRecognizer(delegate: Delegate): GestureRecognizer {
        val options = GestureRecognizerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(delegate)
                    .build()
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(MAX_HANDS)
            .setMinHandDetectionConfidence(0.45f)
            .setMinHandPresenceConfidence(0.4f)
            .setMinTrackingConfidence(0.4f)
            .build()
        return GestureRecognizer.createFromOptions(context, options).also { activeDelegate = delegate.name }
    }

    private fun publish(
        result: GestureRecognizerResult,
        timestamp: Long,
        observedAtMs: Long,
        rotationDegrees: Int
    ) {
        val freshness = freshnessPolicy.evaluate(timestamp, observedAtMs)
        diagnosticTrace?.put("freshness", org.json.JSONObject().put("reason", freshness.reason.name)
            .put("ageMs", freshness.ageMs).put("gapMs", freshness.gapMs))
        AirRuntime.recordVisionResult(freshness)
        if (!freshness.accepted) {
            Log.d(TAG, "Vision result rejected: ${freshness.reason}")
            handlePointerGap(observedAtMs,
                PointerTrackingPolicy.isActive(AirRuntime.pointerEnabled, AirRuntime.controlMode,
                    AirRuntime.motionActive, AirRuntime.state.value.foregroundContext.safety),
                coastAllowed = true, reason = "VISION_${freshness.reason.name}",
                feedback = PointerFeedback.VISION_REJECTED)
            return
        }
        if (lastFrameRotationDegrees != rotationDegrees) {
            resetTrackingState()
            lastFrameRotationDegrees = rotationDegrees
            Log.d(TAG, "Camera landmark rotation: $rotationDegrees degrees clockwise")
        }
        val landmarks = result.landmarks()
        AirRuntime.handsDetected = landmarks.size

        // Populate handedness without creating new list/string objects
        handednessList.clear()
        val rawHandedness = result.handedness()
        for (i in rawHandedness.indices) {
            val category = rawHandedness[i].firstOrNull()?.categoryName()
            val mapped = when (category) {
                "Left" -> "Right"
                "Right" -> "Left"
                else -> "Unknown"
            }
            handednessList.add(mapped)
        }
        AirRuntime.handedness = handednessList.firstOrNull() ?: "Unknown"

        val controlSafe = AirRuntime.controlMode == ControlMode.ARMED &&
            !AirRuntime.motionActive &&
            AirRuntime.state.value.foregroundContext.safety == ForegroundSafety.SAFE
        val pointerActive = PointerTrackingPolicy.isActive(
            AirRuntime.pointerEnabled, AirRuntime.controlMode, AirRuntime.motionActive,
            AirRuntime.state.value.foregroundContext.safety
        )
        val handSelection = selectPointerHand(landmarks, handednessList, timestamp, rotationDegrees)
        val pointerHandIndex = handSelection?.index ?: PointerHandFallback.selectIndex(
            landmarks.mapIndexed { index, hand ->
                val tip = hand.getOrNull(INDEX_TIP)
                PointerHandCandidate(
                    index,
                    handednessList.getOrNull(index) ?: "Unknown",
                    tip?.let { Point3D(it.x(), it.y(), it.z()) }
                )
            },
            AirRuntime.handPreference
        )
        val selectedHand = pointerHandIndex?.let(landmarks::getOrNull)
        val gesture = OwnedGestureEvidenceSelector.select(
            result.gestures(),
            handSelection?.index
        )
        val gestureName = gesture?.categoryName()?.takeIf { it.isNotBlank() } ?: "None"
        val gestureScore = gesture?.score() ?: 0f
        AirRuntime.lastGesture = gestureName
        val indexTip = selectedHand?.getOrNull(INDEX_TIP)
        val commandOwnerId = handSelection?.ownerId
        diagnosticTrace?.put("selectedHandIndex", pointerHandIndex ?: org.json.JSONObject.NULL)
            ?.put("commandOwnerId", commandOwnerId ?: org.json.JSONObject.NULL)

        reusablePointsList.clear()
        if (selectedHand != null) {
            for (i in selectedHand.indices) {
                val lm = selectedHand[i]
                reusablePointsList.add(
                    CameraCoordinateTransform.toUpright(lm.x(), lm.y(), lm.z(), rotationDegrees)
                )
            }
        }
        val selectedPoseEvidence = if (selectedHand != null) {
            poseEvidenceEvaluator.evaluate(reusablePointsList)
        } else {
            null
        }
        if (selectedPoseEvidence != null) {
            AirRuntime.recordPoseEvidence(selectedPoseEvidence)
        } else {
            AirRuntime.clearPoseEvidence()
        }
        val frameCalibration = pointerMapper.snapshot()
        val calibrationContext = AirRuntime.handPreference to
            (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val mappingChanged = frameCalibration != lastAppliedCalibration || calibrationContext != lastCalibrationContext
        if (mappingChanged) {
            gestureTransaction.reset()
            pointerVisibility.reset()
            interpreter.reset()
            lineageComparison.reset()
            AirRuntime.invalidatePendingActions()
            lastAppliedCalibration = frameCalibration
            lastCalibrationContext = calibrationContext
        }
        val filterMode = TestingTools.state.value.filterMode
        val selectedHandedness = pointerHandIndex?.let { handednessList.getOrNull(it) } ?: "Unknown"
        val ownerChanged = (commandOwnerId != null && lastPointerOwnerId != null && commandOwnerId != lastPointerOwnerId) ||
            (selectedHand != null && lastPointerHandedness != null && selectedHandedness != lastPointerHandedness)
        val filterChanged = filterMode != lastFilterMode
        if (ownerChanged || filterChanged) {
            interpreter.reset()
            lineageComparison.reset()
            pointerVisibility.reset()
            gestureTransaction.reset()
            AirRuntime.invalidatePendingActions()
        }
        lastFilterMode = filterMode
        if (selectedHand != null) {
            if (ownerChanged) lastPointerOwnerId = null
            if (commandOwnerId != null) lastPointerOwnerId = commandOwnerId
            lastPointerHandedness = selectedHandedness
        }
        diagnosticTrace?.put("ownerChanged", ownerChanged)?.put("filterMode", filterMode.name)
        val commandTracking = !mappingChanged && !ownerChanged && !filterChanged && CommandTrackingEligibility.isEligible(
            controlSafe = controlSafe,
            handSelected = handSelection != null,
            pointerEnabled = AirRuntime.pointerEnabled,
            indexTipPresent = indexTip != null,
            poseEvidence = selectedPoseEvidence
        )
        diagnosticTrace?.put("commandsAllowed", commandTracking)?.put("delegate", activeDelegate)
            ?.put("commandPoseReason", selectedPoseEvidence?.rejectionReason?.name ?: "NO_POSE")

        if (pointerActive && indexTip != null) {
            val processed = interpreter.processFrame(reusablePointsList, timestamp, actionsAllowed = commandTracking) ?: run {
                handlePointerGap(observedAtMs, pointerActive, landmarks.size <= 1,
                    "NON_FINITE_OR_MISSING_TIP", PointerFeedback.INVALID_TIP)
                return
            }
            AirRuntime.recordPoseEvidence(processed.poseEvidence)
            val rawTip = reusablePointsList[INDEX_TIP]
            AirRuntime.setRawPointerState(rawTip.x, rawTip.y, true)
            val mappedTip = pointerMapper.map(rawTip.x, rawTip.y, frameCalibration)
            val currentPoint = pointerMapper.map(processed.smoothedX, processed.smoothedY, frameCalibration)
            val comparison = lineageComparison.update(mappedTip, currentPoint, timestamp)
            val stabilized = comparison.selected(filterMode)
            fun point(p: PointerCoordinateMapper.Point) = org.json.JSONObject()
                .put("x", DiagnosticFrameData.number(p.x)).put("y", DiagnosticFrameData.number(p.y))
            diagnosticTrace?.put("uprightTip", point(PointerCoordinateMapper.Point(rawTip.x, rawTip.y)))
                ?.put("currentFilteredUpright", point(PointerCoordinateMapper.Point(processed.smoothedX, processed.smoothedY)))
                ?.put("comparison", org.json.JSONObject().put("mappedTip", point(mappedTip))
                    .put("current", point(comparison.current)).put("vc49", point(comparison.vc49)))
                ?.put("cursorVisible", true)?.put("pointerRejection", "NONE")
            pointerVisibility.record(stabilized, observedAtMs)
            AirRuntime.pointerFeedback = PointerFeedback.TRACKING
            lastPointerAt = timestamp
            AirRuntime.setPointerState(stabilized.x, stabilized.y, true)

            if (commandTracking && processed.poseEvidence.accepted &&
                processed.isClickEngaged &&
                GestureActionPolicy.isPointerActionEnabled(AirRuntime.gesturesEnabled, AirAction.TAP)
            ) {
                AirAccessibilityService.instance?.dispatch(AirAction.TAP)
            }
            val swipeAction = when (processed.detectedSwipe) {
                SwipeDirection.UP -> AirAction.SCROLL_UP
                SwipeDirection.DOWN -> AirAction.SCROLL_DOWN
                SwipeDirection.LEFT,
                SwipeDirection.RIGHT,
                SwipeDirection.NONE -> AirAction.NONE
            }
            if (commandTracking && processed.poseEvidence.accepted &&
                GestureActionPolicy.isPointerActionEnabled(AirRuntime.gesturesEnabled, swipeAction)
            ) {
                AirAccessibilityService.instance?.dispatch(swipeAction)
            }

            AirAccessibilityService.instance?.updatePointer(
                stabilized.x,
                stabilized.y,
                true,
                processed.isClickEngaged
            )
        } else {
            val reason = when {
                !pointerActive -> "POINTER_SAFETY_OR_DISABLED"
                landmarks.isEmpty() -> "RAW_LANDMARK_DROPOUT"
                selectedHand != null -> "MISSING_TIP"
                else -> "HAND_SELECTION_REJECTED"
            }
            handlePointerGap(observedAtMs, pointerActive,
                landmarks.isEmpty() || (selectedHand != null && landmarks.size <= 1), reason,
                when {
                    !pointerActive -> PointerFeedback.DISABLED
                    landmarks.size > 1 -> PointerFeedback.AMBIGUOUS
                    else -> PointerFeedback.NO_HAND
                })
        }

        val decision = if (AirRuntime.gesturesEnabled && gestureName != "None") {
            interpreter.interpret(GestureSignal(gestureName, gestureScore))
        } else {
            GestureDecision(AirAction.NONE, 0f)
        }
        val confirmedAction = gestureTransaction.process(
            GestureTransactionStateMachine.Input(
                action = decision.action,
                confidence = decision.confidence,
                timestampMs = timestamp,
                tracking = commandTracking,
                ownershipId = commandOwnerId
            )
        )
        if (confirmedAction != null &&
            GestureActionPolicy.isClassifierActionEnabled(AirRuntime.gesturesEnabled, confirmedAction)
        ) {
            AirAccessibilityService.instance?.dispatch(confirmedAction)
        }
        AirRuntime.visionError = null
        AirRuntime.visionReady = true
    }

    private fun selectPointerHand(
        landmarks: List<List<NormalizedLandmark>>,
        physicalHandedness: List<String>,
        timestampMs: Long,
        rotationDegrees: Int
    ): HandOwnershipTracker.Selection? {
        if (landmarks.isEmpty()) return null
        val observations = landmarks.mapIndexedNotNull { index, hand ->
            if (hand.size <= PINKY_MCP) return@mapIndexedNotNull null
            val rawWrist = hand[WRIST]
            val wrist = CameraCoordinateTransform.toUpright(
                rawWrist.x(), rawWrist.y(), rawWrist.z(), rotationDegrees
            )
            val indexMcp = hand[INDEX_MCP]
            val pinkyMcp = hand[PINKY_MCP]
            val palmSize = Point3D(indexMcp.x(), indexMcp.y()).distance2DTo(
                Point3D(pinkyMcp.x(), pinkyMcp.y())
            )
            if (!wrist.x.isFinite() || !wrist.y.isFinite() || !palmSize.isFinite() || palmSize <= 0f) {
                return@mapIndexedNotNull null
            }
            HandObservation(
                index = index,
                handedness = physicalHandedness.getOrNull(index) ?: "Unknown",
                centerX = wrist.x,
                centerY = wrist.y,
                palmSize = palmSize
            )
        }
        return handOwnership.select(observations, AirRuntime.handPreference, timestampMs)
    }

    /** Retention changes cursor visibility only. Missing evidence immediately cancels all actions. */
    private fun handlePointerGap(now: Long, pointerActive: Boolean, coastAllowed: Boolean,
        reason: String, feedback: PointerFeedback) {
        AirRuntime.invalidatePendingActions()
        interpreter.resetActions()
        gestureTransaction.reset()
        val held = if (pointerActive && coastAllowed) pointerVisibility.heldAt(now) else null
        AirRuntime.setRawPointerState(AirRuntime.rawPointerSnapshot().x, AirRuntime.rawPointerSnapshot().y, false)
        if (held != null) {
            AirRuntime.setPointerState(held.x, held.y, false)
            AirRuntime.pointerFeedback = if (feedback == PointerFeedback.VISION_REJECTED) feedback else PointerFeedback.COASTING
            AirAccessibilityService.instance?.updatePointer(held.x, held.y, true, false)
        } else {
            // Hide after 130 ms, but keep same-owner filter history for up to 500 ms.
            // Identity, calibration, filter and safety changes explicitly clear history.
            if (!pointerActive || !coastAllowed || lastPointerAt == 0L || now - lastPointerAt > 500L) {
                resetTrackingState()
            }
            AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
            AirRuntime.pointerFeedback = feedback
            AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
        }
        diagnosticTrace?.put("pointerRejection", reason)?.put("cursorVisible", held != null)
            ?.put("coasting", held != null)
        AirRuntime.clearPoseEvidence()
    }

    private fun resetTrackingState() {
        if (AirRuntime.pointerTracking) AirRuntime.invalidatePendingActions()
        pointerVisibility.reset()
        AirRuntime.pointerFeedback = PointerFeedback.NO_HAND
        AirRuntime.setRawPointerState(AirRuntime.rawPointerSnapshot().x, AirRuntime.rawPointerSnapshot().y, false)
        lastPointerAt = 0L
        handOwnership.reset()
        lastPointerOwnerId = null
        lastPointerHandedness = null
        lineageComparison.reset()
        interpreter.reset()
        gestureTransaction.reset()
        AirRuntime.clearPoseEvidence()
        AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
        AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            recognizer?.close()
            recognizer = null
            nextRecognizerRetryAt = 0L
            freshnessPolicy.reset()
            resetTrackingState()
            AirRuntime.visionReady = false
        }
    }

    companion object {
        private const val TAG = "GestureRecognitionEngine"
        private const val MODEL_ASSET = "gesture_recognizer.task"
        private const val MIN_GESTURE_SCORE = 0.65f
        private const val MAX_HANDS = 2
        private const val MAX_RETRY_FAILURES = 8
        private const val WRIST = KinematicValidator.WRIST
        private const val INDEX_MCP = KinematicValidator.INDEX_MCP
        private const val INDEX_TIP = KinematicValidator.INDEX_TIP
        private const val PINKY_MCP = KinematicValidator.PINKY_MCP
    }
}
