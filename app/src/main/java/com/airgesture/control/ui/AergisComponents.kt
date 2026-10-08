package com.airgesture.control.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.airgesture.control.R

@Composable
internal fun AergisPanel(
    modifier: Modifier = Modifier,
    accent: Color = AergisColors.Outline,
    content: @Composable ColumnScope.() -> Unit
) {
    val border by animateColorAsState(accent, tween(AergisMotion.StateMs, easing = AergisMotion.Easing), label = "panel state")
    Surface(
        modifier = modifier.fillMaxWidth(), shape = AergisShapes.Card,
        color = AergisColors.Surface, border = BorderStroke(1.dp, border), tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(AergisSpace.Content), verticalArrangement = Arrangement.spacedBy(AergisSpace.Medium), content = content)
    }
}

/** Native Button keeps click/ripple, focus, disabled and TalkBack behavior. */
@Composable
internal fun AergisButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val brush = if (primary && enabled) Brush.linearGradient(listOf(AergisColors.Cyan, AergisColors.Blue, AergisColors.Violet))
        else Brush.linearGradient(listOf(AergisColors.Raised, AergisColors.Raised))
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = if (primary) 56.dp else 48.dp).background(brush, AergisShapes.Control),
        shape = AergisShapes.Control,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = if (primary) AergisColors.Background else AergisColors.Text,
            disabledContainerColor = Color.Transparent, disabledContentColor = AergisColors.Muted.copy(alpha = .55f)
        ),
        border = if (primary && enabled) null else BorderStroke(1.dp, AergisColors.Outline),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), content = content
    )
}

@Composable
internal fun AergisStatusChip(text: String, color: Color = AergisColors.Muted) {
    Surface(color = color.copy(alpha = .10f), shape = AergisShapes.Chip, border = BorderStroke(1.dp, color.copy(alpha = .35f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.size(6.dp), shape = AergisShapes.Chip, color = color) {}
            Text(text, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun AergisSection(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        description?.let { Text(it, color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun AergisSettingRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        val stateLabel = stringResource(if (checked) R.string.ui_switch_on else R.string.ui_switch_off, title)
        Switch(checked, onCheckedChange, modifier = Modifier.semantics { stateDescription = stateLabel })
    }
}

@Composable
internal fun AergisSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChanged: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AergisColors.Muted)
        Slider(value = value.coerceIn(range), onValueChange = onChanged, valueRange = range,
            modifier = Modifier.semantics { stateDescription = label })
    }
}
