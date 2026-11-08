package com.airgesture.control.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgesture.control.AirRuntime
import com.airgesture.control.ControlMode
import com.airgesture.control.ForegroundSafety
import com.airgesture.control.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
internal fun AergisShell(
    inputEnabled: Boolean = true,
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
    val back: () -> Unit = {
        val previous = navigation.back()
        onEntering(previous.current)
        routeNames = previous.routes.joinToString("|")
    }
    val screenState = rememberSaveableStateHolder()
    BackHandler(enabled = navigation.canGoBack || !selected.root) { back() }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        containerColor = AergisColors.Background,
        bottomBar = {
            if (selected != AergisDestination.WELCOME) NavigationBar(containerColor = AergisColors.Background, tonalElevation = 0.dp,
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
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AergisMark(Modifier.size(28.dp))
                    Text(stringResource(R.string.ui_brand), style = AergisType.Brand, modifier = Modifier.weight(1f))
                    if (!selected.root && selected != AergisDestination.WELCOME) {
                        TextButton(onClick = back, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.product_back))
                        }
                    }
                }
                if (selected != AergisDestination.CONTROL) {
                    ActiveSessionLabel(inputEnabled, onControl = { navigate(AergisDestination.CONTROL) })
                }
                HorizontalDivider(color = AergisColors.Outline.copy(alpha = .7f))
                // A route can keep its UI state without keeping camera or calibration effects alive.
                screenState.SaveableStateProvider(selected.name) {
                    Box(Modifier.weight(1f)) { content(selected, navigate) }
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionLabel(inputEnabled: Boolean, onControl: () -> Unit) {
    val flow = remember(inputEnabled) {
        AirRuntime.uiState.map { runtime ->
            when {
                !runtime.running -> null
                !inputEnabled -> R.string.product_action_disabled
                runtime.controlMode == ControlMode.PAUSED ||
                    (runtime.controlMode == ControlMode.ARMED &&
                        (runtime.foregroundSafety != ForegroundSafety.SAFE || !runtime.cameraReady ||
                            !runtime.visionReady || runtime.visionError != null)) -> R.string.product_action_paused
                runtime.controlMode == ControlMode.ARMED -> R.string.product_action_armed
                else -> R.string.product_action_disarmed
            }
        }.distinctUntilChanged()
    }
    val label by flow.collectAsStateWithLifecycle(initialValue = null)
    label?.let {
        TextButton(onControl, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.ui_session_summary, stringResource(R.string.ui_capture_on), stringResource(it)),
                color = if (it == R.string.product_action_armed) AergisColors.Yellow else AergisColors.Purple,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
