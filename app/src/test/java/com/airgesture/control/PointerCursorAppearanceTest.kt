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
