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
        AirRuntime.controlMode = ControlMode.OFF
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

    private fun hand(): List<NormalizedLandmark> = MutableList(21) {
        NormalizedLandmark.create(0.5f, 0.5f, 0f)
    }.apply {
        this[5] = NormalizedLandmark.create(0.4f, 0.5f, 0f)
        this[17] = NormalizedLandmark.create(0.6f, 0.5f, 0f)
        this[8] = NormalizedLandmark.create(0.5f, 0.65f, 0f)
        this[12] = NormalizedLandmark.create(0.55f, 0.65f, 0f)
    }

    private fun publish(landmarks: List<NormalizedLandmark>, timestamp: Long) {
        val hands = if (landmarks.isEmpty()) emptyList() else listOf(
            LandmarkProto.NormalizedLandmarkList.newBuilder().addAllLandmark(landmarks.map {
                LandmarkProto.NormalizedLandmark.newBuilder().setX(it.x()).setY(it.y()).setZ(it.z()).build()
            }).build()
        )
        val handedness = if (landmarks.isEmpty()) emptyList() else listOf(
            ClassificationProto.ClassificationList.newBuilder().addClassification(
                ClassificationProto.Classification.newBuilder().setScore(1f).setLabel("Left").build()
            ).build()
        )
        // MediaPipe's package-private factory consumes the same protobuf messages as camera inference.
        val factory = GestureRecognizerResult::class.java.getDeclaredMethod(
            "create", List::class.java, List::class.java, List::class.java, List::class.java, java.lang.Long.TYPE
        ).apply { isAccessible = true }
        val result = factory.invoke(null, hands, emptyList<Any>(), handedness, emptyList<Any>(), timestamp) as GestureRecognizerResult
        val method = GestureRecognitionEngine::class.java.getDeclaredMethod(
            "publish", GestureRecognizerResult::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE, java.lang.Integer.TYPE
        ).apply { isAccessible = true }
        method.invoke(engine, result, timestamp, timestamp, 0)
    }
}
