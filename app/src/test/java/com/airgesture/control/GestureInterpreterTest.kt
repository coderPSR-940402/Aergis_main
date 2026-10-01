package com.airgesture.control.pointer

import com.airgesture.control.filtering.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GestureInterpreterTest {
    @Test
    fun productionEngineDetectsRightwardMotionOnCompleteWindow() {
        val engine = SwipeGestureEngine(
            windowSize = 5,
            minVelocityScreensPerSec = 0.8f,
            returnCooldownMs = 300L
        )

        var direction = SwipeDirection.NONE
        for (i in 0 until 5) {
            direction = engine.processFrame(Point3D(i * 0.05f, 0.5f), i * 33L)
        }

        assertEquals(SwipeDirection.RIGHT, direction)
    }

    @Test
    fun productionEngineSuppressesSamplesDuringCooldown() {
        val engine = SwipeGestureEngine(windowSize = 3, returnCooldownMs = 300L)
        engine.processFrame(Point3D(0f, 0.5f), 1L)
        engine.processFrame(Point3D(0.1f, 0.5f), 34L)
        assertEquals(SwipeDirection.RIGHT, engine.processFrame(Point3D(0.2f, 0.5f), 67L))

        assertFalse(engine.processFrame(Point3D(0.25f, 0.5f), 100L) == SwipeDirection.RIGHT)
    }
}
