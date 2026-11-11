package com.airgesture.control.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgesture.control.*
import com.airgesture.control.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun ControlScreen(
    accessibilityEnabled: Boolean, pointerClickingEnabled: Boolean,
    gesturesEnabled: Boolean, assignedCount: Int, startError: String?, onStart: () -> Unit, onStop: () -> Unit,
    onArm: (Boolean) -> Unit, onGestures: (Boolean) -> Unit, onNavigate: (AergisDestination) -> Unit,
    cameraAllowed: Boolean = true
) {
    // Do not recompose the dashboard for every camera frame.
    val flow = remember { AirRuntime.uiState.map { HomeUiState.from(it) }.distinctUntilChanged() }
    val home by flow.collectAsStateWithLifecycle(initialValue = HomeUiState.from(AirRuntime.uiStateSnapshot()))
    val visionError = home.visionError
    val session = ProductSession.from(home.running, home.cameraReady, home.visionReady, visionError,
        home.controlMode, home.foregroundSafety, accessibilityEnabled, gesturesEnabled || pointerClickingEnabled)
    val armed = home.controlMode == ControlMode.ARMED
    val setupMissing = !cameraAllowed || !accessibilityEnabled
    val noActions = !gesturesEnabled && !pointerClickingEnabled
    val manualPause = home.controlMode == ControlMode.PAUSED
    val primaryLabel = when {
        armed -> R.string.disarm_control
        setupMissing -> R.string.product_complete_setup
        !home.running -> R.string.start_capture
        visionError != null -> R.string.stop_capture
        noActions -> R.string.product_enable_gestures
        !session.canToggleArm -> R.string.product_waiting_to_arm
        manualPause -> R.string.product_resume_controls
        else -> R.string.arm_control
    }
    val status = when {
        armed && session.actions == ProductActionState.PAUSED -> R.string.product_action_paused
        setupMissing -> R.string.product_setup_required
        home.running && visionError != null -> R.string.product_tracking_problem
        home.running && (!home.cameraReady || !home.visionReady) -> R.string.product_tracking_starting
        else -> session.actions.label()
    }
    val detail = when {
        setupMissing -> stringResource(R.string.product_setup_required_help)
        visionError != null -> visionError
        !home.running -> startError ?: stringResource(R.string.product_hero_stopped_help)
        !home.cameraReady || !home.visionReady -> stringResource(R.string.product_camera_starting)
        home.foregroundSafety == ForegroundSafety.PROTECTED -> stringResource(R.string.product_protected_help)
        home.foregroundSafety == ForegroundSafety.UNKNOWN -> stringResource(R.string.product_foreground_wait)
        noActions -> stringResource(R.string.product_no_action_channels)
        manualPause -> stringResource(R.string.product_resume_help)
        armed -> stringResource(R.string.refined_disarm_caption)
        else -> stringResource(R.string.product_hero_ready_help)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("control-dashboard"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "command-cockpit") {
            ControlCommandCenter(home.running, session.actions, status, detail, primaryLabel,
                enabled = primaryLabel != R.string.product_waiting_to_arm,
                onPrimary = {
                    when (primaryLabel) {
                        R.string.product_complete_setup -> onNavigate(AergisDestination.PERMISSIONS)
                        R.string.start_capture -> onStart()
                        R.string.stop_capture -> onStop()
                        R.string.product_enable_gestures -> onGestures(true)
                        R.string.disarm_control -> onArm(false)
                        else -> onArm(true)
                    }
                }, onStop = onStop)
        }
        item(key = "readiness") {
            ControlReadinessStrip(home)
        }
        item(key = "tracking-shortcut") {
            val handStatus = when {
                !home.running -> R.string.product_camera_off
                visionError != null -> R.string.product_tracking_problem
                !home.cameraReady || !home.visionReady -> R.string.product_camera_starting
                home.handsDetected == 0 -> R.string.product_no_hand
                else -> R.string.product_hand_visible
            }
            ProductLink(AergisDestination.LIVE, stringResource(handStatus, home.handedness), onNavigate)
        }
        item(key = "input-heading") {
            AergisSection(stringResource(R.string.product_control_methods),
                stringResource(R.string.ui_gestures_screen_description))
        }
        item(key = "input-tiles") { ProductInputTiles(assignedCount, onNavigate) }
        item(key = "practice-heading") { AergisSection(stringResource(R.string.product_practice_tools)) }
        item(key = "practice") { ProductLink(AergisDestination.PRACTICE, stringResource(R.string.product_practice_help), onNavigate) }
        item(key = "learn") { ProductLink(AergisDestination.LEARN, stringResource(R.string.product_learn_help), onNavigate) }
        item(key = "calibrate") { ProductLink(AergisDestination.CALIBRATION, stringResource(R.string.product_calibration_optional), onNavigate) }
        item(key = "precision") { ProductLink(AergisDestination.PRECISION, stringResource(R.string.product_precision_help), onNavigate) }
    }
}

/** Read-only state. Pointer availability is intentionally not used as a gesture-action gate. */
@Composable
private fun ControlReadinessStrip(home: HomeUiState) {
    val cameraGood = home.running && home.cameraReady
    val modelGood = cameraGood && home.visionReady && home.visionError == null
    val handGood = modelGood && home.handsDetected > 0
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("control-signal-strip"),
        shape = AergisShapes.Card, color = AergisColors.Surface,
        border = BorderStroke(1.dp, AergisColors.Outline)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProductEyebrow(stringResource(R.string.ui_system_overview), AergisColors.Silver)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadinessSignal(stringResource(R.string.ui_camera),
                    stringResource(if (cameraGood) R.string.ui_capture_on else R.string.ui_capture_off), cameraGood)
                ReadinessSignal(stringResource(R.string.ui_vision),
                    stringResource(if (modelGood) R.string.status_ready else R.string.status_not_ready), modelGood)
                ReadinessSignal(stringResource(R.string.ui_hand),
                    stringResource(if (handGood) R.string.status_ready else R.string.ui_no_hand), handGood)
            }
        }
    }
}

@Composable
private fun RowScope.ReadinessSignal(label: String, value: String, ready: Boolean) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(shape = AergisShapes.Chip,
            color = if (ready) AergisColors.Success else AergisColors.Caution) {
            Spacer(Modifier.size(7.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = AergisColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AergisColors.Text)
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
