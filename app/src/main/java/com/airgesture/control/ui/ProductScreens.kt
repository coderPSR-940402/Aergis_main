package com.airgesture.control.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.airgesture.control.*
import com.airgesture.control.R

@Composable
internal fun ProductHeading(title: Int, eyebrow: Int? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(28.dp).height(3.dp).background(AergisColors.Yellow))
            eyebrow?.let { ProductEyebrow(stringResource(it), AergisColors.Silver) }
        }
        Text(stringResource(title), style = MaterialTheme.typography.displaySmall,
            color = AergisColors.Text, modifier = Modifier.semantics { heading() })
        HorizontalDivider(color = AergisColors.Outline.copy(alpha = .7f))
    }
}

@Composable
internal fun ProductPage(title: Int, eyebrow: Int? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProductHeading(title, eyebrow)
        content()
    }
}

@Composable
internal fun ProductLink(destination: AergisDestination, description: String,
    onNavigate: (AergisDestination) -> Unit) {
    Surface(
        onClick = { onNavigate(destination) }, color = AergisColors.Deep,
        shape = AergisShapes.Card, modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, AergisColors.Outline)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 78.dp).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(AergisColors.StageShadow, AergisShapes.Control),
                contentAlignment = Alignment.Center) {
                AergisIcon(destination.glyph, Modifier.size(25.dp), AergisColors.Yellow)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(destination.label), style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = AergisColors.Muted)
            }
            Box(Modifier.size(30.dp).background(AergisColors.Raised, AergisShapes.Chip),
                contentAlignment = Alignment.Center) {
                AergisIcon(AergisGlyph.ARROW, Modifier.size(17.dp), AergisColors.Silver)
            }
        }
    }
}

@Composable
internal fun WelcomeScreen(cameraAllowed: Boolean, accessibilityEnabled: Boolean,
    onCamera: () -> Unit, onAccessibility: () -> Unit, onAppDetails: () -> Unit,
    handSelector: @Composable () -> Unit, onNavigate: (AergisDestination) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    ProductPage(R.string.product_welcome) {
        ProductEyebrow(stringResource(R.string.product_setup_progress, step + 1, 3))
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth(), color = AergisColors.Purple)
        when (step) {
            0 -> {
                Surface(shape = AergisShapes.Stage, color = AergisColors.Stage) {
                    Box(Modifier.fillMaxWidth().height(146.dp), contentAlignment = Alignment.Center) {
                        CommandField(Modifier.matchParentSize())
                        HandDiagram(RegisteredGesture.OPEN_PALM, Modifier.size(124.dp), AergisColors.OnStage, AergisColors.Yellow)
                    }
                }
                Text(stringResource(R.string.product_welcome_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.product_welcome_help), color = AergisColors.Muted)
                Text(stringResource(R.string.product_local_setup_note), color = AergisColors.Purple)
                AergisButton({ step = 1 }, Modifier.fillMaxWidth(), primary = true) { Text(stringResource(R.string.product_setup_start)) }
                TextButton({ onNavigate(AergisDestination.CONTROL) }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.product_explore))
                }
            }
            1 -> {
                AergisSection(stringResource(R.string.product_setup_permissions), stringResource(R.string.product_setup_permissions_help))
                PermissionsPanel(cameraAllowed, accessibilityEnabled, onCamera, onAccessibility, onAppDetails)
                AergisButton({ step = 2 }, Modifier.fillMaxWidth(), enabled = cameraAllowed && accessibilityEnabled, primary = true) {
                    Text(stringResource(R.string.product_continue))
                }
                TextButton({ step-- }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.product_back)) }
            }
            else -> {
                AergisSection(stringResource(R.string.product_setup_hand), stringResource(R.string.product_setup_hand_help))
                AergisPanel { handSelector() }
                AergisButton({ onNavigate(AergisDestination.CONTROL) }, Modifier.fillMaxWidth(), primary = true) { Text(stringResource(R.string.product_go_control)) }
                ProductLink(AergisDestination.LEARN, stringResource(R.string.product_learn_help), onNavigate)
                TextButton({ step-- }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.product_back)) }
            }
        }
    }
}

