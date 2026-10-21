package com.airgesture.control.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import android.os.Build
import com.airgesture.control.GestureTuning
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.airgesture.control.AirAction
import com.airgesture.control.GestureActionPolicy
import com.airgesture.control.R
import com.airgesture.control.RegisteredGesture

/** Draft selection is local: Back, outside-tap and Cancel never write preferences. */
@Composable
internal fun ActionMappingPicker(
    gestureName: String,
    source: RegisteredGesture,
    current: AirAction,
    pointerEnabled: Boolean,
    tuning: GestureTuning,
    onSave: (AirAction, GestureTuning) -> Unit,
    onDismiss: () -> Unit
) {
    var draftName by rememberSaveable(source.name, current.name) { mutableStateOf(current.name) }
    var confidence by rememberSaveable(source.name) { mutableStateOf(tuning.confidencePercent.toFloat()) }
    var volume by rememberSaveable(source.name) { mutableStateOf(tuning.volumeSteps.toFloat()) }
    var scroll by rememberSaveable(source.name) { mutableStateOf(tuning.scrollPercent.toFloat()) }
    var longPress by rememberSaveable(source.name) { mutableStateOf(tuning.longPressMs.toFloat()) }
    val draft = AirAction.valueOf(draftName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_mapping_picker_title, gestureName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ui_mapping_picker_help))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                    AirAction.entries.filter { it.isUserMappable }.forEach { action ->
                        val supported = Build.VERSION.SDK_INT >= action.minimumSdk
                        val restricted = action != AirAction.NONE && !GestureActionPolicy.isClassifierActionEnabled(
                            gesturesEnabled = true, action = action, pointerControlActive = pointerEnabled, gesture = source
                        )
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(selected = action == draft, role = Role.RadioButton, enabled = supported,
                                    onClick = { draftName = action.name })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = action == draft, onClick = null, enabled = supported)
                            Column(Modifier.weight(1f)) {
                                Text(actionLabel(action), style = MaterialTheme.typography.bodyLarge)
                                if (!supported) Text(stringResource(R.string.action_unavailable), color = AergisColors.Caution)
                                if (restricted) Text(stringResource(R.string.ui_mapping_pointer_restriction),
                                    color = AergisColors.Caution, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (draft != AirAction.NONE) {
                    Text(stringResource(R.string.gesture_tuning_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.gesture_tuning_help))
                    if (!source.isLandmarkPose) TuningSlider(stringResource(R.string.tuning_confidence, confidence.toInt()), confidence, 65f..95f, 5) { confidence = it }
                    if (draft == AirAction.VOLUME_UP || draft == AirAction.VOLUME_DOWN)
                        TuningSlider(stringResource(R.string.tuning_volume, volume.toInt()), volume, 1f..5f, 3) { volume = it }
                    if (draft == AirAction.SCROLL_UP || draft == AirAction.SCROLL_DOWN)
                        TuningSlider(stringResource(R.string.tuning_scroll, scroll.toInt()), scroll, 15f..85f, 13) { scroll = it }
                    if (draft == AirAction.LONG_PRESS)
                        TuningSlider(stringResource(R.string.tuning_long_press, longPress.toInt()), longPress, 500f..2000f, 14) { longPress = it }
                    if (draft in setOf(AirAction.MEDIA_PLAY_PAUSE, AirAction.MEDIA_NEXT, AirAction.MEDIA_PREVIOUS))
                        Text(stringResource(R.string.action_media_help))
                }
                TextButton(onClick = { draftName = source.defaultAction.name
                    confidence = 65f; volume = 1f; scroll = 50f; longPress = 650f }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.ui_mapping_restore_default))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = Build.VERSION.SDK_INT >= draft.minimumSdk,
                onClick = { onSave(draft, tuning.copy(confidencePercent = confidence.toInt(),
                    volumeSteps = volume.toInt(), scrollPercent = scroll.toInt(),
                    longPressMs = longPress.toLong()).bounded()) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.ui_mapping_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.ui_mapping_cancel))
            }
        }
    )
}

@Composable
private fun TuningSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    change: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Slider(value = value, onValueChange = change, valueRange = range, steps = steps)
}

@Composable
internal fun actionLabel(action: AirAction): String = stringResource(when (action) {
    AirAction.TAP -> R.string.action_tap
    AirAction.DOUBLE_TAP -> R.string.action_double_tap
    AirAction.BACK -> R.string.action_back
    AirAction.HOME -> R.string.action_home
    AirAction.RECENTS -> R.string.action_recents
    AirAction.LONG_PRESS -> R.string.action_long_press
    AirAction.SCROLL_UP -> R.string.action_scroll_up
    AirAction.SCROLL_DOWN -> R.string.action_scroll_down
    AirAction.NOTIFICATIONS -> R.string.action_notifications
    AirAction.QUICK_SETTINGS -> R.string.action_quick_settings
    AirAction.DISMISS_SHADE -> R.string.action_dismiss_shade
    AirAction.VOLUME_UP -> R.string.action_volume_up
    AirAction.VOLUME_DOWN -> R.string.action_volume_down
    AirAction.MEDIA_PLAY_PAUSE -> R.string.action_media_play_pause
    AirAction.MEDIA_NEXT -> R.string.action_media_next
    AirAction.MEDIA_PREVIOUS -> R.string.action_media_previous
    AirAction.SCREENSHOT -> R.string.action_screenshot
    AirAction.LOCK_SCREEN -> R.string.action_lock_screen
    AirAction.APP_SEARCH -> R.string.action_app_search
    AirAction.WEB_SEARCH -> R.string.action_web_search
    AirAction.WIFI_CONTROLS -> R.string.action_wifi_controls
    AirAction.BLUETOOTH_SETTINGS -> R.string.action_bluetooth_settings
    AirAction.ROTATION_TOGGLE -> R.string.action_rotation_toggle
    AirAction.SWIPE_LEFT -> R.string.action_swipe_left
    AirAction.SWIPE_RIGHT -> R.string.action_swipe_right
    AirAction.ARM_MOTION -> R.string.action_arm_motion
    AirAction.POINTER_DRAG -> R.string.action_pointer_drag
    AirAction.NEXT_ITEM -> R.string.action_next_item
    AirAction.PREVIOUS_ITEM -> R.string.action_previous_item
    AirAction.SELECT_ITEM -> R.string.action_select_item
    AirAction.FOCUS_NEXT -> R.string.action_focus_next
    AirAction.FOCUS_PREVIOUS -> R.string.action_focus_previous
    AirAction.ACTIVATE_FOCUSED -> R.string.action_activate_focused
    AirAction.NONE -> R.string.action_disabled
})
