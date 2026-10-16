package com.airgesture.control

import android.content.Context
import com.google.mediapipe.formats.proto.ClassificationProto
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalibrationEngineIntegrationTest {
    private lateinit var engine: GestureRecognitionEngine
    private lateinit var store: PointerCalibrationStore

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("aergis_calibration", Context.MODE_PRIVATE).edit().clear().commit()
        store = PointerCalibrationStore(context)
        TestingTools.setFilterMode(PointerFilterMode.CURRENT)
        AirRuntime.pointerEnabled = true
        AirRuntime.gesturesEnabled = false
        AirRuntime.handPreference = ControlHandPreference.EITHER
        AirRuntime.controlMode = ControlMode.ARMED
        AirRuntime.setForegroundContext(ForegroundContextState(safety = ForegroundSafety.SAFE))
        engine = GestureRecognitionEngine(context)
    }

    @After
    fun tearDown() {
        engine.close()
        TestingTools.setFilterMode(PointerFilterMode.CURRENT)
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.pointerEnabled = true
        AirRuntime.gesturesEnabled = true
        AirRuntime.setForegroundContext(ForegroundContextState())
    }

    @Test
    fun actualEngineUpdatesCalibrationAndDisableWhileRunning() {
        publish(hand(), 1000L)
        assertTrue(AirRuntime.pointerY < 0.8f)
        store.save(PointerCalibrationProfile(top = 0.15f, bottom = 0.65f))
        publish(hand(), 1033L)
        assertEquals(1f, AirRuntime.pointerY, 0.0001f)
        store.setEnabled(false)
        publish(hand(), 1066L)
        assertTrue(AirRuntime.pointerY < 0.8f)
        assertTrue(AirRuntime.pointerTracking)
    }

    @Test
    fun stoppedEngineCannotPublishLateInferenceIntoANewSession() {
        publish(hand(), 1000L)
        engine.close()
        AirRuntime.visionReady = false
        AirRuntime.setPointerState(.2f, .3f, false)
        val epoch = AirRuntime.actionEpoch
        publish(hand(), 1033L)
        assertFalse("Late inference must not reactivate stopped vision", AirRuntime.visionReady)
        assertFalse(AirRuntime.pointerTracking)
        assertEquals(.2f, AirRuntime.pointerX, 0f)
        assertEquals(epoch, AirRuntime.actionEpoch)
    }

    @Test
    fun actualEngineCoastsBrieflyWithoutFreshTrackingOrQueuedActions() {
        publish(hand(), 1000L)
        val last = AirRuntime.pointerSnapshot()
        val epoch = AirRuntime.actionEpoch
        publish(emptyList(), 1033L)
        assertEquals(PointerFeedback.COASTING, AirRuntime.pointerFeedback)
        assertFalse(AirRuntime.pointerTracking)
        assertFalse(AirRuntime.rawPointerSnapshot().tracking)
        assertEquals(last.x, AirRuntime.pointerX, 0f)
        assertEquals(last.y, AirRuntime.pointerY, 0f)
        assertTrue(AirRuntime.actionEpoch > epoch)
        publish(emptyList(), 1131L)
        assertEquals(PointerFeedback.NO_HAND, AirRuntime.pointerFeedback)
        assertFalse(AirRuntime.pointerTracking)
    }

    @Test
    fun actualEngineDoesNotCoastAcrossSafetyGateOrRejectedFrame() {
        publish(hand(), 1000L)
        AirRuntime.controlMode = ControlMode.PAUSED
        publish(emptyList(), 1033L)
        assertEquals(PointerFeedback.DISABLED, AirRuntime.pointerFeedback)
        AirRuntime.controlMode = ControlMode.ARMED
        publish(hand(), 1066L)
        publish(emptyList(), 1066L)
        assertEquals(PointerFeedback.VISION_REJECTED, AirRuntime.pointerFeedback)
        assertFalse(AirRuntime.pointerTracking)
    }

    @Test
    fun isolatedInvalidTipCoastsWithoutFreshActionEvidence() {
        publish(hand(), 1000L)
        val previous = AirRuntime.pointerSnapshot()
        val epoch = AirRuntime.actionEpoch
        publish(hand().toMutableList().apply {
            this[8] = NormalizedLandmark.create(Float.NaN, 0.65f, 0f)
        }, 1033L)
        assertEquals(PointerFeedback.COASTING, AirRuntime.pointerFeedback)
        assertFalse(AirRuntime.pointerTracking)
        assertEquals(previous.x, AirRuntime.pointerX, 0f)
        assertEquals(previous.y, AirRuntime.pointerY, 0f)
        assertTrue(AirRuntime.actionEpoch > epoch)
        publish(hand(), 1066L)
        assertTrue(AirRuntime.pointerTracking)
    }

    @Test
    fun replacingHandDoesNotInheritMotionFilterPosition() {
        publish(hand(), 1000L)
        val other = hand().toMutableList().apply { this[8] = NormalizedLandmark.create(0.8f, 0.2f, 0f) }
        publish(other, 1033L, "Right")
        val expected = PointerCoordinateMapper.map(0.8f, 0.2f)
        assertEquals(expected.x, AirRuntime.pointerX, 0.0001f)
        assertEquals(expected.y, AirRuntime.pointerY, 0.0001f)
    }

    @Test
    fun vc49SameHandRecoveryAfterVisibilityGraceRetainsShockGuard() {
        TestingTools.setFilterMode(PointerFilterMode.VC49)
        publish(hand(), 1000L)
        val previous = AirRuntime.pointerSnapshot()
        publish(emptyList(), 1131L)
        assertEquals(PointerFeedback.NO_HAND, AirRuntime.pointerFeedback)
        publish(hand().toMutableList().apply { this[8] = NormalizedLandmark.create(0.8f, 0.65f, 0f) }, 1200L)
        assertTrue(AirRuntime.pointerTracking)
        assertTrue("Same-hand reacquisition must pass through shock guard", kotlin.math.abs(AirRuntime.pointerX - previous.x) < 0.06f)
    }

    @Test
    fun changingFilterUsesOnlyItsOwnNewMeasurementState() {
        publish(hand(), 1000L)
        TestingTools.setFilterMode(PointerFilterMode.VC49)
        publish(hand().toMutableList().apply { this[8] = NormalizedLandmark.create(0.8f, 0.65f, 0f) }, 1033L)
        assertEquals(PointerCoordinateMapper.map(0.8f, 0.65f).x, AirRuntime.pointerX, 0.0001f)
    }

    @Test
    fun gestureOnlyModeConfirmsOnceAndPreservesCooldownUntilNeutral() {
        AirRuntime.pointerEnabled = false
        AirRuntime.gesturesEnabled = true
        repeat(6) { publish(hand(), 1000L + it * 33, gestureName = "Victory") }
        val field = GestureRecognitionEngine::class.java.getDeclaredField("gestureTransaction").apply { isAccessible = true }
        val transaction = field.get(engine) as GestureTransactionStateMachine
        assertEquals(GestureTransactionStateMachine.State.COOLDOWN, transaction.currentState())
        repeat(30) { publish(hand(), 1200L + it * 33, gestureName = "Victory") }
        assertEquals(GestureTransactionStateMachine.State.COOLDOWN, transaction.currentState())
        assertFalse(AirRuntime.pointerTracking)
        assertNotNull(AirRuntime.state.value.poseEvidence)
        publish(hand(), 2200L)
        publish(hand(), 2233L)
        assertEquals("Two frames are not a deliberate release", GestureTransactionStateMachine.State.COOLDOWN, transaction.currentState())
        publish(hand(), 2266L)
        publish(hand(), 2300L)
        assertEquals(GestureTransactionStateMachine.State.NEUTRAL, transaction.currentState())
        repeat(5) { publish(hand(), 2333L + it * 33, gestureName = "Open_Palm") }
        assertEquals(GestureTransactionStateMachine.State.COOLDOWN, transaction.currentState())
        AirRuntime.controlMode = ControlMode.PAUSED
        publish(hand(), 2500L, gestureName = "Victory")
        assertEquals("Pause must not rearm a held command", GestureTransactionStateMachine.State.COOLDOWN, transaction.currentState())
        AirRuntime.controlMode = ControlMode.ARMED
        repeat(20) { publish(hand(), 2550L + it * 40, gestureName = "Victory") }
        assertEquals(GestureTransactionStateMachine.State.COOLDOWN, transaction.currentState())
        publish(hand(), 3350L)
        publish(hand(), 3450L)
        assertEquals(GestureTransactionStateMachine.State.NEUTRAL, transaction.currentState())
        publish(hand(), 3500L, gestureName = "Victory")
        publish(hand(), 3600L, gestureName = "Victory")
        assertEquals(GestureTransactionStateMachine.State.COOLDOWN, transaction.currentState())
    }

    @Test
    fun precisionModeHoldsAnIsolatedTipSpikeAndRecoversWithoutChangingCalibration() {
        TestingTools.setFilterMode(PointerFilterMode.PRECISION)
        publish(hand(), 1000L)
        val before = AirRuntime.pointerSnapshot()
        publish(hand().toMutableList().apply { this[8] = NormalizedLandmark.create(.85f, .65f, 0f) }, 1033L)
        assertFalse(AirRuntime.pointerTracking)
        assertEquals(before.x, AirRuntime.pointerX, .0001f)
        publish(hand(), 1066L)
        assertTrue(AirRuntime.pointerTracking)
        assertEquals(before.x, AirRuntime.pointerX, .0001f)
    }

    @Test
    fun rejectedTipKeepsTheDisplayedPinchAnchorRatherThanTheUnanchoredFilterPoint() {
        TestingTools.setFilterMode(PointerFilterMode.PRECISION)
        AirRuntime.gesturesEnabled = true
        repeat(8) { publish(hand(), 1000L + it * 33L) }
        // Pinch anchoring may hold a displayed point behind the selected filter.
        AirRuntime.setPointerState(.42f, .55f, true)
        publish(hand().toMutableList().apply { this[8] = NormalizedLandmark.create(.65f, .5f, 0f) }, 1264L)
        assertFalse(AirRuntime.pointerTracking)
        assertEquals(.42f, AirRuntime.pointerX, .0001f)
        assertEquals(.55f, AirRuntime.pointerY, .0001f)
    }

    @Test
    fun readyModeSuppliesCalibrationSamplesWithoutPointerOrActionAuthority() {
        AirRuntime.controlMode = ControlMode.READY
        repeat(6) { publish(hand(), 1000L + it * 33L) }
        assertTrue(AirRuntime.rawPointerSnapshot().tracking)
        assertEquals(.5f, AirRuntime.rawPointerSnapshot().x, .0001f)
        assertEquals(.65f, AirRuntime.rawPointerSnapshot().y, .0001f)
        assertFalse(AirRuntime.pointerTracking)
        assertEquals(PointerInteractionPhase.IDLE, AirRuntime.pointerInteraction)
        assertFalse(ActionSafetyPolicy.evaluate(AirAction.TAP, true, AirRuntime.controlMode,
            ForegroundSafety.SAFE).allowed)
        publish(emptyList(), 1200L)
        assertFalse(AirRuntime.rawPointerSnapshot().tracking)
    }

    @Test fun fourCornerCalibrationWorksWithActionsAndPointerOffAtTrackingLatency() {
        AirRuntime.controlMode = ControlMode.READY
        AirRuntime.pointerEnabled = false
        AirRuntime.gesturesEnabled = false
        val session = PointerCalibrationSession().apply { start() }
        var timestamp = 1000L
        for ((x, y) in listOf(.2f to .2f, .8f to .2f, .8f to .8f, .2f to .8f)) {
            val corner = hand().toMutableList().apply { this[8] = NormalizedLandmark.create(x, y, 0f) }
            repeat(4) {
                publish(corner, timestamp, delayMs = 500L)
                timestamp += 134L
            }
            val sample = AirRuntime.rawPointerSnapshot()
            assertTrue("Read-only calibration must not require action freshness or enabled pointer", sample.tracking)
            assertTrue(session.addSample(sample.x, sample.y))
            assertFalse(AirRuntime.pointerTracking)
            assertEquals(PointerInteractionPhase.IDLE, AirRuntime.pointerInteraction)
        }
        assertEquals(4, session.sampleCount())
        assertNotNull(session.complete())
        publish(hand(), timestamp, delayMs = 601L)
        assertFalse("Calibration must still discard truly stale frames", AirRuntime.rawPointerSnapshot().tracking)
    }

    @Test
    fun calibrationPreviewRejectsProtectedContext() {
        AirRuntime.controlMode = ControlMode.READY
        repeat(6) { publish(hand(), 1000L + it * 33L) }
        assertTrue(AirRuntime.rawPointerSnapshot().tracking)
        AirRuntime.setForegroundContext(ForegroundContextState(safety = ForegroundSafety.PROTECTED))
        publish(hand(), 1233L)
        assertFalse(AirRuntime.rawPointerSnapshot().tracking)
        assertFalse(AirRuntime.pointerTracking)
    }

    @Test
    fun slowCameraPinchWithCroppedWristDispatchesExactlyOneNativeTap() {
        assertSlowCameraPinchDispatchesOneTap(hand().toMutableList().apply {
            this[0] = NormalizedLandmark.create(.5f, 1.02f, 0f)
        })
    }

    @Test
    fun slowCameraPinchWithExtendedIndexDispatchesExactlyOneNativeTap() {
        assertSlowCameraPinchDispatchesOneTap(hand().toMutableList().apply {
            // 1.75 times palm width is a plausible extended index, not a broken pinch.
            this[8] = NormalizedLandmark.create(.4f, .15f, 0f)
        })
    }

    @Test fun trackedPinchCanHoldThenDragAndReleaseOneNativeTouch() {
        val controller = org.robolectric.Robolectric.buildService(AirAccessibilityService::class.java).create()
        val service = controller.get()
        AirAccessibilityService::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, service)
        try {
            AirRuntime.gesturesEnabled = true
            AirRuntime.resetActionDispatchTelemetry()
            val shadow = org.robolectric.Shadows.shadowOf(service)
            val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            var timestamp = 1000L
            var completedChunks = 0
            fun frame(points: List<NormalizedLandmark>) {
                publish(points, timestamp, delayMs = 250L)
                timestamp += 134L
                looper.idle()
                while (completedChunks < shadow.gesturesDispatched.size) {
                    val chunk = shadow.gesturesDispatched[completedChunks++]
                    chunk.callback()!!.onCompleted(chunk.description())
                }
            }
            val open = hand()
            val closed = open.toMutableList().apply { this[4] = NormalizedLandmark.create(.56f, .65f, 0f) }
            repeat(4) { frame(open) }
            repeat(8) { frame(closed) }
            assertEquals(PointerInteractionPhase.HOLDING, AirRuntime.pointerInteraction)
            assertEquals("Hold must not dispatch empty continuations", 1, shadow.gesturesDispatched.size)
            val moved = closed.toMutableList().apply { this[8] = NormalizedLandmark.create(.5f, .85f, 0f) }
            repeat(12) { frame(moved) }
            assertEquals(PointerInteractionPhase.DRAGGING, AirRuntime.pointerInteraction)
            assertTrue(shadow.gesturesDispatched.drop(1).any {
                val bounds = android.graphics.RectF()
                it.description().getStroke(0).path.computeBounds(bounds, true)
                bounds.height() > 0f
            })
            frame(moved.toMutableList().apply { this[4] = open[4] })
            assertFalse(shadow.gesturesDispatched.last().description().getStroke(0).willContinue())
            assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
            assertEquals(0L, AirRuntime.state.value.actionDispatchTelemetry.cancelled)
        } finally { controller.destroy() }
    }

    private fun assertSlowCameraPinchDispatchesOneTap(open: List<NormalizedLandmark>) {
        val controller = org.robolectric.Robolectric.buildService(AirAccessibilityService::class.java).create()
        val service = controller.get()
        val instance = AirAccessibilityService::class.java.getDeclaredField("instance").apply { isAccessible = true }
        instance.set(null, service)
        try {
            AirRuntime.gesturesEnabled = true
            AirRuntime.resetActionDispatchTelemetry()
            val shadow = org.robolectric.Shadows.shadowOf(service)
            val closed = open.toMutableList().apply { this[4] = NormalizedLandmark.create(.56f, .65f, 0f) }
            publish(open, 1000L, delayMs = 500L)
            publish(open, 1200L, delayMs = 500L)
            publish(closed, 1400L, delayMs = 500L)
            assertEquals(PointerInteractionPhase.PINCHING, AirRuntime.pointerInteraction)
            val anchor = AirRuntime.pointerSnapshot()
            publish(open, 1600L, delayMs = 500L)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("The full camera-to-accessibility path must request a tap", 1, shadow.gesturesDispatched.size)
            val tap = shadow.gesturesDispatched.single()
            assertFalse("A short pinch must be a complete tap, not a stuck held touch", tap.description().getStroke(0).willContinue())
            tap.callback()!!.onCompleted(tap.description())
            assertEquals(1L, AirRuntime.state.value.actionDispatchTelemetry.completed)
            assertEquals(anchor.x, AirRuntime.pointerX, .0001f)
            assertEquals(anchor.y, AirRuntime.pointerY, .0001f)
            publish(open, 1800L, delayMs = 500L)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1, shadow.gesturesDispatched.size)
        } finally { controller.destroy() }
    }

    @Test
    fun delayedPointerPinchCancelsAcrossStaleResultAndDisarm() {
        AirRuntime.gesturesEnabled = true
        val open = hand()
        val closed = open.toMutableList().apply { this[4] = NormalizedLandmark.create(.56f, .65f, 0f) }
        publish(open, 1000L, delayMs = 500L)
        publish(open, 1200L, delayMs = 500L)
        publish(closed, 1400L, delayMs = 500L)
        assertEquals(PointerInteractionPhase.PINCHING, AirRuntime.pointerInteraction)
        publish(closed, 1600L, delayMs = 601L)
        assertEquals(PointerInteractionPhase.IDLE, AirRuntime.pointerInteraction)
        publish(closed, 1800L, delayMs = 500L)
        assertEquals("Recovery must observe open fingers again", PointerInteractionPhase.AIMING, AirRuntime.pointerInteraction)
        AirRuntime.controlMode = ControlMode.PAUSED
        publish(closed, 2000L, delayMs = 500L)
        assertEquals(PointerInteractionPhase.IDLE, AirRuntime.pointerInteraction)
        assertFalse(AirRuntime.pointerTracking)
    }

    @Test
    fun delayedBriefGapRetainsSameHandFilterHistory() {
        TestingTools.setFilterMode(PointerFilterMode.VC49)
        publish(hand(), 1000L, delayMs = 500L)
        val previous = AirRuntime.pointerSnapshot()
        publish(emptyList(), 1131L, delayMs = 500L)
        publish(hand().toMutableList().apply { this[8] = NormalizedLandmark.create(.8f, .65f, 0f) },
            1200L, delayMs = 500L)
        assertTrue("Retention uses observed time, not camera age plus gap",
            kotlin.math.abs(AirRuntime.pointerX - previous.x) < .06f)
    }

    @Test
    fun delayedClassifierEvidenceStillCannotConfirmCommands() {
        AirRuntime.pointerEnabled = false
        AirRuntime.gesturesEnabled = true
        repeat(8) { publish(hand(), 1000L + it * 200L, gestureName = "Victory", delayMs = 500L) }
        val field = GestureRecognitionEngine::class.java.getDeclaredField("gestureTransaction").apply { isAccessible = true }
        assertEquals(GestureTransactionStateMachine.State.NEUTRAL,
            (field.get(engine) as GestureTransactionStateMachine).currentState())
    }

    @Test fun unsynchronizedSensorEpochDoesNotCreateStaleRejections() {
        publish(hand(), 1_000L, delayMs = 100_000L, measuredAgeMs = 100L)
        publish(hand(), 1_200L, delayMs = 100_000L, measuredAgeMs = 100L)
        assertTrue(AirRuntime.pointerTracking)
        assertEquals(PointerFeedback.TRACKING, AirRuntime.pointerFeedback)
        // A real processing backlog is still stale regardless of clock origin.
        publish(hand(), 1_400L, delayMs = 100_000L, measuredAgeMs = 601L)
        assertFalse(AirRuntime.pointerTracking)
        assertEquals(PointerInteractionPhase.IDLE, AirRuntime.pointerInteraction)
    }

    @Test fun slowCadenceGapKeepsCursorPositionButCancelsTouchImmediately() {
        AirRuntime.gesturesEnabled = true
        publish(hand(), 1000L)
        publish(hand(), 1200L)
        val point = AirRuntime.pointerSnapshot()
        publish(emptyList(), 1400L)
        assertEquals(PointerFeedback.COASTING, AirRuntime.pointerFeedback)
        assertFalse("Held position is not fresh evidence", AirRuntime.pointerTracking)
        assertEquals(PointerInteractionPhase.IDLE, AirRuntime.pointerInteraction)
        assertEquals(point.x, AirRuntime.pointerX, 0f)
        assertEquals(point.y, AirRuntime.pointerY, 0f)
        publish(emptyList(), 1800L)
        assertEquals(PointerFeedback.NO_HAND, AirRuntime.pointerFeedback)
    }

    @Test fun landmarkPoseUsesItsSavedMappingThroughTheFullEngine() {
        val context = RuntimeEnvironment.getApplication() as Context
        val prefs = context.getSharedPreferences("aergis_mappings", Context.MODE_PRIVATE)
        val controller = org.robolectric.Robolectric.buildService(AirAccessibilityService::class.java).create()
        val service = controller.get()
        AirAccessibilityService::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, service)
        try {
            ActionMappingStore(context).setMapping(RegisteredGesture.SHAKA, AirAction.TAP)
            AirRuntime.pointerEnabled = false
            AirRuntime.gesturesEnabled = true
            val pose = LandmarkGestureRecognizerTest.pose(RegisteredGesture.SHAKA).map {
                NormalizedLandmark.create(it.x, it.y, it.z)
            }
            repeat(8) { publish(pose, 1000L + it * 40L) }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("Shaka", AirRuntime.lastGesture)
            assertEquals("Owned landmark gesture must reach native dispatch once", 1,
                org.robolectric.Shadows.shadowOf(service).gesturesDispatched.size)
        } finally {
            prefs.edit().remove("gesture_SHAKA").commit()
            controller.destroy()
        }
    }

    @Test fun recordedDeviceLatencyCanConfirmMappedCommandsExactlyOnce() {
        val controller = org.robolectric.Robolectric.buildService(AirAccessibilityService::class.java).create()
        val service = controller.get()
        AirAccessibilityService::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, service)
        try {
            AirRuntime.gesturesEnabled = true
            val ages = listOf(242L, 231L, 225L, 255L, 248L, 318L, 250L, 245L)
            ages.forEachIndexed { index, age ->
                publish(hand(), 1000L + index * 134L, gestureName = "Victory", delayMs = age)
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(listOf(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),
                org.robolectric.Shadows.shadowOf(service).globalActionsPerformed)
        } finally { controller.destroy() }
    }

    @Test fun commandAgeLimitStillRejectsBacklogWhileCursorCanTrack() {
        AirRuntime.gesturesEnabled = true
        repeat(8) { publish(hand(), 1000L + it * 134L, gestureName = "Victory", delayMs = 351L) }
        assertTrue(AirRuntime.pointerTracking)
        val field = GestureRecognitionEngine::class.java.getDeclaredField("gestureTransaction").apply { isAccessible = true }
        assertEquals(GestureTransactionStateMachine.State.NEUTRAL,
            (field.get(engine) as GestureTransactionStateMachine).currentState())
    }

    private fun hand(): List<NormalizedLandmark> = MutableList(21) {
        NormalizedLandmark.create(0.5f, 0.5f, 0f)
    }.apply {
        this[5] = NormalizedLandmark.create(0.4f, 0.5f, 0f)
        this[17] = NormalizedLandmark.create(0.6f, 0.5f, 0f)
        this[8] = NormalizedLandmark.create(0.5f, 0.65f, 0f)
        this[12] = NormalizedLandmark.create(0.55f, 0.65f, 0f)
    }

    private fun publish(landmarks: List<NormalizedLandmark>, timestamp: Long, rawHandedness: String = "Left", gestureName: String = "None", delayMs: Long = 0L, measuredAgeMs: Long? = null) {
        val hands = if (landmarks.isEmpty()) emptyList() else listOf(
            LandmarkProto.NormalizedLandmarkList.newBuilder().addAllLandmark(landmarks.map {
                LandmarkProto.NormalizedLandmark.newBuilder().setX(it.x()).setY(it.y()).setZ(it.z()).build()
            }).build()
        )
        val handedness = if (landmarks.isEmpty()) emptyList() else listOf(
            ClassificationProto.ClassificationList.newBuilder().addClassification(
                ClassificationProto.Classification.newBuilder().setScore(1f).setLabel(rawHandedness).build()
            ).build()
        )
        // MediaPipe's package-private factory consumes the same protobuf messages as camera inference.
        val factory = GestureRecognizerResult::class.java.getDeclaredMethod(
            "create", List::class.java, List::class.java, List::class.java, List::class.java, java.lang.Long.TYPE
        ).apply { isAccessible = true }
        val gestures = if (landmarks.isEmpty()) emptyList() else listOf(
            ClassificationProto.ClassificationList.newBuilder().addClassification(
                ClassificationProto.Classification.newBuilder().setScore(.99f).setLabel(gestureName).build()
            ).build()
        )
        val result = factory.invoke(null, hands, emptyList<Any>(), handedness, gestures, timestamp) as GestureRecognizerResult
        if (measuredAgeMs != null) {
            val method = GestureRecognitionEngine::class.java.getDeclaredMethod(
                "publishWithAge", GestureRecognizerResult::class.java, java.lang.Long.TYPE,
                java.lang.Long.TYPE, java.lang.Integer.TYPE, java.lang.Long.TYPE
            ).apply { isAccessible = true }
            method.invoke(engine, result, timestamp, timestamp + delayMs, 0, measuredAgeMs)
            return
        }
        val method = GestureRecognitionEngine::class.java.getDeclaredMethod(
            "publish", GestureRecognizerResult::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE, java.lang.Integer.TYPE
        ).apply { isAccessible = true }
        method.invoke(engine, result, timestamp, timestamp + delayMs, 0)
    }
}
