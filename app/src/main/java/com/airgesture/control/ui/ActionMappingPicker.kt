package com.airgesture.control.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.airgesture.control.*
import com.airgesture.control.R

/** Local draft only: Cancel, outside tap and Back never write bindings or tuning. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionMappingPicker(
    gestureName: String, source: RegisteredGesture, current: AirAction, pointerEnabled: Boolean,
    tuning: GestureTuning, onSave: (AirAction, GestureTuning) -> Unit, onDismiss: () -> Unit
) {
    var draftName by rememberSaveable(source.name, current.name) { mutableStateOf(current.name) }
    var query by rememberSaveable(source.name) { mutableStateOf("") }
    var confidence by rememberSaveable(source.name) { mutableFloatStateOf(tuning.confidencePercent.toFloat()) }
    var volume by rememberSaveable(source.name) { mutableFloatStateOf(tuning.volumeSteps.toFloat()) }
    var scroll by rememberSaveable(source.name) { mutableFloatStateOf(tuning.scrollPercent.toFloat()) }
    var longPress by rememberSaveable(source.name) { mutableFloatStateOf(tuning.longPressMs.toFloat()) }
    val draft = AirAction.valueOf(draftName)
    val rows = AirAction.entries.filter { it.isUserMappable }.map { it to actionLabel(it) }
        .filter { (_, label) -> label.contains(query, ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AergisColors.Surface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SculptedHand(source, Modifier.size(72.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProductEyebrow(stringResource(R.string.product_assign_pose))
                        Text(gestureName, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Text(stringResource(R.string.product_map_intro), color = AergisColors.Muted)
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.product_search_actions)) }, singleLine = true)
                Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                    rows.groupBy { actionGroupRes(it.first) }.forEach { (group, actions) ->
                        Text(stringResource(group), color = AergisColors.Purple, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(vertical = 12.dp))
                        actions.forEach { (action, label) ->
                            val supported = Build.VERSION.SDK_INT >= action.minimumSdk
                            val restricted = action != AirAction.NONE && !GestureActionPolicy.isClassifierActionEnabled(true, action, pointerEnabled, source)
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(selected = action == draft, role = Role.RadioButton, enabled = supported,
                                    onClick = { draftName = action.name }).padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = action == draft, onClick = null, enabled = supported)
                                Column(Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.bodyLarge)
                                    if (!supported) Text(stringResource(R.string.action_unavailable), color = AergisColors.Caution)
                                    if (restricted) Text(stringResource(R.string.ui_mapping_pointer_restriction), color = AergisColors.Caution,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    if (rows.isEmpty()) Text(stringResource(R.string.product_no_matches), modifier = Modifier.padding(16.dp))
                }
                if (draft != AirAction.NONE) {
                    if (!source.isLandmarkPose) TuningSlider(stringResource(R.string.tuning_confidence, confidence.toInt()), confidence, 65f..95f, 5) { confidence = it }
                    if (draft == AirAction.VOLUME_UP || draft == AirAction.VOLUME_DOWN)
                        TuningSlider(stringResource(R.string.tuning_volume, volume.toInt()), volume, 1f..5f, 3) { volume = it }
                    if (draft == AirAction.SCROLL_UP || draft == AirAction.SCROLL_DOWN)
                        TuningSlider(stringResource(R.string.tuning_scroll, scroll.toInt()), scroll, 15f..85f, 13) { scroll = it }
                    if (draft == AirAction.LONG_PRESS)
                        TuningSlider(stringResource(R.string.tuning_long_press, longPress.toInt()), longPress, 500f..2000f, 14) { longPress = it }
                    if (draft in setOf(AirAction.MEDIA_PLAY_PAUSE, AirAction.MEDIA_NEXT, AirAction.MEDIA_PREVIOUS))
                        Text(stringResource(R.string.action_media_help), color = AergisColors.Muted)
                    if (!GestureActionPolicy.isClassifierActionEnabled(true, draft, pointerEnabled, source))
                        Text(stringResource(R.string.product_saved_restricted), color = AergisColors.Caution)
                }
                TextButton(onClick = {
                    val defaults = GestureTuning()
                    draftName = source.defaultAction.name
                    confidence = defaults.confidencePercent.toFloat(); volume = defaults.volumeSteps.toFloat()
                    scroll = defaults.scrollPercent.toFloat(); longPress = defaults.longPressMs.toFloat()
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_mapping_restore_default)) }
            }
            HorizontalDivider(color = AergisColors.Outline)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                AergisButton(onClick = { onSave(draft, tuning.copy(confidencePercent = confidence.toInt(), volumeSteps = volume.toInt(),
                    scrollPercent = scroll.toInt(), longPressMs = longPress.toLong()).bounded()) },
                    enabled = Build.VERSION.SDK_INT >= draft.minimumSdk, primary = true, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_mapping_save))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.ui_mapping_cancel)) }
            }
        }
    }
}

private fun actionGroupRes(action: AirAction): Int = when {
    action == AirAction.NONE -> R.string.product_group_off
    action == AirAction.ARM_MOTION -> R.string.product_group_motion
    action.usesPointerTouch -> R.string.product_group_pointer
    action in setOf(AirAction.APP_SEARCH, AirAction.WEB_SEARCH, AirAction.MEDIA_PLAY_PAUSE, AirAction.MEDIA_NEXT, AirAction.MEDIA_PREVIOUS) -> R.string.product_group_media
    action in setOf(AirAction.VOLUME_UP, AirAction.VOLUME_DOWN, AirAction.SCREENSHOT, AirAction.LOCK_SCREEN,
        AirAction.WIFI_CONTROLS, AirAction.BLUETOOTH_SETTINGS, AirAction.ROTATION_TOGGLE) -> R.string.product_group_device
    else -> R.string.product_group_navigation
}

@Composable
private fun TuningSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChanged: (Float) -> Unit) {
    Text(label, color = AergisColors.Muted)
    Slider(value = value, onValueChange = onChanged, valueRange = range, steps = steps)
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
