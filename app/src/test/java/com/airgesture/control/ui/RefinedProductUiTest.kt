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
import com.airgesture.control.MainActivity
import com.airgesture.control.RegisteredGesture
import com.airgesture.control.R
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
                AergisShell { _, navigate ->
                    ControlScreen(true, false, true, 5, null, { captures++ }, {}, { arms++ }, {}, navigate)
                }
            }
        }
        compose.onNodeWithText("Start capture").performClick()
        compose.runOnIdle { assertEquals(1, captures); assertEquals(0, arms) }
        screenshot("stopped-command", verifyNewSurfaces = true)
    }

    @Test fun missingCameraRoutesToPermissionsWithoutStartingCapture() {
        var captures = 0
        var route: AergisDestination? = null
        AirRuntime.running = false
        AirRuntime.controlMode = ControlMode.READY
        compose.setContent {
            PreviewSurface {
                ControlScreen(true, false, true, 5, null, { captures++ }, {}, {}, {}, { route = it },
                    cameraAllowed = false)
            }
        }
        compose.onNodeWithText("Complete setup").performClick()
        compose.runOnIdle {
            assertEquals(0, captures)
            assertEquals(AergisDestination.PERMISSIONS, route)
        }
    }

    @Test fun cameraGrantedStillExposesAccessibilityRecovery() {
        var opened = 0
        compose.setContent {
            PreviewSurface {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PermissionsPanel(true, false, {}, {}, { opened++ })
                }
            }
        }
        compose.onNodeWithText("App settings").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun unsafeArmedSessionRetainsDisarmAndSeparateStop() {
        var captures = 0
        var arms = 0
        compose.setContent {
            PreviewSurface {
                ControlCommandCenter(true, ProductActionState.PAUSED, R.string.product_action_paused,
                    "Protected screen", R.string.disarm_control, true, { arms++ }, { captures++ })
            }
        }
        compose.onNodeWithText("Disarm control").performClick()
        compose.runOnIdle { assertEquals(1, arms); assertEquals(0, captures) }
        compose.onNodeWithText("Stop capture").performClick()
        compose.runOnIdle { assertEquals(1, captures); assertEquals(1, arms) }
    }


    @Test fun manualPauseResumesWithoutRestartingCapture() {
        var resumes = 0
        var stops = 0
        compose.setContent {
            PreviewSurface {
                ControlCommandCenter(true, ProductActionState.PAUSED, R.string.product_action_paused,
                    "Controls paused", R.string.product_resume_controls, true,
                    { resumes++ }, { stops++ })
            }
        }
        compose.onNodeWithText("Resume controls").performClick()
        compose.runOnIdle { assertEquals(1, resumes); assertEquals(0, stops) }
    }

    @Test fun stoppedCameraCannotHideDisarmForAnArmedMode() {
        var captures = 0
        val commands = mutableListOf<Boolean>()
        AirRuntime.running = false
        AirRuntime.controlMode = ControlMode.ARMED
        compose.setContent {
            PreviewSurface {
                ControlScreen(true, false, true, 5, null, { captures++ }, {}, { commands += it }, {}, {})
            }
        }
        val disarm = compose.onNodeWithText("Disarm control").assertIsEnabled()
        disarm.performClick()
        disarm.performClick()
        compose.runOnIdle { assertEquals(listOf(false, false), commands); assertEquals(0, captures) }
        compose.onNodeWithText("Start capture").assertDoesNotExist()
    }

    @Test fun largeTextNarrowLayoutKeepsPrimaryAndInputDestinationsReachable() {
        var route: AergisDestination? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                PreviewSurface {
                    Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        ControlCommandCenter(true, ProductActionState.DISARMED, R.string.product_action_disarmed,
                            "Starting the camera", R.string.arm_control, false, {}, {})
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
                    ProductHeading(R.string.product_gestures, R.string.product_gesture_kicker)
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

    private fun screenshot(name: String, verifyNewSurfaces: Boolean = false) {
        compose.runOnIdle { captureProductScreen(compose.activity.window.decorView, name, verifyNewSurfaces) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityVisualTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun rendersTheRealOnboardingAndPrimaryDestinations() {
        compose.onNodeWithText("Explore the interface").assertIsDisplayed()
        capture("welcome")
        compose.onNodeWithText("Explore the interface").performClick()
        compose.onNodeWithText("Complete setup").assertIsDisplayed()
        capture("control-setup")
        compose.onNodeWithTag("nav-GESTURES").performClick().assertIsSelected()
        compose.onNodeWithText("Find a gesture or action").assertIsDisplayed()
        capture("gestures")
        compose.onNodeWithTag("nav-SETTINGS").performClick().assertIsSelected()
        compose.onNodeWithText("Control hand").assertIsDisplayed()
        capture("settings")
    }

    private fun capture(name: String) {
        compose.runOnIdle { captureProductScreen(compose.activity.window.decorView, name) }
    }
}

private fun captureProductScreen(view: android.view.View, name: String, verifyNewSurfaces: Boolean = false) {
    // PixelCopy waits for a hardware-window callback Robolectric does not deliver.
    val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
    try {
        view.draw(android.graphics.Canvas(bitmap))
        if (verifyNewSurfaces) {
            // Preserve the distinctive dark Aergis background in the rendered product.
            val canvasColor = android.graphics.Color.rgb(11, 13, 20)
            assertEquals(canvasColor, bitmap.getPixel(4, bitmap.height / 2))
            val stage = bitmap.getPixel(27, 110)
            org.junit.Assert.assertTrue("The violet command stage should own the top of Control",
                android.graphics.Color.blue(stage) > android.graphics.Color.red(stage) + 35)
        }
        val file = File("build/reports/testing-tools/product-ui/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
        bitmap.recycle()
    }
}
