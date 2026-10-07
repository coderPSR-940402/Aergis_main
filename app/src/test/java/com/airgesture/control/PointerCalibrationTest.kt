package com.airgesture.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerCalibrationTest {
    @Test
    fun sessionBuildsValidatedProfileFromFourCornerSamples() {
        val session = PointerCalibrationSession()
        session.start()

        assertTrue(session.addSample(0.10f, 0.08f))
        assertTrue(session.addSample(0.90f, 0.08f))
        assertTrue(session.addSample(0.10f, 0.92f))
        assertTrue(session.addSample(0.90f, 0.92f))

        val profile = session.complete()
        assertEquals(0.10f, profile!!.left, 0.0001f)
        assertEquals(0.90f, profile.right, 0.0001f)
        assertEquals(0.08f, profile.top, 0.0001f)
        assertEquals(0.92f, profile.bottom, 0.0001f)
        assertTrue(profile.mirrorX)
        assertEquals(PointerCalibrationSession.State.READY, session.state())
    }

    @Test
    fun sessionRejectsInsufficientOrInvalidSamples() {
        val session = PointerCalibrationSession()
        session.start()

        assertFalse(session.addSample(-0.1f, 0.5f))
        assertFalse(session.addSample(Float.NaN, 0.5f))
        assertTrue(session.addSample(0.5f, 0.5f))
        assertNull(session.complete())
        assertEquals(PointerCalibrationSession.State.INVALID, session.state())
    }

    @Test
    fun sessionRejectsSamplesWithoutEnoughSpan() {
        val session = PointerCalibrationSession(minimumSamples = 4)
        session.start()
        repeat(4) { assertTrue(session.addSample(0.5f, 0.5f)) }

        assertNull(session.complete())
        assertEquals(PointerCalibrationSession.State.INVALID, session.state())
    }

    @Test
    fun codecRoundTripsAndRejectsMalformedProfiles() {
        val profile = PointerCalibrationProfile.DEFAULT.copy(mirrorX = false)
        val encoded = PointerCalibrationProfileCodec.encode(profile)

        assertEquals(profile, PointerCalibrationProfileCodec.decode(encoded))
        assertNull(PointerCalibrationProfileCodec.decode("2|0.9|0.1|0.1|0.9|true"))
        assertNull(PointerCalibrationProfileCodec.decode("not-a-profile"))
        assertNull(PointerCalibrationProfileCodec.decode("2|0.1|0.9|0.1|0.9|maybe"))
    }

    @Test
    fun profileValidationRejectsUnsupportedSchemaAndNarrowBounds() {
        assertFalse(PointerCalibrationProfile(schemaVersion = 1).isValid())
        assertFalse(
            PointerCalibrationProfile(left = 0.46f, right = 0.54f).isValid()
        )
        assertEquals(
            PointerCalibrationProfile.DEFAULT,
            PointerCalibrationProfile(schemaVersion = 1).validatedOrDefault()
        )
    }
}
