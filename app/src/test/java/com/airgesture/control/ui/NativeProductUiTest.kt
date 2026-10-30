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
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeProductUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actionFeedbackDoesNotEnterActivitySemanticsOrMoveControls() {
        var clicks = 0
        compose.setContent {
            AergisTheme {
                AergisShell(true) { _, _ ->
                    Button({ clicks++ }) { Text("Selected control") }
                }
            }
        }
        val control = compose.onNodeWithText("Selected control")
        val before = control.fetchSemanticsNode()
        fun assertControlRetained() {
            val current = control.fetchSemanticsNode()
            assertEquals(before.id, current.id)
            assertEquals(before.boundsInRoot, current.boundsInRoot)
        }
        var requestId = 0L
        compose.runOnIdle { requestId = AirRuntime.beginActionFeedback(AirAction.NEXT_ITEM) }
        compose.waitForIdle()
        assertControlRetained()
        compose.onNodeWithText(AirRuntime.state.value.actionFeedback!!.message(compose.activity)).assertDoesNotExist()
        compose.runOnIdle { AirRuntime.recordActionFeedback(ActionDispatchOutcome.COMPLETED, requestId) }
        compose.waitForIdle()
        assertControlRetained()
        compose.onNodeWithText(AirRuntime.state.value.actionFeedback!!.message(compose.activity)).assertDoesNotExist()
        // Banner expiry must not shift controls or change their accessible content either.
        compose.mainClock.advanceTimeBy(4100L)
        compose.waitForIdle()
        assertControlRetained()
        control.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun externalCalibrationRequestNavigatesExistingShellAndIsHandledOnce() {
        val request = androidx.compose.runtime.mutableStateOf<AergisDestination?>(null)
        var handled = 0
        compose.setContent {
            AergisTheme {
                AergisShell(true, initialDestination = AergisDestination.SETTINGS,
                    onEntering = { if (it.disarmsOnEntry) AirRuntime.controlMode = ControlMode.READY },
                    requestedDestination = request.value,
                    onDestinationHandled = { handled++; request.value = null }) { destination, _ ->
                    Text("Screen: ${destination.name}")
                }
            }
        }
        compose.onNodeWithText("Screen: SETTINGS").assertIsDisplayed()
        compose.runOnIdle { AirRuntime.controlMode = ControlMode.ARMED; request.value = AergisDestination.CALIBRATION }
        compose.onNodeWithText("Screen: CALIBRATION").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ControlMode.READY, AirRuntime.controlMode)
            assertEquals(1, handled)
            assertNull(request.value)
        }
    }

    @Test fun nestedRehearsalNavigationRestoresDisarmedAndBackReturnsToOrigin() {
        AirRuntime.controlMode = ControlMode.READY
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AergisTheme {
                AergisShell(true, onEntering = { if (it.disarmsOnEntry) AirRuntime.controlMode = ControlMode.READY }) { destination, navigate ->
                    when (destination) {
                        AergisDestination.CONTROL -> Button({ navigate(AergisDestination.PRECISION) }) { Text("Open precision") }
                        AergisDestination.PRECISION -> Button({ navigate(AergisDestination.CALIBRATION) }) { Text("Open calibration") }
                        else -> Text("Calibration content")
                    }
                }
            }
        }
        compose.onNodeWithText("Open precision").performClick()
        compose.runOnIdle { AirRuntime.controlMode = ControlMode.ARMED }
        compose.onNodeWithText("Open calibration").performClick()
        compose.runOnIdle { assertEquals(ControlMode.READY, AirRuntime.controlMode) }
        // A notification or another surface may have changed mode while the Activity was gone.
        compose.runOnIdle { AirRuntime.controlMode = ControlMode.ARMED }
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
    @Test fun rehearsalBannerReflectsExternalArmingAndOffersDisarm() {
        AirRuntime.controlMode = ControlMode.READY
        compose.setContent { AergisTheme { RehearsalStatus() } }
        compose.onNodeWithText("REHEARSAL · ACTIONS DISARMED").assertIsDisplayed()
        compose.runOnIdle { AirRuntime.controlMode = ControlMode.ARMED }
        compose.onNodeWithText("Device actions are not disarmed. Disarm before rehearsing.").assertIsDisplayed()
        compose.onNodeWithText("Disarm control").performClick()
        compose.runOnIdle { assertEquals(ControlMode.READY, AirRuntime.controlMode) }
    }

}
