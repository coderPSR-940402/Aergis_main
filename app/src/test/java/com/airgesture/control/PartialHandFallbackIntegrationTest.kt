package com.airgesture.control

import android.content.Context
import com.google.mediapipe.formats.proto.ClassificationProto
import com.google.mediapipe.formats.proto.LandmarkProto
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.nio.ByteBuffer
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PartialHandFallbackIntegrationTest {
    private lateinit var engine: GestureRecognitionEngine

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("aergis_calibration", Context.MODE_PRIVATE).edit().clear().commit()
        TestingTools.setFilterMode(PointerFilterMode.CURRENT)
        AirRuntime.pointerEnabled = true
        AirRuntime.pointerClickingEnabled = true
        AirRuntime.gesturesEnabled = true
        AirRuntime.handPreference = ControlHandPreference.EITHER
        AirRuntime.controlMode = ControlMode.ARMED
        AirRuntime.setForegroundContext(ForegroundContextState(safety = ForegroundSafety.SAFE))
        engine = GestureRecognitionEngine(context)
    }

    @After
    fun tearDown() {
        engine.close()
        AirRuntime.controlMode = ControlMode.OFF
        AirRuntime.pointerEnabled = true
        AirRuntime.pointerClickingEnabled = true
        AirRuntime.gesturesEnabled = true
        AirRuntime.setForegroundContext(ForegroundContextState())
    }

    @Test
    fun rightImageEndpointRetainsMotionRecoveryThroughEveryRotation() =
        assertEndpointRecovery(1f,.5f,-3,0)

    @Test
    fun bottomImageEndpointRetainsMotionRecoveryThroughEveryRotation() =
        assertEndpointRecovery(.5f,1f,0,-3)

    @Test
    fun bottomRightImageCornerRetainsMotionRecoveryThroughEveryRotation() =
        assertEndpointRecovery(1f,1f,-3,-3)

    private fun assertEndpointRecovery(nx: Float, ny: Float, dx: Int, dy: Int) {
        for (rotation in listOf(0,90,180,270)) {
            engine.close()
            engine = GestureRecognitionEngine(RuntimeEnvironment.getApplication())
            val pose = hand().toMutableList().apply {
                this[8] = NormalizedLandmark.create(nx,ny,0f)
            }
            setPixels(textureFrame(0,rotationDegrees=rotation))
            publish(pose,1000L,rotation)
            assertTrue("The endpoint must first have trusted model tracking",AirRuntime.pointerTracking)
            val before = AirRuntime.pointerSnapshot()
            setPixels(textureFrame(dx,shiftY=dy,rotationDegrees=rotation))
            publish(emptyList(),1100L,rotation)
            val after = AirRuntime.pointerSnapshot()
            assertTrue("Endpoint ($nx,$ny), rotation=$rotation must follow inward image motion",
                kotlin.math.hypot(after.x-before.x,after.y-before.y) > .005f)
            assertEquals(PointerFeedback.COASTING,AirRuntime.pointerFeedback)
            assertFalse("Image recovery cannot authorize a click",after.tracking)
            assertFalse(AirRuntime.rawPointerSnapshot().tracking)
            assertEquals(PointerInteractionPhase.IDLE,AirRuntime.pointerInteraction)
        }
    }

    @Test
    fun trustedIndexTipSeedsMotionFallbackWhenUnrelatedLandmarkIsOffscreen() {
        val partialPose = hand().toMutableList().apply {
            // The little fingertip is unrelated to pointer position and pinch evidence.
            // Whole-hand gesture geometry must reject it, but pointer recovery must not
            // lose its fingertip template because this landmark left the camera frame.
            this[20] = NormalizedLandmark.create(1.15f, 0.5f, 0f)
        }

        setPixels(textureFrame(shiftX = 0))
        publish(partialPose, 1_000L)
        assertTrue("The valid index tip must retain normal pointer authority", AirRuntime.pointerTracking)
        val before = AirRuntime.pointerSnapshot()

        // MediaPipe then drops the hand completely while the fingertip texture remains
        // visible one pixel to the right. The fallback is motion-only: it may move the
        // visible cursor but must not claim fresh tracking/action authority.
        setPixels(textureFrame(shiftX = 1))
        publish(emptyList(), 1_100L)
        val after = AirRuntime.pointerSnapshot()

        assertEquals(PointerFeedback.COASTING, AirRuntime.pointerFeedback)
        assertFalse("Image fallback is never fresh gesture/click evidence", after.tracking)
        assertTrue(
            "Fallback should follow the still-visible fingertip instead of freezing at the last MediaPipe point",
            abs(after.x - before.x) > 0.005f
        )
        assertEquals(PointerInteractionPhase.IDLE, AirRuntime.pointerInteraction)
    }

    private fun setPixels(frame: RgbaFrameData) {
        GestureRecognitionEngine::class.java.getDeclaredField("currentPixels")
            .apply { isAccessible = true }
            .set(engine, frame)
    }

    private fun textureFrame(shiftX: Int, width: Int = 120, height: Int = 120,
        shiftY: Int = 0, rotationDegrees: Int = 0): RgbaFrameData {
        val buffer = ByteBuffer.allocate(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourceX = x - shiftX
                val sourceY = y - shiftY
                val value = ((sourceX * 73 + sourceY * 151 + sourceX * sourceY * 17) xor
                    (sourceX shl 3) xor (sourceY shl 5)) and 0xff
                buffer.put(value.toByte())
                buffer.put(value.toByte())
                buffer.put(value.toByte())
                buffer.put(0xff.toByte())
            }
        }
        buffer.rewind()
        return RgbaFrameData(buffer, width, height, width * 4, 4, rotationDegrees, 1_000_000L, "[0,0][$width,$height]")
    }

    private fun hand(): List<NormalizedLandmark> = MutableList(21) {
        NormalizedLandmark.create(0.5f, 0.5f, 0f)
    }.apply {
        this[5] = NormalizedLandmark.create(0.4f, 0.5f, 0f)
        this[17] = NormalizedLandmark.create(0.6f, 0.5f, 0f)
        this[8] = NormalizedLandmark.create(0.5f, 0.65f, 0f)
        this[12] = NormalizedLandmark.create(0.55f, 0.65f, 0f)
    }

    private fun publish(landmarks: List<NormalizedLandmark>, timestamp: Long, rotationDegrees: Int = 0) {
        val hands = if (landmarks.isEmpty()) emptyList() else listOf(
            LandmarkProto.NormalizedLandmarkList.newBuilder().addAllLandmark(landmarks.map {
                LandmarkProto.NormalizedLandmark.newBuilder()
                    .setX(it.x()).setY(it.y()).setZ(it.z()).build()
            }).build()
        )
        val handedness = if (landmarks.isEmpty()) emptyList() else listOf(
            ClassificationProto.ClassificationList.newBuilder().addClassification(
                ClassificationProto.Classification.newBuilder()
                    .setScore(1f).setLabel("Left").build()
            ).build()
        )
        val gestures = if (landmarks.isEmpty()) emptyList() else listOf(
            ClassificationProto.ClassificationList.newBuilder().addClassification(
                ClassificationProto.Classification.newBuilder()
                    .setScore(.99f).setLabel("None").build()
            ).build()
        )
        val factory = GestureRecognizerResult::class.java.getDeclaredMethod(
            "create",
            List::class.java,
            List::class.java,
            List::class.java,
            List::class.java,
            java.lang.Long.TYPE
        ).apply { isAccessible = true }
        val result = factory.invoke(
            null,
            hands,
            emptyList<Any>(),
            handedness,
            gestures,
            timestamp
        ) as GestureRecognizerResult
        val method = GestureRecognitionEngine::class.java.getDeclaredMethod(
            "publish",
            GestureRecognizerResult::class.java,
            java.lang.Long.TYPE,
            java.lang.Long.TYPE,
            java.lang.Integer.TYPE
        ).apply { isAccessible = true }
        method.invoke(engine, result, timestamp, timestamp, rotationDegrees)
    }
}
