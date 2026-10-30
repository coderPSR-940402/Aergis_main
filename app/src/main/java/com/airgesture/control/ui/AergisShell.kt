package com.airgesture.control.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgesture.control.*
import com.airgesture.control.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun AergisShell(
    gesturesEnabled: Boolean,
    initialDestination: AergisDestination = AergisDestination.CONTROL,
    onEntering: (AergisDestination) -> Unit = {},
    requestedDestination: AergisDestination? = null,
    onDestinationHandled: () -> Unit = {},
    content: @Composable (AergisDestination, (AergisDestination) -> Unit) -> Unit
) {
    // Keep a String saveable, compatible with the previous shell's selectedName state.
    var routeNames by rememberSaveable { mutableStateOf(initialDestination.name) }
    val navigation = ProductNavigation(routeNames.split('|'))
    val selected = navigation.current
    LaunchedEffect(Unit) { onEntering(selected) }
    val navigate: (AergisDestination) -> Unit = { destination ->
        if (destination != selected) {
            onEntering(destination)
            routeNames = navigation.open(destination).routes.joinToString("|")
        }
    }
    LaunchedEffect(requestedDestination) {
        requestedDestination?.let { destination ->
            onEntering(destination)
            routeNames = navigation.open(destination).routes.joinToString("|")
            onDestinationHandled()
        }
    }
    val back: () -> Unit = { routeNames = navigation.back().routes.joinToString("|") }
    BackHandler(enabled = navigation.canGoBack || !selected.root) { back() }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        containerColor = AergisColors.Background,
        bottomBar = {
            NavigationBar(containerColor = AergisColors.Background, tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0)) {
                AergisDestination.entries.filter { it.root }.forEach { destination ->
                    val isSelected = destination == selected || (navigation.routes.firstOrNull() == destination.name && !selected.root)
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { navigate(destination) },
                        icon = { AergisIcon(destination.glyph, Modifier.size(22.dp), if (isSelected) AergisColors.Purple else AergisColors.Muted) },
                        label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(selectedTextColor = AergisColors.Purple,
                            unselectedTextColor = AergisColors.Muted, indicatorColor = AergisColors.Raised.copy(alpha = .45f))
                    )
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AergisMark(Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_brand), style = AergisType.Brand)
                    Text(stringResource(R.string.product_touchless), color = AergisColors.Muted,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            SessionLabel(gesturesEnabled)
            // ActionFeedbackOverlay owns transient outcomes outside this activity's
            // scanned tree: changing content here invalidates selection identities.
            if (!selected.root && selected != AergisDestination.WELCOME) {
                TextButton(onClick = back, modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.product_back))
                }
            }
            // No outgoing screen is kept alive during navigation (important for calibration cleanup).
            key(selected) { Box(Modifier.weight(1f)) { content(selected, navigate) } }
        }
    }
}

@Composable
private fun SessionLabel(inputEnabled: Boolean) {
    val flow = remember { AirRuntime.uiState.map {
        Triple(it.running, it.controlMode, it.controlMode == ControlMode.ARMED && it.foregroundSafety != ForegroundSafety.SAFE)
    }.distinctUntilChanged() }
    val initial = AirRuntime.uiStateSnapshot()
    val state by flow.collectAsStateWithLifecycle(initialValue = Triple(initial.running, initial.controlMode,
        initial.controlMode == ControlMode.ARMED && initial.foregroundSafety != ForegroundSafety.SAFE))
    val capture = stringResource(if (state.first) R.string.ui_capture_on else R.string.ui_capture_off)
    val actions = stringResource(when {
        !inputEnabled -> R.string.ui_actions_disabled
        state.third || state.second == ControlMode.PAUSED -> R.string.ui_actions_paused
        state.second == ControlMode.ARMED -> R.string.ui_actions_armed
        else -> R.string.ui_actions_disarmed
    })
    Surface(color = AergisColors.Background) {
        Text(stringResource(R.string.ui_session_summary, capture, actions), color = AergisColors.Muted,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
    }
}
