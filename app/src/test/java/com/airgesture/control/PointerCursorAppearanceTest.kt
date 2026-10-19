package com.airgesture.control

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PointerCursorAppearanceTest {
    @Test fun screenHotspotStaysAlignedAcrossWindowInsetsAndRelayout() {
        val overlay = PointerOverlay(RuntimeEnvironment.getApplication())
        overlay.show()
        try {
            val root = PointerOverlay::class.java.getDeclaredField("rootView").apply { isAccessible = true }
                .get(overlay) as android.widget.FrameLayout
            val cursor = root.getChildAt(0) as PointerOverlay.CursorView
            // The recording's window starts 80 physical pixels below the display origin.
            root.layout(24, 80, 1104, 2340)
            cursor.layout(0, 0, cursor.sizePx, cursor.sizePx)
            overlay.updatePosition(828.75836f, 1126.1808f)
            fun assertHotspot() {
                val origin = IntArray(2)
                root.getLocationOnScreen(origin)
                assertEquals(828.75836f, origin[0] + cursor.x + cursor.sizePx / 2f, .001f)
                assertEquals(1126.1808f, origin[1] + cursor.y + cursor.sizePx / 2f, .001f)
            }
            assertHotspot()
            // Insets can change without a new camera sample; retained screen position wins.
            root.layout(0, 0, 1080, 2340)
            assertHotspot()
            root.layout(80, 0, 2340, 1080)
            assertHotspot()
        } finally { overlay.hide() }
    }

    @Test fun clippedHotspotRevokesAuthorityAndRelayoutRestoresExactTarget() {
        val overlay = PointerOverlay(RuntimeEnvironment.getApplication())
        overlay.show()
        try {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val root = PointerOverlay::class.java.getDeclaredField("rootView").apply { isAccessible = true }
                .get(overlay) as android.widget.FrameLayout
            root.layout(0, 80, 1080, 2340)
            val origin = IntArray(2)
            root.getLocationOnScreen(origin)
            val x = origin[0] + 678f
            val y = origin[1] - 1f
            var applied: PointerCoordinateMapper.Point? = PointerCoordinateMapper.Point(-1f, -1f)
            overlay.updatePosition(x, y) { applied = it }
            assertNull("Attached window must not authorize a clipped hotspot", applied)
            assertFalse(overlay.isHotspotVisible)
            root.layout(0, 0, 1080, 2340)
            assertEquals(PointerCoordinateMapper.Point(x, y), applied)
            assertTrue(overlay.isHotspotVisible)
            overlay.hide()
            assertNull(applied)
        } finally { overlay.hide() }
    }

    @Test fun fullscreenWindowDoesNotFitSystemBarsAndStaysNonInteractive() {
        val overlay = PointerOverlay(RuntimeEnvironment.getApplication())
        val params = PointerOverlay::class.java.getDeclaredField("params").apply { isAccessible = true }
            .get(overlay) as android.view.WindowManager.LayoutParams
        assertEquals(0, params.fitInsetsTypes)
        assertEquals(android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
            params.layoutInDisplayCutoutMode)
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(params.flags and android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }

    @Test fun physicalCutoutDoesNotAuthorizeAnInvisibleHotspot() {
        val overlay = PointerOverlay(RuntimeEnvironment.getApplication())
        overlay.show()
        try {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val root = PointerOverlay::class.java.getDeclaredField("rootView").apply { isAccessible = true }
                .get(overlay) as android.widget.FrameLayout
            root.layout(0, 0, 1080, 2340)
            val origin = IntArray(2)
            root.getLocationOnScreen(origin)
            var applied: PointerCoordinateMapper.Point? = null
            overlay.updatePosition(origin[0] + 540f, origin[1] + 20f) { applied = it }
            assertNotNull(applied)
            val cutout = android.view.DisplayCutout(android.graphics.Rect(0, 80, 0, 0),
                listOf(android.graphics.Rect(510, 0, 570, 80)))
            root.dispatchApplyWindowInsets(android.view.WindowInsets.Builder().setDisplayCutout(cutout).build())
            assertNull(applied)
            root.dispatchApplyWindowInsets(android.view.WindowInsets.Builder().build())
            assertEquals(PointerCoordinateMapper.Point(origin[0] + 540f, origin[1] + 20f), applied)
        } finally { overlay.hide() }
    }

    @Test fun densityScaledCursorKeepsCenterHotspotAndChangesRingOnPress() {
        val context = RuntimeEnvironment.getApplication()
        val cursor = PointerOverlay.CursorView(context)
        val density = context.resources.displayMetrics.density
        assertEquals((32f * density).toInt(), cursor.sizePx)
        cursor.measure(View.MeasureSpec.makeMeasureSpec(cursor.sizePx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(cursor.sizePx, View.MeasureSpec.EXACTLY))
        cursor.layout(0, 0, cursor.sizePx, cursor.sizePx)
        fun render(pressed: Boolean): Bitmap {
            cursor.setClicking(pressed)
            return Bitmap.createBitmap(cursor.sizePx, cursor.sizePx, Bitmap.Config.ARGB_8888).also { cursor.draw(Canvas(it)) }
        }
        val aim = render(false)
        val press = render(true)
        val center = cursor.sizePx / 2
        assertEquals(Color.rgb(255, 214, 0), aim.getPixel(center, center))
        assertEquals(aim.getPixel(center, center), press.getPixel(center, center))
        assertEquals(Color.TRANSPARENT, aim.getPixel(0, 0))
        assertEquals(Color.rgb(225, 228, 235), aim.getPixel(center + (10 * density).toInt(), center))
        assertEquals(Color.rgb(172, 126, 255), press.getPixel(center + (8 * density).toInt(), center))
        aim.recycle(); press.recycle()
    }
}
