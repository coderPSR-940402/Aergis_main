package com.airgesture.control

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ControlHandSelector(selected: ControlHandPreference, onSelected: (ControlHandPreference) -> Unit) {
    Row {
        ControlHandPreference.entries.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelected(option) }, label = { Text(option.name) })
        }
    }
}
