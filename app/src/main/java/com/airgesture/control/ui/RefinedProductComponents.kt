package com.airgesture.control.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.airgesture.control.R
import com.airgesture.control.RegisteredGesture

/** A graphite instrument panel, deliberately separate from action authority. */
@Composable
internal fun ControlCommandCenter(
    running: Boolean, actions: ProductActionState, statusLabel: Int, description: String,
    primaryLabel: Int, enabled: Boolean, onPrimary: () -> Unit, onStop: () -> Unit
) {
    val signal = when (actions) {
        ProductActionState.ARMED -> AergisColors.Success
        ProductActionState.PAUSED -> AergisColors.Caution
        else -> AergisColors.Yellow
    }
    Column(
        Modifier.fillMaxWidth().testTag("control-cockpit").clip(AergisShapes.Stage)
            .background(Brush.verticalGradient(listOf(
                Color(0xFF14171F), AergisColors.Deep, Color(0xFF1A1C2C))))
            .border(1.dp, AergisColors.Silver.copy(alpha = .34f), AergisShapes.Stage)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            ProductEyebrow(stringResource(R.string.ui_brand_subtitle), AergisColors.Silver)
            AergisStatusChip(stringResource(if (running) R.string.ui_capture_on else R.string.ui_capture_off), signal)
        }
        Box(Modifier.fillMaxWidth().height(208.dp), contentAlignment = Alignment.Center) {
            CommandField(Modifier.matchParentSize())
            Box(
                Modifier.size(196.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(
                        AergisColors.Stage.copy(alpha = .80f), AergisColors.StageShadow, AergisColors.Deep)))
                    .border(1.dp, AergisColors.Purple.copy(alpha = .75f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(170.dp).border(1.dp, AergisColors.Yellow.copy(alpha = .52f), CircleShape))
                Box(Modifier.size(141.dp).border(1.dp, AergisColors.StageHint.copy(alpha = .24f), CircleShape))
                HandDiagram(RegisteredGesture.OPEN_PALM, Modifier.size(132.dp), AergisColors.Silver, signal)
            }
        }
        HorizontalDivider(color = AergisColors.Silver.copy(alpha = .20f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProductEyebrow(stringResource(R.string.product_session_label), signal)
            Text(stringResource(statusLabel), style = MaterialTheme.typography.headlineLarge,
                color = AergisColors.Text, modifier = Modifier.semantics { heading() })
            Text(description, color = AergisColors.StageHint, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = onPrimary, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = AergisShapes.Control,
            colors = ButtonDefaults.buttonColors(
                containerColor = AergisColors.Yellow, contentColor = AergisColors.Deep,
                disabledContainerColor = AergisColors.StageShadow, disabledContentColor = AergisColors.StageHint
            )
        ) {
            AergisIcon(AergisGlyph.CONTROL, Modifier.size(19.dp),
                if (enabled) AergisColors.Deep else AergisColors.StageHint)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(primaryLabel), style = MaterialTheme.typography.labelLarge)
        }
        if (running && primaryLabel != R.string.stop_capture) {
            OutlinedButton(
                onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = AergisShapes.Control,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AergisColors.Silver),
                border = BorderStroke(1.dp, AergisColors.Silver.copy(alpha = .54f))
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
