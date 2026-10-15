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
    onSave: (AirAction) -> Unit,
    onDismiss: () -> Unit
) {
    var draftName by rememberSaveable(source.name, current.name) { mutableStateOf(current.name) }
    val draft = AirAction.valueOf(draftName)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_mapping_picker_title, gestureName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ui_mapping_picker_help))
                Column(Modifier.selectableGroup()) {
                    AirAction.entries.forEach { action ->
                        val restricted = action != AirAction.NONE && !GestureActionPolicy.isClassifierActionEnabled(
                            gesturesEnabled = true, action = action, pointerControlActive = pointerEnabled
                        )
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .selectable(selected = action == draft, role = Role.RadioButton,
                                    onClick = { draftName = action.name })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = action == draft, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(actionLabel(action), style = MaterialTheme.typography.bodyLarge)
                                if (restricted) Text(stringResource(R.string.ui_mapping_pointer_restriction),
                                    color = AergisColors.Caution, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                TextButton(onClick = { draftName = source.defaultAction.name }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.ui_mapping_restore_default))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }, modifier = Modifier.heightIn(min = 48.dp)) {
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
internal fun actionLabel(action: AirAction): String = stringResource(when (action) {
    AirAction.TAP -> R.string.action_tap
    AirAction.DOUBLE_TAP -> R.string.action_double_tap
    AirAction.BACK -> R.string.action_back
    AirAction.HOME -> R.string.action_home
    AirAction.RECENTS -> R.string.action_recents
    AirAction.LONG_PRESS -> R.string.action_long_press
    AirAction.SCROLL_UP -> R.string.action_scroll_up
    AirAction.SCROLL_DOWN -> R.string.action_scroll_down
    AirAction.NONE -> R.string.action_disabled
})
