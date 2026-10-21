package com.airgesture.control.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.airgesture.control.AirRuntime
import com.airgesture.control.ControlMode
import com.airgesture.control.RegisteredGesture
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefinedProductUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun stoppedPrimaryStartsCaptureWithoutArming() {
        var captures = 0
        var arms = 0
        AirRuntime.running = false
        AirRuntime.controlMode = ControlMode.READY
        compose.setContent {
            AergisTheme {
                AergisShell(true) { _, navigate ->
                    ControlScreen(true, false, false, true, 5, null, { captures++ }, { arms++ }, {}, navigate)
                }
            }
        }
        compose.onNodeWithText("Start capture").performClick()
        compose.runOnIdle { assertEquals(1, captures); assertEquals(0, arms) }
        screenshot("stopped-command")
    }

    @Test fun unsafeArmedSessionRetainsDisarmAndSeparateStop() {
        var captures = 0
        var arms = 0
        compose.setContent {
            PreviewSurface {
                ControlCommandCenter(true, true, true, ProductActionState.PAUSED,
                    { captures++ }, { arms++ })
            }
        }
        compose.onNodeWithText("Disarm control").performClick()
        compose.runOnIdle { assertEquals(1, arms); assertEquals(0, captures) }
        compose.onNodeWithContentDescription("Stop capture").performClick()
        compose.runOnIdle { assertEquals(1, captures); assertEquals(1, arms) }
    }

    @Test fun stoppedCameraCannotHideDisarmForAnArmedMode() {
        var captures = 0
        var arms = 0
        compose.setContent {
            PreviewSurface {
                ControlCommandCenter(false, true, true, ProductActionState.STOPPED,
                    { captures++ }, { arms++ })
            }
        }
        compose.onNodeWithText("Disarm control").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, arms); assertEquals(0, captures) }
        compose.onNodeWithContentDescription("Start capture").assertIsDisplayed()
    }

    @Test fun largeTextNarrowLayoutKeepsPrimaryAndInputDestinationsReachable() {
        var route: AergisDestination? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                PreviewSurface {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        ControlCommandCenter(true, false, false, ProductActionState.DISARMED, {}, {})
                        ProductInputTiles(5) { route = it }
                    }
                }
            }
        }
        compose.onNodeWithText("Arm control").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Direct motion").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AergisDestination.MOTION, route) }
        screenshot("large-text-inputs")
    }

    @Test fun nativeArtworkAndLibraryOverviewRender() {
        compose.setContent {
            PreviewSurface {
                Column(Modifier.width(411.dp)) {
                    GestureLibraryIntro()
                    GestureMappingRow(RegisteredGesture.VICTORY, "Victory", "Back", "Two fingers extended", null) {}
                    DirectionInstrument(Modifier.fillMaxWidth().height(220.dp))
                }
            }
        }
        compose.onNodeWithText("Victory").assertIsDisplayed()
        screenshot("gesture-and-motion")
    }

    @Composable
    private fun PreviewSurface(content: @Composable () -> Unit) {
        AergisTheme {
            Surface(Modifier.fillMaxSize(), color = AergisColors.Background, content = content)
        }
    }

    private fun screenshot(name: String) {
        // PixelCopy waits for a hardware-window callback Robolectric does not deliver.
        // Draw the actual laid-out Compose host using the native-graphics test backend.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = File("build/reports/testing-tools/product-ui/$name.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
