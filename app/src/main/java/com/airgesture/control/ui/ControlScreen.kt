package com.airgesture.control.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgesture.control.*
import com.airgesture.control.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun ControlScreen(
    accessibilityEnabled: Boolean,
    pointerEnabled: Boolean,
    gesturesEnabled: Boolean,
    startError: String?,
    onCapture: () -> Unit,
    onArm: () -> Unit,
    onAccessibility: () -> Unit,
    onNavigate: (AergisDestination) -> Unit
) {
    val flow = remember { AirRuntime.uiState.map { HomeUiState.from(it) }.distinctUntilChanged() }
    val runtime by flow.collectAsStateWithLifecycle(initialValue = HomeUiState.from(AirRuntime.uiStateSnapshot()))
    val status = runtime.status(accessibilityEnabled, pointerEnabled)
    val statusText = stringResource(status.label())
    val accent = status.color()
    val modeText = stringResource(when {
        !gesturesEnabled -> R.string.ui_actions_disabled
        runtime.controlMode == ControlMode.PAUSED || runtime.motionActive ||
            (runtime.controlMode == ControlMode.ARMED && runtime.foregroundSafety != ForegroundSafety.SAFE) -> R.string.ui_actions_paused
        runtime.controlMode == ControlMode.ARMED -> R.string.ui_actions_armed
        else -> R.string.ui_actions_disarmed
    })

    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { AergisSection(stringResource(R.string.ui_control_title), stringResource(R.string.ui_control_description)) }
        item {
            AergisPanel(accent = accent.copy(alpha = .5f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActivationEmblem(runtime.running, accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(statusText, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { stateDescription = statusText })
                        Text(stringResource(when (status) {
                            HomeStatus.SETUP_REQUIRED -> R.string.ui_setup_description
                            HomeStatus.IDLE -> R.string.ui_idle_description
                            HomeStatus.ERROR -> R.string.ui_error_description
                            HomeStatus.CAMERA_STARTING -> R.string.ui_camera_starting_description
                            HomeStatus.VISION_STARTING -> R.string.ui_vision_starting_description
                            HomeStatus.PAUSED -> R.string.ui_paused_description
                            HomeStatus.POINTER_PAUSED -> R.string.ui_pointer_paused_description
                            HomeStatus.TRACKING -> R.string.ui_tracking_description
                            HomeStatus.SEARCHING -> R.string.ui_searching_description
                        }), style = MaterialTheme.typography.bodyMedium, color = AergisColors.Muted)
                    }
                }
                AergisButton(onCapture, modifier = Modifier.fillMaxWidth(), primary = true) {
                    AergisIcon(AergisGlyph.POWER, Modifier.size(20.dp), AergisColors.Background)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (runtime.running) R.string.stop_capture else R.string.start_capture))
                }
                AergisArmControl(
                    armed = runtime.controlMode == ControlMode.ARMED,
                    enabled = runtime.running && (runtime.controlMode == ControlMode.ARMED || !runtime.motionActive),
                    stateText = modeText, onClick = onArm
                )
                // Tracking and control authority are different facts and are shown as separate chips.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AergisStatusChip(statusText, accent)
                    AergisStatusChip(modeText, if (gesturesEnabled && runtime.controlMode == ControlMode.ARMED) AergisColors.Success else AergisColors.Muted)
                }
                Text(stringResource(R.string.ui_capture_arm_explanation), color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!accessibilityEnabled) item {
            AergisPanel(accent = AergisColors.Caution.copy(alpha = .5f)) {
                Text(stringResource(R.string.ui_setup_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.ui_setup_help), color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium)
                AergisButton(onAccessibility, Modifier.fillMaxWidth()) { Text(stringResource(R.string.accessibility_settings)) }
            }
        }
        startError?.let { error -> item { ErrorPanel(stringResource(R.string.session_start_error_title), error) } }
        if (runtime.running) runtime.visionError?.let { error -> item { ErrorPanel(stringResource(R.string.vision_error_title), error) } }
        item {
            AergisPanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.ui_tracking_snapshot), style = MaterialTheme.typography.titleMedium)
                    AergisMotionTrace(if (status == HomeStatus.TRACKING) AergisColors.Cyan else AergisColors.Muted.copy(alpha = .6f),
                        Modifier.size(72.dp, 28.dp))
                }
                SnapshotRow(stringResource(R.string.ui_camera), stringResource(if (runtime.cameraReady) R.string.status_ready else R.string.status_not_active), runtime.cameraReady)
                SnapshotRow(stringResource(R.string.ui_vision), stringResource(if (runtime.visionReady) R.string.status_ready else R.string.status_not_ready), runtime.visionReady)
                SnapshotRow(stringResource(R.string.ui_hand), if (runtime.handsDetected > 0) runtime.handedness else stringResource(R.string.ui_no_hand), runtime.handsDetected > 0)
                Text(stringResource(runtime.pointerFeedback.label()), color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium)
                if (status == HomeStatus.PAUSED) Text(runtime.safetyReason, color = AergisColors.Caution, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            AergisSection(stringResource(R.string.ui_quick_access))
        }
        item {
            AergisPanel {
                Shortcut(AergisDestination.LIVE, R.string.ui_live_shortcut, R.string.ui_live_shortcut_description, onNavigate)
                Shortcut(AergisDestination.CONTROLS, R.string.ui_controls_shortcut, R.string.ui_controls_shortcut_description, onNavigate)
                Shortcut(AergisDestination.MORE, R.string.ui_more_shortcut, R.string.ui_more_shortcut_description, onNavigate)
            }
        }
        item { Text(stringResource(R.string.ui_preview_note), color = AergisColors.Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ActivationEmblem(active: Boolean, accent: Color) {
    Box(Modifier.size(64.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            // A bounded translucent halo, not a blur or animated shader.
            if (active) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .18f), Color.Transparent), center, size.width / 2))
            drawCircle(accent.copy(alpha = .3f), size.width * .43f, style = Stroke(1.dp.toPx()))
            drawCircle(accent.copy(alpha = .7f), size.width * .34f, style = Stroke(1.dp.toPx()))
        }
        AergisIcon(AergisGlyph.POWER, Modifier.size(26.dp), accent)
    }
}

