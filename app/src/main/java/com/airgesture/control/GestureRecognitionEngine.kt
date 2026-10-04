package com.airgesture.control

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.airgesture.control.filtering.KinematicValidator
import com.airgesture.control.filtering.Point3D
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
    private val interpreter = GestureInterpreter(mappings)
    private var recognizer: GestureRecognizer? = null
    private var pointerInitialized = false
    private var lastPointerAt = 0L
    private val handOwnership = HandOwnershipTracker()
    private val gestureTransaction = GestureTransactionStateMachine()
    private val freshnessPolicy = VisionResultFreshnessPolicy()
    private var recognizerFailureCount = 0
    private var nextRecognizerRetryAt = 0L

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
                publish(result, timestamp, SystemClock.uptimeMillis())
            } finally {
                mpImage.close()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Gesture recognition failed for frame", t)
            AirRuntime.visionError = t.message ?: t.javaClass.simpleName
            AirRuntime.visionReady = false
            runCatching { recognizer?.close() }
            recognizer = null
            recognizerFailureCount = (recognizerFailureCount + 1).coerceAtMost(MAX_RETRY_FAILURES)
            nextRecognizerRetryAt = SystemClock.uptimeMillis() +
                VisionRetryPolicy.delayForFailure(recognizerFailureCount)
            resetTrackingState()
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
        return GestureRecognizer.createFromOptions(context, options)
    }

    private fun publish(
        result: GestureRecognizerResult,
        timestamp: Long,
        observedAtMs: Long
    ) {
        val freshness = freshnessPolicy.evaluate(timestamp, observedAtMs)
        AirRuntime.recordVisionResult(freshness)
        if (!freshness.accepted) {
            Log.d(TAG, "Vision result rejected: ${freshness.reason}")
            resetTrackingState()
            return
        }
        val landmarks = result.landmarks()
        AirRuntime.handsDetected = landmarks.size

        val gesture = result.gestures().firstOrNull()?.firstOrNull()
        val gestureName = gesture?.categoryName()?.takeIf { it.isNotBlank() } ?: "None"
        val gestureScore = gesture?.score() ?: 0f
        AirRuntime.lastGesture = gestureName

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
        val pointerActive = AirRuntime.pointerEnabled && controlSafe
        val handSelection = selectPointerHand(landmarks, handednessList, timestamp)
        val selectedHand = handSelection?.index?.let(landmarks::getOrNull)
        val indexTip = selectedHand?.getOrNull(INDEX_TIP)
        val commandOwnerId = handSelection?.ownerId
        val commandTracking = controlSafe && landmarks.isNotEmpty() &&
            handSelection != null && (!AirRuntime.pointerEnabled || indexTip != null)

        if (pointerActive && indexTip != null) {
            // Re-use landmark points list buffer
            reusablePointsList.clear()
            for (i in selectedHand.indices) {
                val lm = selectedHand[i]
                reusablePointsList.add(Point3D(lm.x(), lm.y(), lm.z()))
            }

            val processed = interpreter.processFrame(reusablePointsList, timestamp) ?: run {
                resetTrackingState()
                return
            }
            // GestureInterpreter owns the single latency-bounded pointer filter. Applying
            // another filter here doubled lag and made fast motion appear to freeze.
            val stabilized = PointerCoordinateMapper.map(processed.smoothedX, processed.smoothedY)
            pointerInitialized = true
            lastPointerAt = timestamp
            AirRuntime.setPointerState(stabilized.x, stabilized.y, true)

            if (processed.isClickEngaged &&
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
            if (GestureActionPolicy.isPointerActionEnabled(AirRuntime.gesturesEnabled, swipeAction)) {
                AirAccessibilityService.instance?.dispatch(swipeAction)
            }

            AirAccessibilityService.instance?.updatePointer(
                stabilized.x,
                stabilized.y,
                true,
                processed?.isClickEngaged == true
            )
        } else {
            pointerInitialized = false
            lastPointerAt = 0L
            interpreter.reset()
            if (!commandTracking) {
                handOwnership.reset()
                gestureTransaction.reset()
            }
            AirRuntime.setPointerState(AirRuntime.pointerX, AirRuntime.pointerY, false)
            AirAccessibilityService.instance?.updatePointer(0f, 0f, false)
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
        timestampMs: Long
    ): HandOwnershipTracker.Selection? {
        if (landmarks.isEmpty()) return null
        val observations = landmarks.mapIndexedNotNull { index, hand ->
            if (hand.size <= PINKY_MCP) return@mapIndexedNotNull null
            val wrist = hand[WRIST]
            val indexMcp = hand[INDEX_MCP]
            val pinkyMcp = hand[PINKY_MCP]
            val palmSize = Point3D(indexMcp.x(), indexMcp.y()).distance2DTo(
                Point3D(pinkyMcp.x(), pinkyMcp.y())
            )
            HandObservation(
                index = index,
                handedness = physicalHandedness.getOrNull(index) ?: "Unknown",
                centerX = wrist.x(),
                centerY = wrist.y(),
                palmSize = palmSize
            )
        }
        return handOwnership.select(observations, AirRuntime.handPreference, timestampMs)
    }

    private fun resetTrackingState() {
        pointerInitialized = false
        lastPointerAt = 0L
        handOwnership.reset()
        interpreter.reset()
        gestureTransaction.reset()
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
