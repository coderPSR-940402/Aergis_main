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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airgesture.control.R
import com.airgesture.control.RegisteredGesture

/** Eligibility is supplied by ProductSession; this component never changes runtime itself. */
@Composable
internal fun ControlCommandCenter(
    running: Boolean, armed: Boolean, canToggleArm: Boolean, actions: ProductActionState,
    onCapture: () -> Unit, onArm: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProductEyebrow(stringResource(R.string.refined_everyday))
                Text(stringResource(R.string.refined_home_title), style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() })
            }
            val cameraLabel = stringResource(if (running) R.string.stop_capture else R.string.start_capture)
            FilledTonalIconButton(onClick = onCapture,
                modifier = Modifier.size(52.dp).semantics { contentDescription = cameraLabel },
                shape = AergisShapes.Control,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = AergisColors.Raised)) {
                AergisIcon(AergisGlyph.CAMERA, Modifier.size(22.dp), if (running) AergisColors.Silver else AergisColors.Muted)
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = AergisColors.Outline.copy(alpha = .55f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            AergisStatusChip(stringResource(actions.label()),
                if (actions == ProductActionState.ARMED) AergisColors.Yellow else AergisColors.Purple)
            Text(stringResource(R.string.refined_instrument_code), style = MaterialTheme.typography.labelSmall, color = AergisColors.Muted)
        }
        ControlInstrument(actions == ProductActionState.ARMED, !running,
            Modifier.align(Alignment.CenterHorizontally).size(216.dp))
        Text(stringResource(if (running) R.string.refined_hand_caption else R.string.product_hero_stopped_help),
            style = MaterialTheme.typography.bodySmall, color = AergisColors.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        AergisButton(onClick = if (running || armed) onArm else onCapture,
            enabled = armed || !running || canToggleArm, primary = true, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (armed) R.string.disarm_control else if (!running) R.string.start_capture else R.string.arm_control),
                modifier = Modifier.weight(1f))
            AergisIcon(if (armed) AergisGlyph.POWER else AergisGlyph.ARROW, Modifier.size(20.dp), AergisColors.Background)
        }
        Text(stringResource(if (armed) R.string.refined_disarm_caption else R.string.refined_safety_caption),
            style = MaterialTheme.typography.bodySmall, color = AergisColors.Muted)
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

@Composable
internal fun GestureLibraryIntro() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(RegisteredGesture.entries.size.toString(), style = MaterialTheme.typography.displayMedium,
                color = AergisColors.SilverBright)
            Text(stringResource(R.string.refined_possibilities), style = MaterialTheme.typography.titleLarge,
                color = AergisColors.Muted)
            Text(stringResource(R.string.refined_language), style = MaterialTheme.typography.bodyMedium,
                color = AergisColors.Muted)
        }
        SculptedHand(RegisteredGesture.VICTORY, Modifier.width(120.dp).height(166.dp))
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
