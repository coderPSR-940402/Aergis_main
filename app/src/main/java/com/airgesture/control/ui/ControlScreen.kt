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
    accessibilityEnabled: Boolean, pointerClickingEnabled: Boolean,
    gesturesEnabled: Boolean, assignedCount: Int, startError: String?, onStart: () -> Unit, onStop: () -> Unit,
    onArm: (Boolean) -> Unit, onGestures: (Boolean) -> Unit, onNavigate: (AergisDestination) -> Unit,
    cameraAllowed: Boolean = true
) {
    // Project only visible state so per-frame telemetry cannot invalidate this screen.
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ProductHeading(R.string.product_control, R.string.product_control_kicker)
        }
        item {
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
        item {
            AergisPanel {
                AergisSection(stringResource(R.string.product_feedback_title))
                val handStatus = when {
                    !home.running -> R.string.product_camera_off
                    visionError != null -> R.string.product_tracking_problem
                    !home.cameraReady || !home.visionReady -> R.string.product_camera_starting
                    home.handsDetected == 0 -> R.string.product_no_hand
                    else -> R.string.product_hand_visible
                }
                AergisStatusChip(stringResource(handStatus, home.handedness),
                    if (home.running && home.cameraReady && home.visionReady && home.handsDetected > 0) AergisColors.Success else AergisColors.Muted)
                ProductLink(AergisDestination.LIVE, stringResource(R.string.product_live_help), onNavigate)
            }
        }
        item { AergisSection(stringResource(R.string.product_control_methods)) }
        item { ProductInputTiles(assignedCount, onNavigate) }
        item {
            AergisPanel {
                AergisSection(stringResource(R.string.product_practice_tools))
                ProductLink(AergisDestination.LEARN, stringResource(R.string.product_learn_help), onNavigate)
                ProductLink(AergisDestination.PRACTICE, stringResource(R.string.product_practice_help), onNavigate)
                ProductLink(AergisDestination.CALIBRATION, stringResource(R.string.product_calibration_optional), onNavigate)
                ProductLink(AergisDestination.PRECISION, stringResource(R.string.product_precision_help), onNavigate)
            }
        }
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
