package com.airgesture.control
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class TargetHighlightOverlayTest {
    @Test fun offscreenTargetCannotCreateAnAuthorizedHighlight() {
        val overlay=TargetHighlightOverlay(RuntimeEnvironment.getApplication())
        assertFalse(overlay.show(Rect(-500,-500,-400,-400)))
        assertFalse(overlay.isVisible)
        overlay.hide()
    }
}
