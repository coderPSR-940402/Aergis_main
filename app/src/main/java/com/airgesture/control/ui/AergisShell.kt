package com.airgesture.control.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
        modifier = Modifier.fillMaxSize().background(AergisColors.Background).safeDrawingPadding(),
        containerColor = AergisColors.Background,
        bottomBar = {
            if (selected != AergisDestination.WELCOME) {
                val activeRoot = if (selected.root) selected.name else navigation.routes.firstOrNull()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    NavigationDock(activeRoot, navigate)
                }
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(AergisColors.Deep, AergisColors.StageShadow, AergisColors.Deep)))
                        .padding(horizontal = 18.dp, vertical = 12.dp).heightIn(min = 60.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(42.dp).background(AergisColors.Yellow, AergisShapes.Control),
                        contentAlignment = Alignment.Center) {
                        AergisMark(Modifier.size(25.dp), AergisColors.Deep)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.ui_brand), style = AergisType.Brand, color = AergisColors.Text)
                        Text(stringResource(R.string.product_console_label), style = MaterialTheme.typography.labelSmall,
                            color = AergisColors.Muted)
                    }
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
private fun NavigationDock(activeRoot: String?, navigate: (AergisDestination) -> Unit) {
    Row(
        Modifier.widthIn(max = 520.dp).fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(AergisShapes.Card).background(AergisColors.Surface)
            .border(1.dp, AergisColors.Outline, AergisShapes.Card)
            .selectableGroup().padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (destination in AergisDestination.entries.filter { it.root }) {
            val selected = activeRoot == destination.name
            val ink = if (selected) AergisColors.Deep else AergisColors.Silver
            Column(
                Modifier.weight(1f).heightIn(min = 62.dp).clip(AergisShapes.Control)
                    .background(if (selected) AergisColors.Yellow else AergisColors.Surface)
                    .testTag("nav-" + destination.name)
                    .selectable(selected, role = Role.Tab, onClick = { navigate(destination) })
                    .padding(horizontal = 4.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AergisIcon(destination.glyph, Modifier.size(22.dp), ink)
                Spacer(Modifier.height(5.dp))
                Text(stringResource(destination.label), color = ink,
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
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
                color = if (it == R.string.product_action_armed) AergisColors.Success else AergisColors.Purple,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
