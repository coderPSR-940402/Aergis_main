package com.airgesture.control.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgesture.control.AirRuntime
import com.airgesture.control.ControlMode
import com.airgesture.control.ForegroundSafety
import com.airgesture.control.R
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

internal enum class AergisDestination(val label: Int, val glyph: AergisGlyph) {
    CONTROL(R.string.ui_control, AergisGlyph.CONTROL),
    TRACKING(R.string.ui_tracking, AergisGlyph.TRACKING),
    GESTURES(R.string.ui_gestures, AergisGlyph.GESTURES),
    SETTINGS(R.string.ui_settings, AergisGlyph.SETTINGS)
}

@Composable
internal fun AergisShell(gesturesEnabled: Boolean, content: @Composable (AergisDestination, (AergisDestination) -> Unit) -> Unit) {
    var selectedName by rememberSaveable { mutableStateOf(AergisDestination.CONTROL.name) }
    val selected = AergisDestination.valueOf(selectedName)
    val navigate: (AergisDestination) -> Unit = { selectedName = it.name }
    BackHandler(enabled = selected != AergisDestination.CONTROL) { navigate(AergisDestination.CONTROL) }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        containerColor = AergisColors.Background,
        bottomBar = {
            NavigationBar(containerColor = AergisColors.Background, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                AergisDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selected, onClick = { navigate(destination) },
                        icon = { AergisIcon(destination.glyph, Modifier.size(24.dp),
                            if (destination == selected) AergisColors.Cyan else AergisColors.Muted) },
                        label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = AergisColors.Cyan, unselectedTextColor = AergisColors.Muted,
                            indicatorColor = AergisColors.Raised
                        )
                    )
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AergisMark(Modifier.size(36.dp))
                Column {
                    Text(stringResource(R.string.ui_brand), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.ui_brand_subtitle), color = AergisColors.Muted,
                        style = MaterialTheme.typography.labelMedium)
                    SessionLabel(gesturesEnabled)
                }
            }
            HorizontalDivider(color = AergisColors.Outline.copy(alpha = .5f))
            Crossfade(targetState = selected, modifier = Modifier.weight(1f),
                animationSpec = tween(AergisMotion.NavigationMs, easing = AergisMotion.Easing), label = "destination") {
                content(it, navigate)
            }
        }
    }
}

@Composable
private fun SessionLabel(gesturesEnabled: Boolean) {
    val flow = remember {
        AirRuntime.uiState.map {
            Triple(it.running, it.controlMode,
                it.motionActive || (it.controlMode == ControlMode.ARMED && it.foregroundSafety != ForegroundSafety.SAFE))
        }.distinctUntilChanged()
    }
    val initial = AirRuntime.uiStateSnapshot()
    val state by flow.collectAsStateWithLifecycle(initialValue = Triple(initial.running, initial.controlMode,
        initial.motionActive || (initial.controlMode == ControlMode.ARMED && initial.foregroundSafety != ForegroundSafety.SAFE)))
    val capture = stringResource(if (state.first) R.string.ui_capture_on else R.string.ui_capture_off)
    val actions = stringResource(when {
        !gesturesEnabled -> R.string.ui_actions_disabled
        state.third || state.second == ControlMode.PAUSED -> R.string.ui_actions_paused
        state.second == ControlMode.ARMED -> R.string.ui_actions_armed
        else -> R.string.ui_actions_disarmed
    })
    Text(stringResource(R.string.ui_session_summary, capture, actions), color = AergisColors.Muted,
        style = MaterialTheme.typography.bodySmall)
}
