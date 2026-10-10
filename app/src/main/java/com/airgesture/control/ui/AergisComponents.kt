package com.airgesture.control.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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


/** Segmented sub-navigation. Selection is announced by role and state, not by colour alone. */
@Composable
internal fun AergisSegmentedTabs(labels: List<String>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(AergisColors.Surface, AergisShapes.Control).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .background(if (isSelected) AergisColors.Raised else Color.Transparent, AergisShapes.Segment)
                    .border(1.dp, if (isSelected) AergisColors.Cyan.copy(alpha = .6f) else Color.Transparent, AergisShapes.Segment)
                    .selectable(selected = isSelected, onClick = { onSelected(index) }, role = Role.Tab),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) AergisColors.Cyan else AergisColors.Muted)
            }
        }
    }
}

/** A real measurement. A null value renders an explicit "unavailable" state, never a placeholder number. */
@Composable
internal fun AergisMetric(label: String, value: String?, modifier: Modifier = Modifier) {
    val shown = value ?: stringResource(R.string.ui_metric_unavailable)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $shown" }) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AergisColors.Muted)
        Text(shown, style = AergisType.Telemetry, color = if (value == null) AergisColors.Muted else AergisColors.Text)
    }
}

/**
 * ARM / DISARM. Control authority is shown separately from tracking: green offers to arm,
 * red offers to disarm, and the state is also spoken and written, not colour alone.
 */
@Composable
internal fun AergisArmControl(armed: Boolean, enabled: Boolean, stateText: String, onClick: () -> Unit) {
    val accent = if (armed) AergisColors.Error else AergisColors.Success
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { stateDescription = stateText },
        shape = AergisShapes.Brand,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = .16f), contentColor = accent,
            disabledContainerColor = AergisColors.Surface, disabledContentColor = AergisColors.Muted.copy(alpha = .55f)
        ),
        border = BorderStroke(1.dp, if (enabled) accent else AergisColors.Outline)
    ) {
        AergisIcon(AergisGlyph.POWER, Modifier.size(22.dp), if (enabled) accent else AergisColors.Muted)
        Spacer(Modifier.width(10.dp))
        Text(stringResource(if (armed) R.string.disarm_control else R.string.arm_control),
            style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Aergis "motion trace": raw → stabilised → final pointer, three thin traces converging on one point.
 * Static and decorative; colour carries tracking state supplied by the caller.
 */
@Composable
internal fun AergisMotionTrace(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val end = Offset(size.width - 4.dp.toPx(), size.height / 2f)
        val stroke = Stroke(1.2f * density, cap = StrokeCap.Round)
        fun trace(startY: Float, wave: Float, alpha: Float) {
            val path = Path().apply {
                moveTo(0f, startY)
                cubicTo(size.width * .35f, startY + wave, size.width * .65f, end.y - wave * .4f, end.x, end.y)
            }
            drawPath(path, color.copy(alpha = alpha), style = stroke)
        }
        trace(size.height * .12f, size.height * .55f, .35f)
        trace(size.height * .88f, -size.height * .35f, .6f)
        trace(size.height * .5f, size.height * .1f, .95f)
        drawCircle(color, 3.dp.toPx(), end)
    }
}