@Composable
private fun SnapshotRow(title: String, value: String, available: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = AergisColors.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = if (available) AergisColors.Success else AergisColors.Text)
    }
}

@Composable
private fun Shortcut(destination: AergisDestination, title: Int, description: Int, navigate: (AergisDestination) -> Unit) {
    AergisButton({ navigate(destination) }, Modifier.fillMaxWidth()) {
        AergisIcon(destination.glyph, Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = AergisColors.Muted)
        }
    }
}

@Composable
private fun ErrorPanel(title: String, error: String) {
    AergisPanel(accent = AergisColors.Error.copy(alpha = .5f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AergisColors.Error)
        Text(error, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun HomeStatus.label() = when (this) {
    HomeStatus.SETUP_REQUIRED -> R.string.ui_setup_required
    HomeStatus.IDLE -> R.string.ui_idle
    HomeStatus.ERROR -> R.string.ui_needs_attention
    HomeStatus.CAMERA_STARTING -> R.string.ui_camera_starting
    HomeStatus.VISION_STARTING -> R.string.ui_vision_starting
    HomeStatus.PAUSED -> R.string.ui_actions_paused
    HomeStatus.POINTER_PAUSED -> R.string.ui_pointer_paused
    HomeStatus.TRACKING -> R.string.ui_tracking_live
    HomeStatus.SEARCHING -> R.string.ui_waiting_for_hand
}

private fun HomeStatus.color() = when (this) {
    HomeStatus.TRACKING -> AergisColors.Cyan
    HomeStatus.ERROR -> AergisColors.Error
    HomeStatus.SETUP_REQUIRED, HomeStatus.PAUSED -> AergisColors.Caution
    HomeStatus.CAMERA_STARTING, HomeStatus.VISION_STARTING -> AergisColors.Blue
    else -> AergisColors.Muted
}

internal fun PointerFeedback.label() = when (this) {
    PointerFeedback.TRACKING -> R.string.pointer_feedback_tracking
    PointerFeedback.COASTING -> R.string.pointer_feedback_coasting
    PointerFeedback.NO_HAND -> R.string.pointer_feedback_no_hand
    PointerFeedback.DISABLED -> R.string.pointer_feedback_disabled
    PointerFeedback.AMBIGUOUS -> R.string.pointer_feedback_ambiguous
    PointerFeedback.INVALID_TIP -> R.string.pointer_feedback_invalid_tip
    PointerFeedback.VISION_REJECTED -> R.string.pointer_feedback_vision_rejected
}
