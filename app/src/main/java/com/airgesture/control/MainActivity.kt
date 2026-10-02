package com.airgesture.control

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private var pointerEnabled by mutableStateOf(true)
    private var gesturesEnabled by mutableStateOf(true)
    private var handPreference by mutableStateOf(ControlHandPreference.EITHER)
    private var sessionStartError by mutableStateOf<String?>(null)
    private var accessibilityEnabled by mutableStateOf(false)
    private var mappingsVersion by mutableIntStateOf(0)

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            refreshSettings()
            startSession()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshSettings()
        setContent { AirGestureScreen() }
    }

    override fun onResume() {
        super.onResume()
        refreshSettings()
    }

    private fun refreshSettings() {
        val mappings = ActionMappingStore(this)
        pointerEnabled = mappings.pointerEnabled()
        gesturesEnabled = mappings.gesturesEnabled()
        handPreference = mappings.handPreference()
        AirRuntime.handPreference = handPreference
        accessibilityEnabled = AirAccessibilityService.enabled()
    }

    private fun startSession() {
        val cameraPermissionGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!cameraPermissionGranted) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val decision = SessionStartPolicy.evaluate(
            cameraPermissionGranted = true,
            accessibilityEnabled = AirAccessibilityService.enabled()
        )
        if (!decision.allowed) {
            sessionStartError = decision.reason
            refreshSettings()
            return
        }
        sessionStartError = null
        ContextCompat.startForegroundService(this, Intent(this, GestureCaptureService::class.java))
    }

    private fun stopSession() {
        stopService(Intent(this, GestureCaptureService::class.java))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun cycleMapping(source: AirAction) {
        val options = AirAction.entries.filter { it != AirAction.NONE }
        val store = ActionMappingStore(this)
        val current = store.mapping(source)
        val index = options.indexOf(current).coerceAtLeast(0)
        store.setMapping(source, options[(index + 1) % options.size])
        mappingsVersion++
    }

    private fun mapping(source: AirAction, version: Int): AirAction {
        return ActionMappingStore(this).mapping(source)
    }

    @Composable
    private fun AirGestureScreen() {
        val runtime by AirRuntime.uiState.collectAsStateWithLifecycle(
            initialValue = AirRuntime.uiStateSnapshot()
        )

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                            Text(stringResource(R.string.app_tagline))
                        }
                    }
                    item {
                        StatusCard(
                            title = stringResource(R.string.session_status_title),
                            message = stringResource(
                                if (runtime.running) R.string.session_active else R.string.session_stopped
                            )
                        )
                    }
                    item {
                        StatusCard(
                            title = stringResource(
                                R.string.control_mode_title,
                                when (runtime.controlMode) {
                                    ControlMode.OFF -> stringResource(R.string.session_stopped)
                                    ControlMode.READY -> stringResource(R.string.control_mode_ready)
                                    ControlMode.ARMED -> stringResource(R.string.control_mode_armed)
                                    ControlMode.PAUSED -> stringResource(R.string.control_mode_paused)
                                }
                            ),
                            message = stringResource(R.string.control_mode_description),
                            isError = runtime.motionActive || runtime.foregroundSafety == ForegroundSafety.PROTECTED
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pointer_mode))
                                Text(stringResource(R.string.pointer_mode_description))
                            }
                            Switch(checked = pointerEnabled, onCheckedChange = {
                                pointerEnabled = it
                                ActionMappingStore(this@MainActivity).setPointerEnabled(it)
                                AirRuntime.pointerEnabled = it
                                AirAccessibilityService.instance?.updatePointer(
                                    AirRuntime.pointerX,
                                    AirRuntime.pointerY,
                                    it && AirRuntime.pointerTracking
                                )
                            })
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.gesture_actions))
                                Text(stringResource(R.string.gesture_actions_description))
                            }
                            Switch(checked = gesturesEnabled, onCheckedChange = {
                                gesturesEnabled = it
                                ActionMappingStore(this@MainActivity).setGesturesEnabled(it)
                                AirRuntime.gesturesEnabled = it
                            })
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.tracking_hand_preference))
                            ControlHandSelector(
                                selected = handPreference,
                                onSelected = { selected ->
                                    handPreference = selected
                                    ActionMappingStore(this@MainActivity).setHandPreference(selected)
                                    AirRuntime.handPreference = selected
                                }
                            )
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(
                                    R.string.accessibility_status,
                                    stringResource(
                                        if (accessibilityEnabled) R.string.status_enabled else R.string.status_not_enabled
                                    )
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.vision_status,
                                    stringResource(
                                        if (runtime.visionReady) R.string.status_ready else R.string.status_not_ready
                                    )
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.camera_status,
                                    stringResource(
                                        if (runtime.cameraReady) R.string.status_ready else R.string.status_not_active
                                    )
                                )
                            )
                        }
                    }
                    item { Text(stringResource(R.string.hands_detected, runtime.handsDetected)) }
                    item { Text(stringResource(R.string.tracking_hand, runtime.handedness)) }
                    item { Text(stringResource(R.string.gesture, runtime.lastGesture)) }
                    item {
                        Text(
                            stringResource(
                                R.string.pointer_tracking,
                                stringResource(
                                    if (runtime.pointerTracking) {
                                        R.string.pointer_tracking_active
                                    } else {
                                        R.string.pointer_tracking_inactive
                                    }
                                )
                            )
                        )
                    }
                    item {
                        Text(
                            stringResource(
                                R.string.motion_status,
                                stringResource(
                                    when {
                                        !runtime.motionSensorsAvailable -> R.string.motion_unavailable
                                        runtime.motionActive -> R.string.motion_active
                                        else -> R.string.motion_inactive
                                    }
                                )
                            )
                        )
                        Text(
                            stringResource(
                                R.string.foreground_status,
                                stringResource(
                                    when (runtime.foregroundSafety) {
                                        ForegroundSafety.SAFE -> R.string.foreground_safe
                                        ForegroundSafety.PROTECTED -> R.string.foreground_protected
                                        ForegroundSafety.UNKNOWN -> R.string.foreground_unknown
                                    }
                                )
                            )
                        )
                    }
                    runtime.visionError?.let { error ->
                        item {
                            StatusCard(
                                title = stringResource(R.string.vision_error_title),
                                message = error,
                                isError = true
                            )
                        }
                    }
                    sessionStartError?.let { error ->
                        item {
                            StatusCard(
                                title = stringResource(R.string.session_start_error_title),
                                message = error,
                                isError = true
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.gesture_mappings),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item { MappingRow(R.string.mapping_thumb_up, AirAction.TAP) }
                    item { MappingRow(R.string.mapping_victory, AirAction.BACK) }
                    item { MappingRow(R.string.mapping_open_palm, AirAction.HOME) }
                    item { MappingRow(R.string.mapping_fist, AirAction.RECENTS) }
                    item { MappingRow(R.string.mapping_pointing_up, AirAction.DOUBLE_TAP) }
                    item {
                        Button(
                            onClick = { if (runtime.running) stopSession() else startSession() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(if (runtime.running) R.string.stop_capture else R.string.start_capture))
                        }
                    }
                    item {
                        Button(
                            enabled = runtime.running &&
                                (runtime.controlMode == ControlMode.ARMED || !runtime.motionActive),
                            onClick = {
                                AirRuntime.controlMode = if (runtime.controlMode == ControlMode.ARMED) {
                                    ControlMode.READY
                                } else {
                                    ControlMode.ARMED
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(
                                    if (runtime.controlMode == ControlMode.ARMED) {
                                        R.string.disarm_control
                                    } else {
                                        R.string.arm_control
                                    }
                                )
                            )
                        }
                    }
                    item {
                        Button(onClick = { openAccessibilitySettings() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.accessibility_settings))
                        }
                    }
                    item {
                        Button(onClick = { openAppDetails() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.app_settings))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MappingRow(labelRes: Int, source: AirAction) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(labelRes), modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { cycleMapping(source) }) {
                Text(actionLabel(mapping(source, mappingsVersion)))
            }
        }
    }

    @Composable
    private fun actionLabel(action: AirAction): String = stringResource(
        when (action) {
            AirAction.TAP -> R.string.action_tap
            AirAction.DOUBLE_TAP -> R.string.action_double_tap
            AirAction.BACK -> R.string.action_back
            AirAction.HOME -> R.string.action_home
            AirAction.RECENTS -> R.string.action_recents
            AirAction.LONG_PRESS -> R.string.action_long_press
            AirAction.SCROLL_UP -> R.string.action_scroll_up
            AirAction.SCROLL_DOWN -> R.string.action_scroll_down
            AirAction.NONE -> R.string.status_not_ready
        }
    )

    @Composable
    private fun StatusCard(title: String, message: String, isError: Boolean = false) {
        val colors = MaterialTheme.colorScheme
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isError) colors.errorContainer else colors.secondaryContainer,
                contentColor = if (isError) colors.onErrorContainer else colors.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
