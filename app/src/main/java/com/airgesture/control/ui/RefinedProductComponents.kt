package com.airgesture.control.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airgesture.control.R
import com.airgesture.control.RegisteredGesture

/** Eligibility is supplied by ProductSession; this component never changes runtime itself. */
@Composable
internal fun ControlCommandCenter(
    running: Boolean, actions: ProductActionState, statusLabel: Int, description: String,
    primaryLabel: Int, enabled: Boolean, onPrimary: () -> Unit, onStop: () -> Unit
) {
    val accent = when (actions) {
        ProductActionState.ARMED -> AergisColors.Yellow
        ProductActionState.PAUSED -> AergisColors.Caution
        else -> AergisColors.Purple
    }
    AergisPanel(accent = accent.copy(alpha = .55f)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProductEyebrow(stringResource(R.string.product_session_label))
                Text(stringResource(statusLabel), style = MaterialTheme.typography.headlineMedium,
                    color = accent, modifier = Modifier.semantics { heading() })
            }
            if (LocalDensity.current.fontScale <= 1.3f) {
                ControlInstrument(actions == ProductActionState.ARMED, !running, Modifier.size(90.dp))
            }
        }
        Text(description, color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium)
        AergisButton(onPrimary, Modifier.fillMaxWidth(), enabled = enabled, primary = true) {
            Text(stringResource(primaryLabel))
        }
        if (running && primaryLabel != R.string.stop_capture) {
            AergisButton(onStop, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop_capture)) }
        }
    }
}

@Composable
internal fun ProductEyebrow(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = AergisColors.Muted)
}

@Composable
internal fun ProductInputTiles(assignedCount: Int, onNavigate: (AergisDestination) -> Unit) {
    val stack = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (stack || maxWidth < 330.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InputTile(AergisDestination.GESTURES, stringResource(R.string.product_assigned_count, assignedCount, RegisteredGesture.entries.size), Modifier.fillMaxWidth(), onNavigate)
                InputTile(AergisDestination.MOTION, stringResource(R.string.refined_motion_caption), Modifier.fillMaxWidth(), onNavigate)
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InputTile(AergisDestination.GESTURES, stringResource(R.string.product_assigned_count, assignedCount, RegisteredGesture.entries.size), Modifier.weight(1f), onNavigate)
            InputTile(AergisDestination.MOTION, stringResource(R.string.refined_motion_caption), Modifier.weight(1f), onNavigate)
        }
    }
}

@Composable
private fun InputTile(destination: AergisDestination, caption: String, modifier: Modifier,
    onNavigate: (AergisDestination) -> Unit) {
    Surface(onClick = { onNavigate(destination) }, modifier = modifier, shape = AergisShapes.Card,
        color = AergisColors.Surface, border = BorderStroke(1.dp, AergisColors.Outline.copy(alpha = .55f))) {
        Column(Modifier.background(Brush.verticalGradient(listOf(AergisColors.Raised.copy(alpha = .55f), AergisColors.Surface)))
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ProductEyebrow(stringResource(if (destination == AergisDestination.GESTURES) R.string.refined_gesture_number else R.string.refined_motion_number))
                Spacer(Modifier.weight(1f))
                AergisIcon(AergisGlyph.ARROW, Modifier.size(16.dp), AergisColors.Muted)
            }
            if (destination == AergisDestination.GESTURES)
                SculptedHand(RegisteredGesture.VICTORY, Modifier.fillMaxWidth().height(96.dp))
            else DirectionInstrument(Modifier.fillMaxWidth().height(96.dp))
            Text(stringResource(destination.label), style = MaterialTheme.typography.titleSmall)
            Text(caption, color = AergisColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Entire row is one native, focusable button; saved assignment and eligibility remain caller-owned. */
@Composable
internal fun GestureMappingRow(gesture: RegisteredGesture, label: String, action: String,
    help: String, restriction: String?, onClick: () -> Unit) {
    Column {
        Surface(onClick = onClick, color = Color.Transparent, shape = AergisShapes.Control) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).background(AergisColors.Surface, AergisShapes.Control), contentAlignment = Alignment.Center) {
                    SculptedHand(gesture, Modifier.size(58.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(action, style = MaterialTheme.typography.bodyMedium, color = AergisColors.Yellow)
                    Text(help, style = MaterialTheme.typography.bodySmall, color = AergisColors.Muted)
                    restriction?.let { Text(it, color = AergisColors.Caution, style = MaterialTheme.typography.bodySmall) }
                }
                AergisIcon(AergisGlyph.ARROW, Modifier.size(20.dp), AergisColors.Muted)
            }
        }
        HorizontalDivider(color = AergisColors.Outline.copy(alpha = .5f))
    }
}
