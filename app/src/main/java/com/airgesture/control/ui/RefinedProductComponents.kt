package com.airgesture.control.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airgesture.control.R
import com.airgesture.control.RegisteredGesture

/** Session commands use the existing eligibility and authority callbacks. */
@Composable
internal fun ControlCommandCenter(
    running: Boolean, actions: ProductActionState, statusLabel: Int, description: String,
    primaryLabel: Int, enabled: Boolean, onPrimary: () -> Unit, onStop: () -> Unit
) {
    val signal = when (actions) {
        ProductActionState.ARMED, ProductActionState.PAUSED -> AergisColors.Yellow
        else -> AergisColors.StageHint
    }
    Column(
        Modifier.fillMaxWidth().clip(AergisShapes.Stage)
            .background(Brush.linearGradient(listOf(AergisColors.Stage, AergisColors.StageShadow)))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showArt = maxWidth >= 290.dp && LocalDensity.current.fontScale <= 1.25f
            if (showArt) {
                CommandField(Modifier.matchParentSize())
                HandDiagram(RegisteredGesture.OPEN_PALM, Modifier.align(Alignment.CenterEnd).size(135.dp),
                    AergisColors.StageHint, signal)
            }
            Column(
                Modifier.fillMaxWidth(if (showArt) .57f else 1f).heightIn(min = if (showArt) 146.dp else 0.dp),
                verticalArrangement = Arrangement.Center
            ) {
                ProductEyebrow(stringResource(R.string.product_session_label), signal)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(statusLabel),
                    style = if (showArt) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
                    color = AergisColors.OnStage, modifier = Modifier.semantics { heading() })
            }
        }
        Text(description, color = AergisColors.StageHint, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), enabled = enabled,
            shape = AergisShapes.Chip,
            colors = ButtonDefaults.buttonColors(
                containerColor = AergisColors.Yellow, contentColor = AergisColors.Deep,
                disabledContainerColor = AergisColors.StageShadow, disabledContentColor = AergisColors.StageHint
            )
        ) {
            Text(stringResource(primaryLabel), style = MaterialTheme.typography.labelLarge)
        }
        if (running && primaryLabel != R.string.stop_capture) {
            OutlinedButton(
                onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = AergisShapes.Chip,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AergisColors.OnStage),
                border = BorderStroke(1.dp, AergisColors.StageHint)
            ) {
                Text(stringResource(R.string.stop_capture))
            }
        }
    }
}

@Composable
internal fun ProductEyebrow(text: String, color: Color = AergisColors.Purple) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
internal fun ProductInputTiles(assignedCount: Int, onNavigate: (AergisDestination) -> Unit) {
    val scale = LocalDensity.current.fontScale
    val assignments = stringResource(R.string.product_assigned_count, assignedCount, RegisteredGesture.entries.size)
    val motion = stringResource(R.string.product_motion_tile)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (scale > 1.3f || maxWidth < 330.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InputTile(AergisDestination.GESTURES, assignments, Modifier.fillMaxWidth(), onNavigate)
                InputTile(AergisDestination.MOTION, motion, Modifier.fillMaxWidth(), onNavigate)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputTile(AergisDestination.GESTURES, assignments, Modifier.weight(1f), onNavigate)
                InputTile(AergisDestination.MOTION, motion, Modifier.weight(1f), onNavigate)
            }
        }
    }
}

@Composable
private fun InputTile(
    destination: AergisDestination, caption: String, modifier: Modifier,
    onNavigate: (AergisDestination) -> Unit
) {
    val gestures = destination == AergisDestination.GESTURES
    Surface(
        onClick = { onNavigate(destination) }, modifier = modifier, shape = AergisShapes.Card,
        color = if (gestures) AergisColors.Raised else AergisColors.Surface,
        border = BorderStroke(1.dp, AergisColors.Outline)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AergisIcon(destination.glyph, Modifier.size(25.dp), AergisColors.Purple)
                Spacer(Modifier.weight(1f))
                AergisIcon(AergisGlyph.ARROW, Modifier.size(16.dp), AergisColors.Purple)
            }
            Text(stringResource(destination.label), style = MaterialTheme.typography.titleSmall)
            Text(caption, color = AergisColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Entire row is one native, focusable button. */
@Composable
internal fun GestureMappingRow(
    gesture: RegisteredGesture, label: String, action: String,
    help: String, restriction: String?, onClick: () -> Unit
) {
    Surface(onClick = onClick, color = AergisColors.Surface, shape = AergisShapes.Card,
        modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, AergisColors.Outline)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(62.dp).background(AergisColors.Raised, AergisShapes.Control), contentAlignment = Alignment.Center) {
                HandDiagram(gesture, Modifier.size(51.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                AergisStatusChip(action, AergisColors.Purple)
                Text(help, style = MaterialTheme.typography.bodySmall, color = AergisColors.Muted)
                restriction?.let { Text(it, color = AergisColors.Caution, style = MaterialTheme.typography.bodySmall) }
            }
            AergisIcon(AergisGlyph.ARROW, Modifier.size(18.dp), AergisColors.Purple)
        }
    }
}