@Composable
internal fun PermissionsPanel(cameraAllowed: Boolean, accessibilityEnabled: Boolean,
    onCamera: () -> Unit, onAccessibility: () -> Unit, onAppDetails: () -> Unit) {
    AergisPanel {
        AergisSection(stringResource(R.string.ui_camera), stringResource(R.string.product_camera_permission_help))
        AergisStatusChip(stringResource(if (cameraAllowed) R.string.status_enabled else R.string.status_not_enabled),
            if (cameraAllowed) AergisColors.Success else AergisColors.Caution)
        if (!cameraAllowed) {
            AergisButton(onCamera, Modifier.fillMaxWidth(), primary = true) { Text(stringResource(R.string.product_allow_camera)) }
            if (accessibilityEnabled) {
                TextButton(onAppDetails, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.app_settings)) }
            }
        }
    }
    AergisPanel {
        AergisSection(stringResource(R.string.product_accessibility_title), stringResource(R.string.product_access_permission_help))
        AergisStatusChip(stringResource(if (accessibilityEnabled) R.string.status_enabled else R.string.status_not_enabled),
            if (accessibilityEnabled) AergisColors.Success else AergisColors.Caution)
        AergisButton(onAccessibility, Modifier.fillMaxWidth(), primary = cameraAllowed && !accessibilityEnabled) {
            Text(stringResource(R.string.accessibility_settings))
        }
        if (!accessibilityEnabled) {
            Text(stringResource(R.string.product_accessibility_recovery), color = AergisColors.Muted,
                style = MaterialTheme.typography.bodySmall)
            TextButton(onAppDetails, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.app_settings)) }
        }
    }
}

@Composable
internal fun PrivacyPanel() {
    AergisPanel(accent = AergisColors.Purple.copy(alpha = .35f)) {
        Text(stringResource(R.string.product_on_device), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.product_privacy_help), style = MaterialTheme.typography.bodyMedium, color = AergisColors.Muted)
    }
}

@Composable
internal fun MotionScreen(entryGestures: List<RegisteredGesture>, onChoose: () -> Unit,
    onNavigate: (AergisDestination) -> Unit) {
    ProductPage(R.string.product_motion) {
        AergisSection(stringResource(R.string.product_motion_title))
        DirectionInstrument(Modifier.fillMaxWidth().height(230.dp))
        Text(stringResource(R.string.product_motion_direction_help), modifier = Modifier.align(Alignment.CenterHorizontally), color = AergisColors.Purple)
        AergisPanel {
            MotionInstructions()
            Text(stringResource(R.string.product_motion_no_sensors), color = AergisColors.Muted)
        }
        AergisPanel {
            Text(stringResource(R.string.product_motion_entry), style = MaterialTheme.typography.titleSmall)
            if (entryGestures.isEmpty()) Text(stringResource(R.string.product_motion_unassigned), color = AergisColors.Muted)
            else entryGestures.forEach { Text(stringResource(gestureLabelRes(it))) }
            AergisButton(onChoose, Modifier.fillMaxWidth(), primary = true) { Text(stringResource(R.string.product_motion_assign)) }
        }
        ProductLink(AergisDestination.MOTION_PRACTICE, stringResource(R.string.product_motion_try), onNavigate)
    }
}

@Composable
private fun MotionInstructions() {
    for ((title, help) in listOf(R.string.product_motion_step1 to R.string.product_motion_step1_help,
        R.string.product_motion_step2 to R.string.product_motion_step2_help,
        R.string.product_motion_step3 to R.string.product_motion_step3_help)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(help), color = AergisColors.Muted)
    }
}

@Composable
internal fun MotionWalkthrough(onNavigate: (AergisDestination) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(R.string.product_motion_step1, R.string.product_motion_step2, R.string.product_motion_step3)
    val descriptions = listOf(R.string.product_motion_step1_help, R.string.product_motion_step2_help, R.string.product_motion_step3_help)
    ProductPage(R.string.product_motion_practice) {
        RehearsalStatus()
        DirectionInstrument(Modifier.fillMaxWidth().height(230.dp))
        AergisSection(stringResource(titles[step]), stringResource(descriptions[step]))
        Text(stringResource(R.string.product_motion_walkthrough_note), color = AergisColors.Muted)
        AergisButton({ if (step < 2) step++ else onNavigate(AergisDestination.CONTROL) }, Modifier.fillMaxWidth(), primary = true) {
            Text(stringResource(if (step < 2) R.string.product_motion_next else R.string.product_motion_finish))
        }
    }
}

