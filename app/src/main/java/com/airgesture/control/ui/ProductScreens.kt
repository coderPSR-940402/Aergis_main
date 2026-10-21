package com.airgesture.control.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        eyebrow?.let { Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = AergisColors.Muted) }
        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    }
}

@Composable
internal fun ProductPage(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProductHeading(title)
        content()
    }
}

@Composable
internal fun ProductLink(destination: AergisDestination, description: String,
    onNavigate: (AergisDestination) -> Unit) {
    Surface(onClick = { onNavigate(destination) }, color = androidx.compose.ui.graphics.Color.Transparent,
        shape = AergisShapes.Control, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AergisIcon(destination.glyph, Modifier.size(22.dp), AergisColors.Purple)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(destination.label), style = MaterialTheme.typography.titleSmall)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = AergisColors.Muted)
                }
                AergisIcon(AergisGlyph.ARROW, Modifier.size(18.dp), AergisColors.Muted)
            }
            HorizontalDivider(color = AergisColors.Outline.copy(alpha = .5f))
        }
    }
}

@Composable
internal fun WelcomeScreen(cameraAllowed: Boolean, accessibilityEnabled: Boolean,
    onCamera: () -> Unit, onAccessibility: () -> Unit, onAppDetails: () -> Unit,
    handSelector: @Composable () -> Unit, onNavigate: (AergisDestination) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    ProductPage(R.string.product_welcome) {
        LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth(), color = AergisColors.Purple)
        when (step) {
            0 -> {
                GestureIllustration(RegisteredGesture.OPEN_PALM)
                Text(stringResource(R.string.product_welcome_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.product_welcome_help), color = AergisColors.Muted)
                PrivacyPanel()
                AergisButton({ step = 1 }, Modifier.fillMaxWidth(), primary = true) { Text(stringResource(R.string.product_setup_start)) }
                TextButton({ onNavigate(AergisDestination.CONTROL) }) { Text(stringResource(R.string.product_explore)) }
            }
            1 -> {
                AergisSection(stringResource(R.string.product_setup_permissions), stringResource(R.string.product_setup_permissions_help))
                PermissionsPanel(cameraAllowed, accessibilityEnabled, onCamera, onAccessibility, onAppDetails)
                AergisButton({ step = 2 }, Modifier.fillMaxWidth(), enabled = cameraAllowed && accessibilityEnabled, primary = true) {
                    Text(stringResource(R.string.product_continue))
                }
                TextButton({ step = 0 }) { Text(stringResource(R.string.product_back)) }
            }
            else -> {
                AergisSection(stringResource(R.string.product_setup_hand), stringResource(R.string.product_setup_hand_help))
                AergisPanel { handSelector() }
                AergisButton({ onNavigate(AergisDestination.CONTROL) }, Modifier.fillMaxWidth(), primary = true) { Text(stringResource(R.string.product_go_control)) }
                ProductLink(AergisDestination.LEARN, stringResource(R.string.product_learn_help), onNavigate)
            }
        }
    }
}

@Composable
internal fun PermissionsPanel(cameraAllowed: Boolean, accessibilityEnabled: Boolean,
    onCamera: () -> Unit, onAccessibility: () -> Unit, onAppDetails: () -> Unit) {
    AergisPanel {
        Text(stringResource(R.string.ui_camera), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.product_camera_permission_help), color = AergisColors.Muted)
        AergisStatusChip(stringResource(if (cameraAllowed) R.string.status_enabled else R.string.status_not_enabled))
        AergisButton(onCamera, Modifier.fillMaxWidth(), enabled = !cameraAllowed) { Text(stringResource(R.string.product_allow_camera)) }
        HorizontalDivider(color = AergisColors.Outline)
        Text(stringResource(R.string.ui_permissions_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.product_access_permission_help), color = AergisColors.Muted)
        AergisStatusChip(stringResource(if (accessibilityEnabled) R.string.status_enabled else R.string.status_not_enabled))
        AergisButton(onAccessibility, Modifier.fillMaxWidth()) { Text(stringResource(R.string.accessibility_settings)) }
        AergisButton(onAppDetails, Modifier.fillMaxWidth()) { Text(stringResource(R.string.app_settings)) }
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
    onEdit: (RegisteredGesture) -> Unit, onNavigate: (AergisDestination) -> Unit) {
    var gestureName by rememberSaveable { mutableStateOf(RegisteredGesture.VICTORY.name) }
    val gesture = RegisteredGesture.entries.firstOrNull { it.name == gestureName } ?: RegisteredGesture.VICTORY
    ProductPage(R.string.product_learn) {
        RehearsalStatus()
        GestureIllustration(gesture)
        Text(stringResource(R.string.product_pose_index, gesture.ordinal + 1, RegisteredGesture.entries.size), color = AergisColors.Muted)
        AergisSection(stringResource(gestureLabelRes(gesture)), stringResource(gestureHelpRes(gesture)))
        AergisStatusChip(actionLabel(mapping(gesture)), AergisColors.Yellow)
        Text(stringResource(R.string.product_guide_help), color = AergisColors.Muted)
        // Stacked controls remain usable at large font scales and narrow widths.
        AergisButton({ gestureName = RegisteredGesture.entries[(gesture.ordinal + RegisteredGesture.entries.size - 1) % RegisteredGesture.entries.size].name }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.product_previous))
        }
        AergisButton({ gestureName = RegisteredGesture.entries[(gesture.ordinal + 1) % RegisteredGesture.entries.size].name }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.product_next))
        }
        AergisButton({ onEdit(gesture) }, Modifier.fillMaxWidth(), primary = true) { Text(stringResource(R.string.product_assign_pose)) }
        ProductLink(AergisDestination.LIVE, stringResource(R.string.product_live_help), onNavigate)
    }
}

/** Decorative pose artwork; written pose instructions remain the authoritative guide. */
@Composable
private fun GestureIllustration(gesture: RegisteredGesture) {
    SculptedHand(gesture, Modifier.fillMaxWidth().height(210.dp))
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
    Surface(shape = AergisShapes.Chip, color = if (active) AergisColors.Yellow else AergisColors.Raised) {
        Text(if (completed) "✓" else (index + 1).toString(), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (active) AergisColors.Background else if (completed) AergisColors.Yellow else AergisColors.Muted,
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
