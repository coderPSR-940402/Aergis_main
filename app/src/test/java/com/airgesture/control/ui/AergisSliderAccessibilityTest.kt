package com.airgesture.control.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AergisSliderAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun labelledSlidersRetainIndependentNativeProgressAndAdjustment() {
        val sensitivity = mutableStateOf(25f)
        val smoothing = mutableStateOf(50f)
        compose.setContent {
            AergisTheme {
                Column {
                    AergisSlider("Sensitivity", sensitivity.value, 0f..100f) { sensitivity.value = it }
                    AergisSlider("Smoothing", smoothing.value, 0f..100f) { smoothing.value = it }
                }
            }
        }

        val sensitivitySlider = compose.onNodeWithContentDescription("Sensitivity")
        val smoothingSlider = compose.onNodeWithContentDescription("Smoothing")
        sensitivitySlider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(25f, 0f..100f)))
        smoothingSlider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(50f, 0f..100f)))
        sensitivitySlider.assert(SemanticsMatcher("Setting name does not replace the native value announcement") {
            !it.config.contains(SemanticsProperties.StateDescription) ||
                it.config[SemanticsProperties.StateDescription] != "Sensitivity"
        })

        sensitivitySlider.performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        sensitivitySlider.assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(75f, 0f..100f)))
        compose.runOnIdle {
            assertEquals(75f, sensitivity.value, 0f)
            assertEquals(50f, smoothing.value, 0f)
        }
    }
}
