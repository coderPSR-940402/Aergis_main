package com.airgesture.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgesture.control.*
import com.airgesture.control.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun ControlScreen(
    accessibilityEnabled: Boolean, pointerEnabled: Boolean, pointerClickingEnabled: Boolean,
    gesturesEnabled: Boolean, assignedCount: Int, startError: String?, onCapture: () -> Unit,
    onArm: () -> Unit, onGestures: (Boolean) -> Unit, onNavigate: (AergisDestination) -> Unit
) {
    // Avoid subscribing Home to per-frame pointer coordinates, evidence and counters.
    val flow = remember { AirRuntime.uiState.map { HomeUiState.from(it) }.distinctUntilChanged() }
    val home by flow.collectAsStateWithLifecycle(initialValue = HomeUiState.from(AirRuntime.uiStateSnapshot()))
    val snapshot = AirRuntime.uiStateSnapshot().copy(running = home.running, cameraReady = home.cameraReady,
        visionReady = home.visionReady, visionError = home.visionError, controlMode = home.controlMode,
        foregroundSafety = home.foregroundSafety)
    val shown = ProductSession.from(snapshot, accessibilityEnabled, gesturesEnabled, pointerEnabled, pointerClickingEnabled)
    val armed = home.controlMode == ControlMode.ARMED
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            ControlCommandCenter(home.running, armed, shown.canToggleArm, shown.actions, onCapture, onArm)
        }
        if (!accessibilityEnabled) item {
            AergisPanel(accent = AergisColors.Caution) {
                Text(stringResource(R.string.product_setup_required), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.product_setup_required_help), color = AergisColors.Muted)
                AergisButton({ onNavigate(AergisDestination.WELCOME) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.product_continue)) }
            }
        }
        if (shown.actions == ProductActionState.PAUSED) item { Text(home.safetyReason, color = AergisColors.Caution) }
        startError?.let { error -> item { Text(error, color = AergisColors.Error) } }
        home.visionError?.let { error -> item { Text(error, color = AergisColors.Error) } }
        item { ProductInputTiles(assignedCount, onNavigate) }
        item {
            AergisPanel {
                AergisSettingRow(stringResource(R.string.product_gestures), stringResource(R.string.product_gesture_switch_help), gesturesEnabled, onGestures)
                ProductLink(AergisDestination.LEARN, stringResource(R.string.product_learn_help), onNavigate)
            }
        }
        item {
            AergisPanel {
                Text(stringResource(R.string.product_feedback_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(when {
                    !home.running -> R.string.product_camera_off
                    !home.cameraReady || !home.visionReady -> R.string.product_camera_starting
                    home.handsDetected == 0 -> R.string.product_no_hand
                    else -> R.string.product_hand_visible
                }, home.handedness), color = AergisColors.Muted)
                Text(stringResource(if (pointerEnabled) R.string.product_pointer_on_help else R.string.product_pointer_off_help), style = MaterialTheme.typography.bodySmall)
                ProductLink(AergisDestination.LIVE, stringResource(R.string.product_live_help), onNavigate)
            }
        }
        item { ProductLink(AergisDestination.PRECISION, stringResource(R.string.product_precision_help), onNavigate) }
    }
}

internal fun ProductActionState.label(): Int = when (this) {
    ProductActionState.STOPPED -> R.string.product_action_stopped
    ProductActionState.DISABLED -> R.string.product_action_disabled
    ProductActionState.DISARMED -> R.string.product_action_disarmed
    ProductActionState.ARMED -> R.string.product_action_armed
    ProductActionState.PAUSED -> R.string.product_action_paused
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
