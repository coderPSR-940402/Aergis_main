package com.airgesture.control

import org.junit.Assert.*
import org.junit.Test

class CalibrationReachRegressionTest {
    @Test
    fun runningMapperAppliesSaveEditAndDisableWithoutRestart() {
        var profile: PointerCalibrationProfile? = null
        val mapper = LivePointerMapper { profile }
        assertTrue(mapper.map(0.5f, 0.65f).y < 0.8f)
        profile = PointerCalibrationProfile(top = 0.15f, bottom = 0.65f)
        assertEquals(1f, mapper.map(0.5f, 0.65f).y, 0.0001f)
        profile = profile!!.copy(bottom = 0.85f)
        assertEquals(0.7142857f, mapper.map(0.5f, 0.65f).y, 0.0001f)
        profile = null
        assertTrue(mapper.map(0.5f, 0.65f).y < 0.8f)
    }

    @Test
    fun comfortableReachReachesBottomBeforeCameraBoundary() {
        val profile = PointerCalibrationProfile.COMFORTABLE_REACH
        assertEquals(1f, PointerCoordinateMapper.map(0.5f, 0.65f, profile).y, 0.0001f)
        assertEquals(0f, PointerCoordinateMapper.map(0.5f, 0.12f, profile).y, 0.0001f)
        assertEquals(1f, PointerCoordinateMapper.map(0.15f, 0.4f, profile).x, 0.0001f)
        assertEquals(0f, PointerCoordinateMapper.map(0.85f, 0.4f, profile).x, 0.0001f)
    }

    @Test
    fun asymmetricCaptureUsesMirroredUserPerspectiveBounds() {
        val session = PointerCalibrationSession()
        session.start()
        for ((x, y) in listOf(0.6f to 0.2f, 0.9f to 0.2f, 0.6f to 0.6f, 0.9f to 0.6f)) {
            assertTrue(session.addSample(x, y))
        }
        val profile = session.complete()!!
        assertEquals(0.1f, profile.left, 0.0001f)
        assertEquals(0.4f, profile.right, 0.0001f)
        assertEquals(1f, PointerCoordinateMapper.map(0.6f, 0.6f, profile).x, 0.0001f)
        assertEquals(0f, PointerCoordinateMapper.map(0.9f, 0.2f, profile).x, 0.0001f)
    }

    @Test
    fun readyCaptureCanExpandComfortableBoundsBeyondFourSamples() {
        val session = PointerCalibrationSession(mirrorX = false)
        session.start()
        for ((x, y) in listOf(0.3f to 0.3f, 0.6f to 0.3f, 0.3f to 0.6f, 0.6f to 0.6f)) {
            assertTrue(session.addSample(x, y))
        }
        assertTrue(session.addSample(0.2f, 0.7f))
        val profile = session.complete()!!
        assertEquals(0.2f, profile.left, 0.0001f)
        assertEquals(0.7f, profile.bottom, 0.0001f)
    }

    @Test
    fun responseCurvesPersistAndPreserveCenterAndEdges() {
        val profile = PointerCalibrationProfile(left = 0.2f, right = 0.8f, top = 0.2f, bottom = 0.8f,
            mirrorX = false, curveX = 1.5f, curveY = 1.5f)
        val restored = PointerCalibrationProfileCodec.decode(PointerCalibrationProfileCodec.encode(profile))!!
        assertEquals(1.5f, restored.curveX, 0f)
        assertEquals(1.5f, restored.curveY, 0f)
        val mapped = PointerCoordinateMapper.map(0.65f, 0.65f, restored)
        assertEquals(0.6767767f, mapped.x, 0.0001f)
        assertEquals(0.6767767f, mapped.y, 0.0001f)
        assertEquals(0.5f, PointerCoordinateMapper.map(0.5f, 0.5f, restored).y, 0.0001f)
        assertEquals(1f, PointerCoordinateMapper.map(0.8f, 0.8f, restored).y, 0.0001f)
    }

    @Test
    fun invalidResponseCurvesCannotBeSaved() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, 3f)) {
            assertFalse(PointerCalibrationProfile(curveX = bad).isValid())
            assertFalse(PointerCalibrationProfile(curveY = bad).isValid())
        }
    }

    @Test
    fun briefLossHoldsExactLastPositionAndExpiresOnTime() {
        val grace = PointerVisibilityGrace()
        val point = PointerCoordinateMapper.Point(0.4f, 0.95f)
        grace.record(point, 1000L)
        assertEquals(point, grace.heldAt(1130L))
        assertNull(grace.heldAt(1131L))
        assertNull(grace.heldAt(999L))
        grace.reset()
        assertNull(grace.heldAt(1050L))
    }
}
