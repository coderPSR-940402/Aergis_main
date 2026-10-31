package com.airgesture.control

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
import com.airgesture.control.filtering.PoseGeometryEvidenceEvaluator
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer.GestureRecognizerOptions
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.nio.ByteBuffer
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
    private val fingertipPatch = FingertipPatchTracker()
    private var patchEpoch = -1L
    private var reacquireFrom: PointerCoordinateMapper.Point? = null
    private var currentPixels: RgbaFrameData? = null
    private val pointerVisibility = PointerVisibilityGrace()
    private var lastAppliedCalibration: PointerCalibrationProfile? = null
    private var lastCalibrationContext: Pair<ControlHandPreference, Boolean>? = null
    private val interpreter = GestureInterpreter(mappings)
    private val pinchPointer = PointerPinchController()
    private val selectionPinch = com.airgesture.control.pointer.ClickHysteresisStateMachine()
    private val pinchValidator = KinematicValidator()
    private var lastPointerActionEpoch: Long? = null
    private val poseEvidenceEvaluator = PoseGeometryEvidenceEvaluator()
    private var recognizer: GestureRecognizer? = null
    private var recognizerDelegate = "UNAVAILABLE"
    private var handLandmarker: HandLandmarker? = null
    private var handLandmarkerDelegate = "UNAVAILABLE"
    private var handModelBuffer: ByteBuffer? = null
    private var handLandmarkerUnavailable = false
    private var activeDelegate = "UNAVAILABLE"
    private val lineageComparison = PointerLineageComparison()
    private var lastPointerOwnerId: String? = null
    private var lastPointerHandedness: String? = null
    private var lastFilterMode = PointerFilterMode.CURRENT
    private var lastPointerAt = 0L
    private var lastPointerObservedAt = 0L
    private var lastFrameRotationDegrees: Int? = null
    private var frameAspectRatio = 1f
    private var lastFrameAspectRatio: Float? = null
    private val handOwnership = HandOwnershipTracker()
    private val directMotion = DirectMotionController()
    private val staticConfirmation = StaticPoseConfirmation()
    private val gestureTransaction = GestureTransactionStateMachine()
    // Camera age remains bounded for every pointer sample. Classifier commands use the
    // stricter age limit; deliberate pointer pinches follow the displayed pointer stream.
    private val freshnessPolicy = VisionResultFreshnessPolicy(POINTER_MAX_AGE_MS, POINTER_MAX_GAP_MS)
    private var timeSource: FrameTimeSource? = null
    private var sensorTimeline: Boolean? = null
    @Volatile internal var cameraTimestampsRealtime = false
    private var lastSubmittedTimestampMs = Long.MIN_VALUE
    private var lastPipelineAgeMs = -1L
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
        PipelineMetrics.shared.reset()
    }

    private fun nowIn(source: FrameTimeSource): Long = when (source) {
        FrameTimeSource.CAMERA_ELAPSED_REALTIME -> SystemClock.elapsedRealtime()
        FrameTimeSource.CAMERA_UPTIME, FrameTimeSource.ANALYZER_UPTIME -> SystemClock.uptimeMillis()
    }

    private fun useLandmarksOnly(): Boolean = HandInferencePolicy.landmarksOnly(
        AirRuntime.gesturesEnabled, AirRuntime.controlMode,
        // A consumed custom occurrence still needs actual classifier None evidence to rearm.
        classifierRequired = gestureTransaction.currentState() != GestureTransactionStateMachine.State.NEUTRAL ||
            RegisteredGesture.entries.any { !it.isLandmarkPose && mappings.mapping(it) != AirAction.NONE })

    internal fun analyze(frame: CapturedCameraFrame) {
        if (closed.get()) return
        var diagnosticImage: RgbaFrameData? = null
        val diagnosticStartedAt = SystemClock.uptimeMillis()
        var diagnosticResult: GestureRecognizerResult? = null
        var diagnosticError: String? = null
        var handOnlyInference = false
        diagnosticTrace = if (TestingTools.needsFrames()) org.json.JSONObject() else null
        lastPipelineAgeMs = -1L
        try {
            val metrics = PipelineMetrics.shared
            val analysisStartUptime = SystemClock.uptimeMillis()
            metrics.recordAnalyzerFrame(analysisStartUptime)
            // One clock domain for the whole pipeline: the camera frame's own timestamp. Detected once
            // per session so frame age, inter-frame gaps and every filter dt describe physical time.
            val cameraNs = frame.timestampNs
            val useSensorTimeline = sensorTimeline ?: (cameraNs > 0L).also { sensorTimeline = it }
            if (useSensorTimeline && cameraNs <= 0L) {
                metrics.recordOutOfOrderSkip()
                return
            }
            val source = timeSource ?: CameraClock.detect(cameraNs, frame.receivedUptimeMs,
                frame.receivedElapsedMs, cameraTimestampsRealtime).also { timeSource = it }
            // Never change sample-clock origin mid-session after a missing timestamp.
            val timestamp = if (useSensorTimeline) CameraClock.sampleTimestampMs(cameraNs, frame.receivedUptimeMs)
                else frame.receivedUptimeMs
            fun frameAge() = CameraClock.ageMs(source, cameraNs, frame.receivedUptimeMs,
                SystemClock.uptimeMillis(), SystemClock.elapsedRealtime())
            val frameAgeAtStart = frameAge().coerceAtLeast(0L)
            // MediaPipe VIDEO mode needs strictly increasing timestamps; never submit older work.
            if (timestamp <= lastSubmittedTimestampMs) {
                metrics.recordOutOfOrderSkip()
                return
            }
            fun discardExpiredExposure(): Boolean {
                val age = frameAge().coerceAtLeast(0L)
                if (age <= POINTER_MAX_AGE_MS) return false
                // Already expired before conversion/native work; revoke action state now.
                synchronized(this) { if (!closed.get()) handlePointerGap(nowIn(source),
                    PointerTrackingPolicy.isActive(AirRuntime.pointerEnabled, AirRuntime.controlMode,
                        AirRuntime.state.value.foregroundContext.safety), true, "STALE_BEFORE_INFERENCE", PointerFeedback.VISION_REJECTED) }
                lastSubmittedTimestampMs = timestamp
                AirRuntime.recordVisionResult(freshnessPolicy.evaluate(timestamp, timestamp + age))
                metrics.recordRejection("STALE_BEFORE_INFERENCE")
                TestingTools.event("frame_skipped", org.json.JSONObject().put("reason", "STALE_BEFORE_INFERENCE")
                    .put("resultTimestampMs", timestamp).put("ageMs", age))
                return true
            }
            if (discardExpiredExposure()) return
            val landmarksOnly = useLandmarksOnly()
            ensureRecognizer(analysisStartUptime, landmarksOnly)
            val currentLandmarker = if (landmarksOnly && !handLandmarkerUnavailable) handLandmarker else null
            val currentRecognizer = if (currentLandmarker == null) recognizer else null
            if (currentLandmarker == null && currentRecognizer == null) return
            activeDelegate = if (currentLandmarker != null) handLandmarkerDelegate else recognizerDelegate
            handOnlyInference = currentLandmarker != null
            diagnosticTrace?.put("inferenceMode", if (currentLandmarker != null) "LANDMARKS_ONLY" else "GESTURE_RECOGNIZER")
            if (closed.get()) return
            lastSubmittedTimestampMs = timestamp
            val preStart = SystemClock.uptimeMillis()
            val image = frame.data
            diagnosticImage = image
            val mpImage = frame.createImage()
            val preEnd = SystemClock.uptimeMillis()
            try {
                val imageProcessingOptions = ImageProcessingOptions.builder()
                    .setRotationDegrees(image.rotationDegrees)
                    .build()
                // Lazy GPU/model initialization and conversion can outlive the
                // exposure's remaining budget. Skip this native call and drain the
                // latest waiting frame; never reset its timestamp to make it fresh.
                if (discardExpiredExposure() || closed.get()) return
                val result = if (currentLandmarker != null) LandmarkOnlyGestureResult(
                    currentLandmarker.detectForVideo(mpImage, imageProcessingOptions, timestamp))
                else currentRecognizer!!.recognizeForVideo(mpImage, imageProcessingOptions, timestamp)
                val inferenceEnd = SystemClock.uptimeMillis()
                if (currentLandmarker != null && !useLandmarksOnly()) {
                    // Commands were enabled during inference. Do not publish missing
                    // classifier evidence into the new authority epoch.
                    synchronized(this) { if (!closed.get()) resetTrackingState() }
                    return
                }
                diagnosticResult = result
                frameAspectRatio = CameraCoordinateTransform.uprightAspectRatio(image.width, image.height, image.rotationDegrees)
                currentPixels = image
                try { publishWithAge(result, timestamp, nowIn(source), image.rotationDegrees, frameAge()) }
                finally { currentPixels = null }
                com.airgesture.control.gaze.GazeLab.offer(image, timestamp, frameAge())
                val publishEnd = SystemClock.uptimeMillis()
                lastPipelineAgeMs = frameAge().coerceAtLeast(0L)
                metrics.recordFrame(publishEnd, frameAgeAtStart, preEnd - preStart,
                    inferenceEnd - preEnd, publishEnd - inferenceEnd, lastPipelineAgeMs)
                diagnosticTrace?.put("timing", org.json.JSONObject()
                    .put("timeSource", source.name).put("frameAgeAtStartMs", frameAgeAtStart)
                    .put("captureToAnalysisMs", analysisStartUptime - frame.receivedUptimeMs)
                    .put("preprocessMs", preEnd - preStart).put("inferenceMs", inferenceEnd - preEnd)
                    .put("publishMs", publishEnd - inferenceEnd).put("frameToPointerMs", lastPipelineAgeMs))
            } finally {
                mpImage.close()
            }
        } catch (t: Throwable) {
            diagnosticError = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "Gesture recognition failed for frame", t)
            runCatching { recognizer?.close() }
            recognizer = null
            runCatching { handLandmarker?.close() }
            handLandmarker = null
            handModelBuffer = null
            if (handOnlyInference) handLandmarkerUnavailable = true
            recognizerFailureCount = (recognizerFailureCount + 1).coerceAtMost(MAX_RETRY_FAILURES)
            nextRecognizerRetryAt = SystemClock.uptimeMillis() +
                VisionRetryPolicy.delayForFailure(recognizerFailureCount)
            synchronized(this) {
                if (!closed.get()) {
                    AirRuntime.visionError = diagnosticError
                    AirRuntime.visionReady = false
                    resetTrackingState()
                }
            }
        } finally {
            synchronized(this) {
                val image = diagnosticImage
                if (!closed.get() && image != null && TestingTools.needsFrames()) runCatching {
                    TestingTools.onFrame(context, image, diagnosticResult, diagnosticStartedAt,
                        SystemClock.uptimeMillis() - diagnosticStartedAt, diagnosticTrace, diagnosticError,
                        lastPipelineAgeMs)
                }.onFailure { Log.w(TAG, "Testing frame capture failed", it) }
            }
            diagnosticTrace = null
        }
    }

    private fun ensureRecognizer(now: Long, landmarksOnly: Boolean = false) {
        if (closed.get() || now < nextRecognizerRetryAt) return
        if (landmarksOnly && !handLandmarkerUnavailable && handLandmarker == null) {
            runCatching { handLandmarker = createHandLandmarker() }.onFailure {
                handLandmarkerUnavailable = true
                handModelBuffer = null
                Log.w(TAG, "Optional hand-only path unavailable; preserving full recognizer", it)
            }
        }
        if ((!landmarksOnly || handLandmarker == null) && recognizer == null) {
            recognizer = createRecognizerWithGpuFallback()
            recognizerDelegate = activeDelegate
        }
        recognizerFailureCount = 0
        nextRecognizerRetryAt = 0L
        synchronized(this) {
            if (!closed.get()) {
                AirRuntime.visionReady = true
                AirRuntime.visionError = null
            }
        }
    }

    private fun createHandLandmarker(): HandLandmarker {
        val model = handModelBuffer ?: HandModelBundle.extract(context.assets.open(MODEL_ASSET)).also { handModelBuffer = it }
        fun create(delegate: Delegate): HandLandmarker {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(model.duplicate().apply { rewind() })
                    .setDelegate(delegate).build())
                .setRunningMode(RunningMode.VIDEO).setNumHands(MAX_HANDS)
                .setMinHandDetectionConfidence(.45f).setMinHandPresenceConfidence(.4f)
                .setMinTrackingConfidence(.4f).build()
            return HandLandmarker.createFromOptions(context, options).also { handLandmarkerDelegate = delegate.name }
        }
        return runCatching { create(Delegate.GPU) }.getOrElse { create(Delegate.CPU) }
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

    @Synchronized
    private fun publish(
        result: GestureRecognizerResult,
        timestamp: Long,
        observedAtMs: Long,
        rotationDegrees: Int
    ) = publishWithAge(result, timestamp, observedAtMs, rotationDegrees, observedAtMs - timestamp)

    @Synchronized
    private fun publishWithAge(result: GestureRecognizerResult, timestamp: Long,
        observedAtMs: Long, rotationDegrees: Int, ageMs: Long) {
        if (closed.get()) return
        // Ordering and filter dt use the sensor clock. Age uses only comparable clocks;
        // UNKNOWN cameras have no reliable absolute exposure-to-delivery measurement.
        if (AirRuntime.controlMode != ControlMode.READY) AirRuntime.setCalibrationGesture(null)
        val freshness = freshnessPolicy.evaluate(timestamp, (timestamp + ageMs).coerceAtLeast(0L))
        diagnosticTrace?.put("freshness", org.json.JSONObject().put("reason", freshness.reason.name)
            .put("ageMs", freshness.ageMs).put("gapMs", freshness.gapMs))
        AirRuntime.recordVisionResult(freshness)
        if (!freshness.accepted) PipelineMetrics.shared.recordRejection("VISION_${freshness.reason.name}")
        // Bounded command latency includes sensor delivery and inference; cursor motion tolerates more.
        val actionsFresh = freshness.accepted &&
            freshness.ageMs <= VisionResultFreshnessPolicy.DEFAULT_MAX_AGE_MS
        diagnosticTrace?.put("actionsFresh", actionsFresh)
        if (!freshness.accepted) {
            Log.d(TAG, "Vision result rejected: ${freshness.reason}")
            handlePointerGap(observedAtMs,
                PointerTrackingPolicy.isActive(AirRuntime.pointerEnabled, AirRuntime.controlMode,
                    AirRuntime.state.value.foregroundContext.safety),
                coastAllowed = true, reason = "VISION_${freshness.reason.name}",
                feedback = PointerFeedback.VISION_REJECTED)
            return
        }
        if (lastFrameRotationDegrees != rotationDegrees || lastFrameAspectRatio != frameAspectRatio) {
            resetTrackingState()
            lastFrameRotationDegrees = rotationDegrees
            lastFrameAspectRatio = frameAspectRatio
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

        if (patchEpoch != AirRuntime.actionEpoch) {
            fingertipPatch.reset()
            reacquireFrom = null
        }
        val controlSafe = AirRuntime.controlMode == ControlMode.ARMED &&
            AirRuntime.state.value.foregroundContext.safety == ForegroundSafety.SAFE
        val pointerActive = PointerTrackingPolicy.isActive(
            AirRuntime.pointerEnabled, AirRuntime.controlMode,
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
        val categoryName = gesture?.categoryName()?.takeIf { it.isNotBlank() }
        var gestureName = categoryName ?: "None"
        // Missing labels are not a confident neutral classification.
        var gestureScore = if (categoryName != null) gesture?.score() ?: 0f else 0f
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
        if (handSelection != null) {
            val directionalEnabled = RegisteredGesture.entries.any { it.isDirectionalGun && mappings.mapping(it) != AirAction.NONE }
            val generic = LandmarkGestureRecognizer.recognize(reusablePointsList, frameAspectRatio)
            val directional = if (directionalEnabled) LandmarkGestureRecognizer.recognizeDirectional(reusablePointsList, frameAspectRatio, generic) else null
            val custom = directional?.takeIf {
                it.name == "Double_Gun_Ambiguous" || RegisteredGesture.fromSignal(it.name)?.let { pose -> mappings.mapping(pose) != AirAction.NONE } == true
            } ?: generic
            custom?.let {
                gestureName = it.name
                gestureScore = it.score
            }
        }
        AirRuntime.lastGesture = gestureName
        val selectedPoseEvidence = if (selectedHand != null) {
            poseEvidenceEvaluator.evaluateGesture(reusablePointsList, RegisteredGesture.fromSignal(gestureName), frameAspectRatio)
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
            fingertipPatch.reset()
            reacquireFrom = null
            resetPointerTouch()
            gestureTransaction.reset()
            directMotion.reset()
            staticConfirmation.reset()
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
            (commandOwnerId == null && selectedHand != null && lastPointerHandedness != null && selectedHandedness != lastPointerHandedness)
        val filterChanged = filterMode != lastFilterMode
        if (ownerChanged || filterChanged) {
            fingertipPatch.reset()
            reacquireFrom = null
            resetPointerTouch()
            interpreter.reset()
            lineageComparison.reset()
            pointerVisibility.reset()
            gestureTransaction.reset()
            directMotion.reset()
            staticConfirmation.reset()
            AirRuntime.invalidatePendingActions()
        }
        lastFilterMode = filterMode
        if (selectedHand != null) {
            if (ownerChanged) lastPointerOwnerId = null
            if (commandOwnerId != null) lastPointerOwnerId = commandOwnerId
            lastPointerHandedness = selectedHandedness
        }
        diagnosticTrace?.put("ownerChanged", ownerChanged)?.put("filterMode", filterMode.name)
        val commandTracking = actionsFresh && !mappingChanged && !ownerChanged && !filterChanged && CommandTrackingEligibility.isEligible(
            controlSafe = controlSafe,
            handSelected = handSelection != null,
            pointerEnabled = AirRuntime.pointerEnabled,
            indexTipPresent = indexTip != null,
            poseEvidence = selectedPoseEvidence
        )
        diagnosticTrace?.put("commandsAllowed", commandTracking)?.put("delegate", activeDelegate)
            ?.put("commandPoseReason", selectedPoseEvidence?.rejectionReason?.name ?: "NO_POSE")

        // Pinch control follows the visible cursor, while classifier commands retain the
        // stricter 350 ms rule. Ordered results over 600 ms old are rejected above; a gap
        // over 350 ms cannot carry a pointer-touch transaction into the next sample.
        // A pause/resume or permission/owner change between frames must not preserve
        // an open/contact history and replay it in the new authority epoch.
        if (lastPointerActionEpoch != AirRuntime.actionEpoch) {
            interpreter.resetActions()
            resetPointerTouch()
            lastPointerActionEpoch = AirRuntime.actionEpoch
        }
        val pointerEpoch = AirRuntime.actionEpoch
        // Only a target from this authority epoch may bridge a brief index refit.
        val refitAnchor = pinchPointer.refitAnchor(timestamp)
        val pointerTouchTracking = freshness.gapMs <= 350L && !mappingChanged && !ownerChanged && !filterChanged &&
            CommandTrackingEligibility.isEligible(controlSafe, handSelection != null,
                AirRuntime.pointerEnabled, indexTip != null,
                if (selectedHand != null) poseEvidenceEvaluator.evaluatePointer(reusablePointsList,
                    frameAspectRatio, requireIndexTip = refitAnchor == null) else null)
        diagnosticTrace?.put("pointerTouchAllowed", pointerTouchTracking && AirRuntime.pointerClickingEnabled)
        val motionWasActive = directMotion.active
        val motionCenter = reusablePointsList.getOrNull(0)?.let { Point3D(1f - it.x, it.y, it.z) }
        val motionScale = if (reusablePointsList.size > 17)
            reusablePointsList[5].distance2DTo(reusablePointsList[17], frameAspectRatio) else 0f
        val motionAction = directMotion.process(motionCenter, motionScale, frameAspectRatio, timestamp,
            commandOwnerId, AirRuntime.actionEpoch, commandTracking && AirRuntime.gesturesEnabled)
        if (motionAction != null) AirAccessibilityService.instance?.dispatch(motionAction)
        var pinchInProgress = motionWasActive
        if (pointerActive && indexTip != null) {
            val dragging = AirAccessibilityService.instance?.precisionDragActive == true
            val touchAllowed = pointerTouchTracking && AirRuntime.pointerClickingEnabled && !motionWasActive && !dragging && reacquireFrom == null
            // toUpright rotates x/y but preserves MediaPipe's sensor-width z.
            // Upright height equals sensor width for quarter-turn buffers.
            val depthScale = if (rotationDegrees == 90 || rotationDegrees == 270) 1f else frameAspectRatio
            val processed = interpreter.processFrame(reusablePointsList, timestamp,
                actionsAllowed = touchAllowed, aspectRatio = frameAspectRatio, depthScale = depthScale,
                allowIndexRefit = touchAllowed && refitAnchor != null) ?: run {
                handlePointerGap(observedAtMs, pointerActive, landmarks.size <= 1,
                    "NON_FINITE_OR_MISSING_TIP", PointerFeedback.INVALID_TIP)
                return
            }
            AirRuntime.recordPoseEvidence(processed.poseEvidence)
            val rawTip = reusablePointsList[INDEX_TIP]
            val tipUsable = rawTip.x in 0f..1f && rawTip.y in 0f..1f
            val mappedTip = pointerMapper.map(rawTip.x, rawTip.y, frameCalibration)
            val currentPoint = pointerMapper.map(processed.smoothedX, processed.smoothedY, frameCalibration)
            val comparison = if (tipUsable) lineageComparison.update(mappedTip, currentPoint, timestamp)
                else lineageComparison.latest ?: run {
                    handlePointerGap(observedAtMs, pointerActive, false, "INVALID_TIP", PointerFeedback.INVALID_TIP)
                    return
                }
            val targetCandidate = comparison.selected(filterMode)
            val fromFallback = reacquireFrom
            val candidate = if (fromFallback != null) {
                val dx = targetCandidate.x - fromFallback.x
                val dy = targetCandidate.y - fromFallback.y
                val distance = kotlin.math.hypot(dx, dy)
                val fraction = if (distance <= .04f) 1f else .04f / distance
                PointerCoordinateMapper.Point(fromFallback.x + dx * fraction, fromFallback.y + dy * fraction).also {
                    reacquireFrom = if (fraction == 1f) null else it
                }
            } else targetCandidate
            val trusted = tipUsable && (filterMode != PointerFilterMode.PRECISION || comparison.precisionTrusted)
            val anchorProtected = !trusted && refitAnchor != null && touchAllowed && processed.poseEvidence.accepted &&
                (processed.isPinchApproaching || processed.isPinchPressed)
            val targetTrusted = trusted || anchorProtected
            if (AirRuntime.actionEpoch != pointerEpoch) { resetTrackingState(); return }
            if (!tipUsable && !anchorProtected) {
                handlePointerGap(observedAtMs, pointerActive, false, "INVALID_TIP", PointerFeedback.INVALID_TIP)
                return
            }
            if (tipUsable) AirRuntime.setRawPointerState(rawTip.x, rawTip.y, trusted)
            else AirRuntime.rawPointerSnapshot().let { AirRuntime.setRawPointerState(it.x, it.y, false) }
            val stabilized = if (dragging && pointerTouchTracking && trusted) {
                AirRuntime.pointerInteraction = PointerInteractionPhase.DRAGGING
                candidate
            } else if (touchAllowed && processed.poseEvidence.accepted && targetTrusted) {
                val interaction = pinchPointer.update(if (anchorProtected) refitAnchor!! else candidate,
                    processed.isPinchApproaching, processed.isPinchPressed, timestamp, positionTrusted = trusted)
                pinchInProgress = processed.isPinchApproaching || processed.isClickEngaged
                AirRuntime.pointerInteraction = interaction.phase
                interaction.point
            } else if (touchAllowed && processed.poseEvidence.accepted && !trusted) {
                // Hold a suspect position without accepting a command. Separation may
                // release the visual anchor, but it never dispatches an action.
                pinchInProgress = true
                val heldPoint = AirRuntime.pointerSnapshot().let { PointerCoordinateMapper.Point(it.x, it.y) }
                if (!processed.isPinchPressed) {
                    pinchPointer.update(heldPoint, false, false, timestamp, positionTrusted = false)
                    AirRuntime.pointerInteraction = PointerInteractionPhase.AIMING
                }
                heldPoint
            } else {
                resetPointerTouch()
                candidate
            }
            fun point(p: PointerCoordinateMapper.Point) = org.json.JSONObject()
                .put("x", DiagnosticFrameData.number(p.x)).put("y", DiagnosticFrameData.number(p.y))
            diagnosticTrace?.put("uprightTip", point(PointerCoordinateMapper.Point(rawTip.x, rawTip.y)))
                ?.put("currentFilteredUpright", point(PointerCoordinateMapper.Point(processed.smoothedX, processed.smoothedY)))
                ?.put("comparison", org.json.JSONObject().put("mappedTip", point(mappedTip))
                    .put("current", point(comparison.current)).put("vc49", point(comparison.vc49))
                    .put("precision", point(comparison.precision)))
                ?.put("precisionTrusted", comparison.precisionTrusted)?.put("geometryAspectRatio", frameAspectRatio)
                ?.put("pointerInteraction", AirRuntime.pointerInteraction.name)
                ?.put("thumbMiddleDistance", DiagnosticFrameData.number(processed.normalizedDistance))
                ?.put("pinchPressed", processed.isPinchPressed)
                ?.put("cursorVisible", true)?.put("anchorProtected", anchorProtected)
                ?.put("pointerRejection", if (trusted) "NONE" else if (anchorProtected) "PINCH_ANCHOR_REFIT" else "POINTER_OUTLIER")
                ?.put("pointerRejectionDetail", org.json.JSONObject()
                    .put("reason", comparison.precisionRejection.name)
                    .put("jumpDistance", DiagnosticFrameData.number(comparison.precisionJumpDistance))
                    .put("jumpLimit", DiagnosticFrameData.number(comparison.precisionJumpLimit))
                    .put("dtMs", timestamp - (lastPointerAt.takeIf { it > 0L } ?: timestamp)))
            // Motion-only recovery follows trusted fingertip evidence, not whole-hand
            // gesture geometry. Unrelated off-screen landmarks must not erase the
            // fallback template; handlePointerGap still cancels every action channel.
            if (trusted && handSelection != null &&
                indexTip.x().isFinite() && indexTip.y().isFinite() &&
                indexTip.x() in 0f..1f && indexTip.y() in 0f..1f
            ) {
                currentPixels?.let { pixels ->
                    fingertipPatch.seedNormalized(pixels.width, pixels.height,
                        indexTip.x(), indexTip.y(),
                        { x, y -> luminance(pixels, x, y) })
                    patchEpoch = AirRuntime.actionEpoch
                }
            }
            if (trusted) {
                pointerVisibility.record(stabilized, observedAtMs)
                lastPointerAt = timestamp
                lastPointerObservedAt = observedAtMs
                PipelineMetrics.shared.recordAcceptedPointer(SystemClock.uptimeMillis())
            } else {
                if (anchorProtected) pointerVisibility.record(stabilized, observedAtMs)
                PipelineMetrics.shared.recordRejection("POINTER_OUTLIER_${comparison.precisionRejection.name}")
            }
            AirRuntime.pointerFeedback = if (targetTrusted) PointerFeedback.TRACKING else PointerFeedback.COASTING
            AirRuntime.setPointerState(stabilized.x, stabilized.y, targetTrusted)
            // Recognition initiates one bounded tap. Release only rearms. The same
            // frame's pinch evidence excludes the classifier before its dispatch gate.
            if (touchAllowed && targetTrusted && processed.isClickEngaged) {
                AirAccessibilityService.instance?.dispatchPointerTap(pointerEpoch)
            }

            // Pointer travel and held contact never dispatch scrolling or drag.
            AirAccessibilityService.instance?.updatePointer(
                stabilized.x,
                stabilized.y,
                true,
                processed.isPinchPressed
            )
            PipelineMetrics.shared.recordCursorUpdate(SystemClock.uptimeMillis())
        } else if (AirRuntime.controlMode == ControlMode.READY &&
            AirRuntime.state.value.foregroundContext.safety == ForegroundSafety.SAFE &&
            freshness.accepted && handSelection != null && indexTip != null &&
            reusablePointsList[INDEX_TIP].let { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f }) {
            // Read-only calibration uses the bounded tracking stream, independent of
            // pointer/action switches and the stricter command age. READY never grants pointer or action
            // authority; retain hand ownership so a stable preview can be acquired.
            resetPointerTouch()
            interpreter.reset()
            lineageComparison.reset()
            pointerVisibility.reset()
            lastPointerAt = 0L
            lastPointerObservedAt = 0L
            val rawTip = reusablePointsList[INDEX_TIP]
            AirRuntime.setRawPointerState(rawTip.x, rawTip.y, true)
            if (actionsFresh && poseEvidenceEvaluator.evaluatePointer(reusablePointsList, frameAspectRatio).accepted) {
                val validator = com.airgesture.control.filtering.KinematicValidator()
                val depthScale = if (rotationDegrees % 180 != 0) 1f else frameAspectRatio
                AirRuntime.setCalibrationGesture(CalibrationGestureFrame(timestamp, AirRuntime.actionEpoch,
                    handSelection.ownerId, rawTip.x, rawTip.y,
                    validator.calculateNormalizedFingerDistance(reusablePointsList, frameAspectRatio),
                    validator.calculateNormalizedFingerDepthDistance(reusablePointsList, frameAspectRatio, depthScale),
                    if (selectedPoseEvidence?.accepted == true && gestureScore >= .7f) gestureName else "None",
                    // UI delivery-age checks use uptime even when camera timing includes sleep.
                    observedAtMs = SystemClock.uptimeMillis()))
            } else AirRuntime.setCalibrationGesture(null)
            AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
            AirRuntime.pointerFeedback = PointerFeedback.DISABLED
            AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
            diagnosticTrace?.put("pointerRejection", "CALIBRATION_PREVIEW_ONLY")?.put("cursorVisible", false)
        } else if (!AirRuntime.pointerEnabled && controlSafe && handSelection != null) {
            // A hidden cursor uses the explicit highlighted selection, never stale coordinates.
            // Contact evidence needs thumb/middle/palm only; the pointing index may refit.
            val selectionTouchAllowed = actionsFresh && freshness.gapMs <= 350L &&
                !mappingChanged && !ownerChanged && !filterChanged && AirRuntime.pointerClickingEnabled &&
                !motionWasActive && AirAccessibilityService.instance?.nativeTouchInFlight != true &&
                poseEvidenceEvaluator.evaluatePointer(reusablePointsList, frameAspectRatio, requireIndexTip = false).accepted
            diagnosticTrace?.put("selectionPinchAllowed", selectionTouchAllowed)
            if (selectionTouchAllowed) {
                val depthScale = if (rotationDegrees % 180 != 0) 1f else frameAspectRatio
                val clicked = selectionPinch.processFrame(
                    pinchValidator.calculateNormalizedFingerDistance(reusablePointsList, frameAspectRatio), timestamp,
                    pinchValidator.calculateNormalizedFingerDepthDistance(reusablePointsList, frameAspectRatio, depthScale))
                pinchInProgress = selectionPinch.isPointerLocked
                diagnosticTrace?.put("pinchPressed", selectionPinch.getCurrentState() ==
                    com.airgesture.control.pointer.ClickHysteresisStateMachine.State.CLICKED)
                if (clicked) AirAccessibilityService.instance?.dispatchSelectionPinch(pointerEpoch)
            } else selectionPinch.reset()
            pinchPointer.reset()
            AirRuntime.pointerInteraction = PointerInteractionPhase.IDLE
            interpreter.reset()
            lineageComparison.reset()
            pointerVisibility.reset()
            lastPointerAt = 0L
            lastPointerObservedAt = 0L
            AirRuntime.setRawPointerState(AirRuntime.rawPointerSnapshot().x, AirRuntime.rawPointerSnapshot().y, false)
            AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
            AirRuntime.pointerFeedback = PointerFeedback.DISABLED
            AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
            diagnosticTrace?.put("pointerRejection", "POINTER_DISABLED")?.put("cursorVisible", false)
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

        val registeredGesture = RegisteredGesture.fromSignal(gestureName)
        val gestureTuning = registeredGesture?.let(mappings::tuning)?.let {
            // Landmark poses use geometric evidence, not a classifier confidence score.
            if (registeredGesture?.isLandmarkPose == true) it.copy(confidencePercent = 65) else it
        }
        // Keep recognition identity separate from action availability. A disabled,
        // unknown or pointer-restricted pose is not neutral. Only an actual confident
        // MediaPipe None category may supply release evidence (missing categories score 0).
        val mappedDecision = interpreter.interpret(GestureSignal(gestureName, gestureScore))
        val decision = mappedDecision.copy(action = GestureActionPolicy.resolve(mappedDecision.action, pointerActive))
        val retainClassifierReadiness = actionsFresh && !mappingChanged && !ownerChanged && !filterChanged &&
            CommandTrackingEligibility.canRetainReadiness(controlSafe, handSelection != null,
                AirRuntime.pointerEnabled, indexTip != null, selectedPoseEvidence)
        val confirmedPose = staticConfirmation.accept(registeredGesture?.name ?: gestureName.takeUnless { it == "None" },
            commandOwnerId, AirRuntime.actionEpoch, timestamp,
            commandTracking && !pinchInProgress && gestureScore.isFinite() &&
                gestureScore >= (gestureTuning?.confidencePercent ?: 65) / 100f,
            gestureTuning?.holdMs ?: 100L)
        val confirmedAction = gestureTransaction.process(
            GestureTransactionStateMachine.Input(
                action = decision.action,
                confidence = gestureScore,
                timestampMs = timestamp,
                tracking = (commandTracking || retainClassifierReadiness) && AirRuntime.gesturesEnabled && !pinchInProgress,
                ownershipId = commandOwnerId,
                gestureId = registeredGesture?.name ?: gestureName.takeUnless { it == "None" },
                tuning = gestureTuning,
                actionEpoch = AirRuntime.actionEpoch,
                evidenceValid = commandTracking,
                confirmationReady = confirmedPose,
                occurrenceSuppressed = pinchInProgress
            )
        )
        if (confirmedAction != null &&
            GestureActionPolicy.isClassifierActionEnabled(AirRuntime.gesturesEnabled, confirmedAction, pointerActive, registeredGesture)
        ) {
            if (confirmedAction == AirAction.ARM_MOTION && commandOwnerId != null) {
                if (AirAccessibilityService.instance?.nativeTouchInFlight == true) {
                    AirRuntime.recordActionDispatchOutcome(ActionDispatchOutcome.PLATFORM_REJECTED)
                    return
                }
                directMotion.arm(commandOwnerId, AirRuntime.actionEpoch, timestamp)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, R.string.motion_armed_help, android.widget.Toast.LENGTH_SHORT).show()
                }
                resetPointerTouch()
                interpreter.resetActions()
            } else AirAccessibilityService.instance?.dispatch(confirmedAction, gestureTuning ?: GestureTuning())
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
        val observations = landmarks.mapIndexedNotNull { index, hand ->
            if (hand.size <= PINKY_MCP) return@mapIndexedNotNull null
            // Palm anchors do not include fingertips that move/refit during contact.
            // A close or clipped hand can lose its wrist while both palm-width
            // anchors remain reliable. Keep identity independent of unused joints;
            // per-gesture and pinch evidence still apply their own visibility gates.
            val palm = intArrayOf(INDEX_MCP, PINKY_MCP).map { joint ->
                val raw = hand[joint]
                CameraCoordinateTransform.toUpright(raw.x(), raw.y(), raw.z(), rotationDegrees)
            }
            val centerX = palm.sumOf { it.x.toDouble() }.toFloat() / palm.size
            val centerY = palm.sumOf { it.y.toDouble() }.toFloat() / palm.size
            val palmSize = palm[0].distance2DTo(palm[1], frameAspectRatio)
            if (!centerX.isFinite() || !centerY.isFinite() || !palmSize.isFinite() || palmSize <= 0f) {
                return@mapIndexedNotNull null
            }
            HandObservation(
                index = index,
                handedness = physicalHandedness.getOrNull(index) ?: "Unknown",
                centerX = centerX,
                centerY = centerY,
                palmSize = palmSize
            )
        }
        return handOwnership.select(observations, AirRuntime.handPreference, timestampMs)
    }

    /** Retention changes cursor visibility only. Missing evidence immediately cancels all actions. */
    private fun handlePointerGap(now: Long, pointerActive: Boolean, coastAllowed: Boolean,
        reason: String, feedback: PointerFeedback) {
        AirRuntime.setCalibrationGesture(null)
        val pixels = currentPixels
        val fallback = if (reason == "RAW_LANDMARK_DROPOUT" && pointerActive && coastAllowed &&
            pixels != null && patchEpoch == AirRuntime.actionEpoch)
            fingertipPatch.track(pixels.width, pixels.height) { x, y -> luminance(pixels, x, y) } else null
        if (fallback == null) fingertipPatch.reset()
        resetPointerTouch()
        AirRuntime.invalidatePendingActions(clearSelection = false)
        patchEpoch = AirRuntime.actionEpoch
        interpreter.resetActions()
        gestureTransaction.reset()
            directMotion.reset()
            staticConfirmation.reset()
        if (fallback != null && pixels != null) {
            val upright = CameraCoordinateTransform.toUpright(fallback.x, fallback.y, 0f, pixels.rotationDegrees)
            val mapped = pointerMapper.map(upright.x, upright.y, pointerMapper.snapshot())
            AirRuntime.setRawPointerState(upright.x, upright.y, false)
            reacquireFrom = mapped
            AirRuntime.setPointerState(mapped.x, mapped.y, false)
            AirRuntime.pointerFeedback = PointerFeedback.COASTING
            AirAccessibilityService.instance?.updatePointer(mapped.x, mapped.y, true, false)
            diagnosticTrace?.put("pointerRejection", "PATCH_MOTION_ONLY")?.put("cursorVisible", true)
            AirRuntime.clearPoseEvidence()
            return
        }
        val held = if (pointerActive && coastAllowed) pointerVisibility.heldAt(now) else null
        AirRuntime.setRawPointerState(AirRuntime.rawPointerSnapshot().x, AirRuntime.rawPointerSnapshot().y, false)
        if (held != null) {
            reacquireFrom = held // Motion catches up in bounded steps; touch history was revoked above.
            AirRuntime.setPointerState(held.x, held.y, false)
            AirRuntime.pointerFeedback = if (feedback == PointerFeedback.VISION_REJECTED) feedback else PointerFeedback.COASTING
            AirAccessibilityService.instance?.updatePointer(held.x, held.y, true, false)
        } else {
            // Visibility grace follows cadence; keep same-owner filter history for 500 ms.
            // Identity, calibration, filter and safety changes explicitly clear history.
            if (!pointerActive || !coastAllowed || lastPointerAt == 0L || now - lastPointerObservedAt > 500L) {
                resetTrackingState(preserveSelection = true, preserveOwnership =
                    AirRuntime.controlMode == ControlMode.ARMED && AirRuntime.state.value.foregroundContext.safety == ForegroundSafety.SAFE)
            }
            AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
            AirRuntime.pointerFeedback = feedback
            AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
        }
        diagnosticTrace?.put("pointerRejection", reason)?.put("cursorVisible", held != null)
            ?.put("coasting", held != null)
        AirRuntime.clearPoseEvidence()
    }

    private fun luminance(frame: RgbaFrameData, x: Int, y: Int): Float {
        val i = y * frame.rowStride + x * frame.pixelStride
        return ((frame.buffer.get(i).toInt() and 255) * .299f +
            (frame.buffer.get(i + 1).toInt() and 255) * .587f +
            (frame.buffer.get(i + 2).toInt() and 255) * .114f)
    }

    private fun resetTrackingState(preserveSelection: Boolean = false, preserveOwnership: Boolean = false) {
        AirRuntime.setCalibrationGesture(null)
        fingertipPatch.reset()
        reacquireFrom = null
        resetPointerTouch()
        if (AirRuntime.pointerTracking) AirRuntime.invalidatePendingActions(clearSelection = !preserveSelection)
        pointerVisibility.reset()
        AirRuntime.pointerFeedback = PointerFeedback.NO_HAND
        AirRuntime.setRawPointerState(AirRuntime.rawPointerSnapshot().x, AirRuntime.rawPointerSnapshot().y, false)
        lastPointerAt = 0L
        lastPointerObservedAt = 0L
        if (!preserveOwnership) {
            handOwnership.reset()
            lastPointerOwnerId = null
            lastPointerHandedness = null
        }
        lineageComparison.reset()
        interpreter.reset()
        gestureTransaction.reset()
            directMotion.reset()
            staticConfirmation.reset()
        AirRuntime.clearPoseEvidence()
        AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
        AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
    }

    private fun resetPointerTouch() {
        pinchPointer.reset()
        selectionPinch.reset()
        AirRuntime.pointerInteraction = PointerInteractionPhase.IDLE
        AirAccessibilityService.instance?.cancelPointerTouch()
    }

    /** Invalidate publication immediately; never dispose a native recognizer from this thread. */
    @Synchronized
    fun stop() {
        if (closed.compareAndSet(false, true)) {
            freshnessPolicy.reset()
            resetTrackingState()
            AirRuntime.visionReady = false
        }
    }

    /** Call on the analyzer executor, after any synchronous inference has finished. */
    override fun close() {
        stop()
        try { recognizer?.close() } finally {
            recognizer = null
            try { handLandmarker?.close() } finally { handLandmarker = null; handModelBuffer = null }
        }
        nextRecognizerRetryAt = 0L
    }

    companion object {
        private const val TAG = "GestureRecognitionEngine"
        private const val MODEL_ASSET = "gesture_recognizer.task"
        private const val MIN_GESTURE_SCORE = 0.65f
        private const val MAX_HANDS = 2
        private const val MAX_RETRY_FAILURES = 8
        /** Real camera-frame age / spacing beyond which a result is too old to move the cursor. */
        private const val POINTER_MAX_AGE_MS = 600L
        private const val POINTER_MAX_GAP_MS = 500L
        private const val INDEX_MCP = KinematicValidator.INDEX_MCP
        private const val INDEX_TIP = KinematicValidator.INDEX_TIP
        private const val PINKY_MCP = KinematicValidator.PINKY_MCP
    }
}
