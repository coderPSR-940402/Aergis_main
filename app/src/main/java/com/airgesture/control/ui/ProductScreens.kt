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
    OutlinedButton(onClick = { onNavigate(destination) }, shape = AergisShapes.Control,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        contentPadding = PaddingValues(16.dp)) {
        AergisIcon(destination.glyph, Modifier.size(24.dp), AergisColors.Purple)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(destination.label), style = MaterialTheme.typography.titleSmall, color = AergisColors.Text)
            Text(description, style = MaterialTheme.typography.bodySmall, color = AergisColors.Muted)
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
        AergisIcon(AergisGlyph.MOTION, Modifier.align(Alignment.CenterHorizontally).size(90.dp), AergisColors.Purple)
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
        AergisIcon(AergisGlyph.MOTION, Modifier.align(Alignment.CenterHorizontally).size(120.dp), AergisColors.Purple)
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

/** Decorative pose guide. Labels and the existing pose instructions carry the semantics. */
@Composable
private fun GestureIllustration(gesture: RegisteredGesture) {
    val extended = when (gesture) {
        RegisteredGesture.VICTORY -> setOf(0, 1)
        RegisteredGesture.CLOSED_FIST, RegisteredGesture.THUMB_UP, RegisteredGesture.THUMB_DOWN -> emptySet()
        RegisteredGesture.POINTING_UP, RegisteredGesture.L_SHAPE -> setOf(0)
        RegisteredGesture.PINKY_ONLY, RegisteredGesture.SHAKA -> setOf(3)
        RegisteredGesture.I_LOVE_YOU, RegisteredGesture.HORNS -> setOf(0, 3)
        RegisteredGesture.THREE_FINGERS -> setOf(0, 1, 2)
        RegisteredGesture.OK, RegisteredGesture.THREE_OUTER_FINGERS, RegisteredGesture.FOUR_INDEX_FOLDED -> setOf(1, 2, 3)
        RegisteredGesture.THUMB_INDEX_MIDDLE, RegisteredGesture.DOUBLE_GUN_LEFT, RegisteredGesture.DOUBLE_GUN_RIGHT,
        RegisteredGesture.DOUBLE_GUN_UP, RegisteredGesture.DOUBLE_GUN_DOWN -> setOf(0, 1)
        RegisteredGesture.FOUR_LITTLE_FOLDED -> setOf(0, 1, 2)
        else -> setOf(0, 1, 2, 3)
    }
    Canvas(Modifier.fillMaxWidth().height(160.dp).clearAndSetSemantics {}) {
        val unit = size.height / 120f
        val left = size.width / 2f - unit * 30f
        fun p(x: Float, y: Float) = Offset(left + x * unit, y * unit)
        val color = AergisColors.Purple
        val palm = Path().apply {
            moveTo(p(5f, 62f).x, p(5f, 62f).y)
            lineTo(p(5f, 82f).x, p(5f, 82f).y)
            cubicTo(p(5f, 112f).x, p(5f, 112f).y, p(60f, 112f).x, p(60f, 112f).y, p(60f, 82f).x, p(60f, 82f).y)
            lineTo(p(60f, 62f).x, p(60f, 62f).y)
        }
        val rotation = when (gesture) {
            RegisteredGesture.DOUBLE_GUN_LEFT -> -90f
            RegisteredGesture.DOUBLE_GUN_RIGHT -> 90f
            RegisteredGesture.DOUBLE_GUN_DOWN -> 180f
            else -> 0f
        }
        rotate(rotation, p(30f, 65f)) {
        drawPath(palm, color.copy(alpha = .12f))
        drawPath(palm, color, style = Stroke(2f * unit))
        for (finger in 0..3) {
            val x = 13f + finger * 13f
            val y = if (finger in extended) listOf(29f, 15f, 24f, 42f)[finger] else 55f
            drawLine(color, p(x, 67f), p(x, y), unit * 8, StrokeCap.Round)
        }
        val thumb = when (gesture) {
            RegisteredGesture.THUMB_UP -> p(-3f, 25f)
            RegisteredGesture.THUMB_DOWN -> p(-3f, 112f)
            RegisteredGesture.OPEN_PALM, RegisteredGesture.I_LOVE_YOU, RegisteredGesture.SHAKA,
            RegisteredGesture.L_SHAPE, RegisteredGesture.THUMB_INDEX_MIDDLE,
            RegisteredGesture.FOUR_LITTLE_FOLDED, RegisteredGesture.FOUR_INDEX_FOLDED,
            RegisteredGesture.DOUBLE_GUN_LEFT, RegisteredGesture.DOUBLE_GUN_RIGHT,
            RegisteredGesture.DOUBLE_GUN_UP, RegisteredGesture.DOUBLE_GUN_DOWN -> p(-10f, 58f)
            else -> p(30f, 75f)
        }
        drawLine(color, p(8f, 83f), thumb, unit * 8, StrokeCap.Round)
        drawCircle(AergisColors.Yellow, 3f * unit, p(31f, 84f))
        }
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
