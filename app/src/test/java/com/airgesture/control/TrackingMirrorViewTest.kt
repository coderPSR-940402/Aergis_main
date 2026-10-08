package com.airgesture.control

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import com.airgesture.control.filtering.Point3D
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrackingMirrorViewTest {
    @Test fun imageAndControlLandmarksRenderInTheSameRectangle() {
        val photo = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(65, 80, 95)) }
        val hand = listOf(.50f to .85f, .40f to .74f, .30f to .63f, .23f to .52f, .17f to .42f,
            .40f to .51f, .38f to .36f, .37f to .24f, .36f to .13f,
            .51f to .48f, .51f to .30f, .51f to .18f, .51f to .07f,
            .61f to .51f, .64f to .35f, .65f to .24f, .66f to .15f,
            .70f to .58f, .76f to .47f, .79f to .38f, .82f to .30f).map { Point3D(it.first, it.second) }
        val view = TrackingMirrorView(RuntimeEnvironment.getApplication()).apply {
            layout(0, 0, 340, 456)
            frame = MirrorFrame(photo, listOf(hand), "TRACKING · Open_Palm", 0,
                "hand 0 · NONE\nraw 0.64,0.13 map 0.65,0.11\npinch 0.51 palm · AIMING\ncursor 0.65,0.11 · actions false", SystemClock.uptimeMillis())
        }
        val output = Bitmap.createBitmap(340, 456, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(output))
        // mdpi footer is 106px, leaving a centered 340px square at y=5.
        assertEquals("The mirror must contain real camera pixels", Color.rgb(65, 80, 95), output.getPixel(25, 280))
        assertEquals("Landmark 8 must align with the camera fingertip", Color.YELLOW, output.getPixel((.36f * 340).toInt(), (5 + .13f * 340).toInt()))
        val file = File("build/reports/testing-tools/mirror-render.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun staleAndFailedFramesAreNeverReportedLive() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val frame = MirrorFrame(bitmap, emptyList(), "tracking", timestampMs = 1000)
        assertTrue(frame.isLive(1000))
        assertTrue(frame.isLive(2000))
        assertFalse(frame.isLive(2001))
        assertFalse(frame.isLive(999))
        assertFalse(frame.copy(bitmap = null).isLive(1000))
    }
}
