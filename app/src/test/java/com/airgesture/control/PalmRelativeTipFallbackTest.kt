package com.airgesture.control

import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test

class PalmRelativeTipFallbackTest {
    private fun hand(
        indexX: Float = .4f, indexY: Float = .5f,
        pinkyX: Float = .6f, pinkyY: Float = .5f,
        tipX: Float = .44f, tipY: Float = .3f
    ): List<Point3D> = MutableList(21) { Point3D(.5f, .5f) }.also {
        it[5] = Point3D(indexX, indexY)
        it[17] = Point3D(pinkyX, pinkyY)
        it[8] = Point3D(tipX, tipY)
    }

    @Test fun translationFollowsFreshPalmButNotAnEstimatedFingertip() {
        val fallback = PalmRelativeTipFallback()
        assertTrue(fallback.observe(hand(), 1f, "Right:1", 7L, 11L, 1000L))
        val missing = hand(indexX = .5f, indexY = .6f, pinkyX = .7f, pinkyY = .6f,
            tipX = Float.NaN, tipY = Float.NaN)
        val predicted = fallback.predict(missing, 1f, "Right:1", 7L, 11L, 1040L)
        assertNotNull(predicted)
        assertEquals(.54f, predicted!!.x, .0001f)
        assertEquals(.4f, predicted.y, .0001f)
        // The prediction is not accepted as an observation or allowed to extend its lifetime.
        assertNull(fallback.predict(missing, 1f, "Right:1", 7L, 11L, 1301L))
    }

    @Test fun inPlanePalmRollRotatesCursorEstimate() {
        val fallback = PalmRelativeTipFallback()
        fallback.observe(hand(), 1f, "Right:1", 7L, 11L, 1000L)
        val rolled = hand(indexX = .45f, indexY = .45f, pinkyX = .45f, pinkyY = .65f,
            tipX = Float.NaN, tipY = Float.NaN)
        val predicted = fallback.predict(rolled, 1f, "Right:1", 7L, 11L, 1100L)
        assertNotNull(predicted)
        assertEquals(.65f, predicted!!.x, .0001f)
        assertEquals(.49f, predicted.y, .0001f)
    }

    @Test fun palmScaleIsHandledWithoutDistortingImageAspect() {
        val fallback = PalmRelativeTipFallback()
        val original = hand()
        fallback.observe(original, 2f, "Right:1", 7L, 11L, 1000L)
        val scaled = hand(indexX = .4f, pinkyX = .7f, tipX = Float.NaN, tipY = Float.NaN)
        val predicted = fallback.predict(scaled, 2f, "Right:1", 7L, 11L, 1040L)
        assertNotNull(predicted)
        assertEquals(.46f, predicted!!.x, .0001f)
        assertEquals(.2f, predicted.y, .0001f)
    }

    @Test fun authorityAndOwnerChangesCannotUseOldTip() {
        val fallback = PalmRelativeTipFallback()
        fallback.observe(hand(), 1f, "Right:1", 7L, 11L, 1000L)
        val missing = hand(tipX = Float.NaN)
        assertNull(fallback.predict(missing, 1f, "Left:2", 7L, 11L, 1020L))
        assertNull(fallback.predict(missing, 1f, "Right:1", 8L, 11L, 1020L))
        assertNull(fallback.predict(missing, 1f, "Right:1", 7L, 12L, 1020L))
        fallback.reset()
        assertNull(fallback.predict(missing, 1f, "Right:1", 7L, 11L, 1020L))
    }

    @Test fun invalidPalmGeometryOrLargeCameraJumpIsRejected() {
        val fallback = PalmRelativeTipFallback()
        fallback.observe(hand(), 1f, "Right:1", 7L, 11L, 1000L)
        assertNull(fallback.predict(hand(pinkyX = .4f, tipX = Float.NaN), 1f,
            "Right:1", 7L, 11L, 1033L))
        assertNull(fallback.predict(hand(indexX = .8f, indexY = .8f, pinkyX = 1f,
            pinkyY = .8f, tipX = Float.NaN), 1f, "Right:1", 7L, 11L, 1033L))
        assertNull(fallback.predict(hand(pinkyX = .9f, tipX = Float.NaN), 1f,
            "Right:1", 7L, 11L, 1033L))
        assertNull(fallback.predict(hand(indexX = Float.NaN, tipX = Float.NaN), 1f,
            "Right:1", 7L, 11L, 1033L))
    }

    @Test fun predictionCannotBeCreatedFromAnInvalidObservation() {
        val fallback = PalmRelativeTipFallback()
        assertFalse(fallback.observe(hand(tipX = Float.NaN), 1f,
            "Right:1", 7L, 11L, 1000L))
        assertFalse(fallback.observe(hand(), 1f, null, 7L, 11L, 1000L))
        assertNull(fallback.predict(hand(), 1f, "Right:1", 7L, 11L, 1050L))
    }
}
