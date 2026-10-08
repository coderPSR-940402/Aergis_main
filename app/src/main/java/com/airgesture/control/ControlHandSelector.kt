package com.airgesture.control
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ControlHandSelector(selected: ControlHandPreference, onSelected: (ControlHandPreference) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlHandPreference.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(stringResource(option.labelRes())) }
            )
        }
    }
}

private fun ControlHandPreference.labelRes(): Int = when (this) {
    ControlHandPreference.LEFT -> R.string.hand_left
    ControlHandPreference.RIGHT -> R.string.hand_right
    ControlHandPreference.EITHER -> R.string.hand_either
}