@Composable
internal fun LearnGesturesScreen(mapping: (RegisteredGesture) -> AirAction,
    onEdit: (RegisteredGesture) -> Unit, onNavigate: (AergisDestination) -> Unit,
    readiness: @Composable () -> Unit
) {
    var gestureName by rememberSaveable { mutableStateOf(RegisteredGesture.VICTORY.name) }
    val gesture = RegisteredGesture.entries.firstOrNull { it.name == gestureName } ?: RegisteredGesture.VICTORY
    val signalFlow = remember {
        AirRuntime.uiState.map { state ->
            RegisteredGesture.fromSignal(state.lastGesture)
                ?.takeIf { state.running && state.visionReady && state.handsDetected > 0 }
        }.distinctUntilChanged()
    }
    val signal by signalFlow.collectAsStateWithLifecycle(initialValue = null)
    ProductPage(R.string.product_learn) {
        RehearsalStatus()
        readiness()
        HandDiagram(gesture, Modifier.fillMaxWidth().height(140.dp))
        Text(stringResource(R.string.product_pose_index, gesture.ordinal + 1, RegisteredGesture.entries.size), color = AergisColors.Muted)
        AergisSection(stringResource(gestureLabelRes(gesture)), stringResource(gestureHelpRes(gesture)))
        AergisStatusChip(actionLabel(mapping(gesture)), AergisColors.Purple)
        AergisPanel {
            Text(stringResource(R.string.product_live_signal,
                signal?.let { stringResource(gestureLabelRes(it)) } ?: stringResource(R.string.product_live_signal_empty)),
                color = if (signal == gesture) AergisColors.Success else AergisColors.Muted)
            Text(stringResource(R.string.product_guide_help), color = AergisColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AergisButton({
                gestureName = RegisteredGesture.entries[(gesture.ordinal + RegisteredGesture.entries.size - 1) % RegisteredGesture.entries.size].name
            }, Modifier.weight(1f)) { Text(stringResource(R.string.product_previous)) }
            AergisButton({
                gestureName = RegisteredGesture.entries[(gesture.ordinal + 1) % RegisteredGesture.entries.size].name
            }, Modifier.weight(1f)) { Text(stringResource(R.string.product_next)) }
        }
        AergisButton({ onEdit(gesture) }, Modifier.fillMaxWidth(), primary = true) { Text(stringResource(R.string.product_assign_pose)) }
        ProductLink(AergisDestination.LIVE, stringResource(R.string.product_live_help), onNavigate)
    }
}

/** Four native corner indicators around the existing raw-pointer preview; no coordinate logic. */
@Composable
internal fun CalibrationStepPreview(sampleCount: Int, capturing: Boolean) {
    Box(Modifier.fillMaxWidth()) {
        CalibrationPreview(rawCoordinates = true)
        Column(Modifier.matchParentSize().padding(10.dp).clearAndSetSemantics {},
            verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CalibrationCorner(0, sampleCount, capturing)
                CalibrationCorner(1, sampleCount, capturing)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CalibrationCorner(3, sampleCount, capturing)
                CalibrationCorner(2, sampleCount, capturing)
            }
        }
    }
}

@Composable
private fun CalibrationCorner(index: Int, count: Int, capturing: Boolean) {
    val active = capturing && count == index
    val completed = capturing && count > index
    Surface(shape = AergisShapes.Chip, color = if (active) AergisColors.Purple else AergisColors.Raised) {
        Text(if (completed) "✓" else (index + 1).toString(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (active) AergisColors.OnStage else if (completed) AergisColors.Purple else AergisColors.Muted,
            style = MaterialTheme.typography.labelMedium)
    }
}

/** A notification can change control mode while a guide is visible; never show a stale safe badge. */
@Composable
internal fun RehearsalStatus() {
    val flow = remember { AirRuntime.uiState.map { it.controlMode }.distinctUntilChanged() }
    val mode by flow.collectAsStateWithLifecycle(initialValue = AirRuntime.controlMode)
    if (mode == ControlMode.READY) AergisStatusChip(stringResource(R.string.product_guide_badge), AergisColors.Purple)
    else AergisPanel(accent = AergisColors.Caution) {
        Text(stringResource(R.string.product_rehearsal_not_disarmed), color = AergisColors.Caution)
        AergisButton({ AirRuntime.controlMode = ControlMode.READY }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.disarm_control))
        }
    }
}
