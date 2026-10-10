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
        AirRuntime.motionActive = false
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
        assertEquals(GestureTransactionStateMachine.State.NEUTRAL, transaction.currentState())
        repeat(4) { publish(hand(), 2266L + it * 33, gestureName = "Open_Palm") }
        assertEquals(GestureTransactionStateMachine.State.COOLDOWN, transaction.currentState())
        AirRuntime.controlMode = ControlMode.PAUSED
        publish(hand(), 2400L, gestureName = "Victory")
        assertEquals(GestureTransactionStateMachine.State.NEUTRAL, transaction.currentState())
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

    private fun hand(): List<NormalizedLandmark> = MutableList(21) {
        NormalizedLandmark.create(0.5f, 0.5f, 0f)
    }.apply {
        this[5] = NormalizedLandmark.create(0.4f, 0.5f, 0f)
        this[17] = NormalizedLandmark.create(0.6f, 0.5f, 0f)
        this[8] = NormalizedLandmark.create(0.5f, 0.65f, 0f)
        this[12] = NormalizedLandmark.create(0.55f, 0.65f, 0f)
    }

    private fun publish(landmarks: List<NormalizedLandmark>, timestamp: Long, rawHandedness: String = "Left", gestureName: String = "None") {
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
        val method = GestureRecognitionEngine::class.java.getDeclaredMethod(
            "publish", GestureRecognizerResult::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE, java.lang.Integer.TYPE
        ).apply { isAccessible = true }
        method.invoke(engine, result, timestamp, timestamp, 0)
    }
}
