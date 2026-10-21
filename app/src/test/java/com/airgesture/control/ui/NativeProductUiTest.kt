package com.airgesture.control.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.airgesture.control.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeProductUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun nestedNavigationRestoresThenBackReturnsToOriginWithoutEngineSideEffects() {
        AirRuntime.controlMode = ControlMode.READY
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AergisTheme {
                AergisShell(true) { destination, navigate ->
                    when (destination) {
                        AergisDestination.CONTROL -> Button({ navigate(AergisDestination.PRECISION) }) { Text("Open precision") }
                        AergisDestination.PRECISION -> Button({ navigate(AergisDestination.CALIBRATION) }) { Text("Open calibration") }
                        else -> Text("Calibration content")
                    }
                }
            }
        }
        compose.onNodeWithText("Open precision").performClick()
        compose.onNodeWithText("Open calibration").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Calibration content").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Open calibration").assertIsDisplayed()
        assertEquals(ControlMode.READY, AirRuntime.controlMode)
    }

    @Test fun mappingCancelDoesNotInvokeSaveAndRetainsOriginalTuning() {
        var saves = 0
        var dismisses = 0
        val original = GestureTuning(confidencePercent = 85, volumeSteps = 3, scrollPercent = 70)
        compose.setContent {
            AergisTheme {
                ActionMappingPicker("Victory", RegisteredGesture.VICTORY, AirAction.BACK, false,
                    original, { _, _ -> saves++ }, { dismisses++ })
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(0, saves)
            assertEquals(1, dismisses)
            assertEquals(85, original.confidencePercent)
        }
    }

    @Test fun mappingSaveRetainsHiddenLegacyTuningAndUsesActualCurrentBinding() {
        val original = GestureTuning(holdMs = 800, repeatDelayMs = 1400, confidencePercent = 90, volumeSteps = 4)
        var saved: Pair<AirAction, GestureTuning>? = null
        compose.setContent {
            AergisTheme {
                ActionMappingPicker("Victory", RegisteredGesture.VICTORY, AirAction.BACK, false,
                    original, { action, tuning -> saved = action to tuning }, {})
            }
        }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle {
            assertEquals(AirAction.BACK, saved?.first)
            assertEquals(original, saved?.second)
        }
    }
}
