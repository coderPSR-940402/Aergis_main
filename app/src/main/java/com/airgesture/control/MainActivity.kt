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
        if (granted) refreshSettings()
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
                            Text("Aergis", style = MaterialTheme.typography.headlineLarge)
                            Text("Air gesture control • 0.10.0-preview")
                        }
                    }
                    item { Text(if (runtime.running) "Session: ACTIVE" else "Session: STOPPED") }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Pointer mode")
                                Text("Enable pointer tracking")
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
                            Column {
                                Text("Gesture actions")
                                Text("Enable gesture-triggered actions")
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
                            Text("Tracking Hand Preference")
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
                    item { Text("Accessibility: ${if (accessibilityEnabled) "ENABLED" else "NOT ENABLED"}") }
                    item { Text("Vision: ${if (runtime.visionReady) "READY" else "NOT READY"}") }
                    item { Text("Hands detected: ${runtime.handsDetected}") }
                    item { Text("Tracking hand: ${runtime.handedness}") }
                    item { Text("Gesture: ${runtime.lastGesture}") }
                    item { Text("Pointer tracking: ${if (runtime.pointerTracking) "TRACKING" else "NO HAND"}") }
                    runtime.visionError?.let { error -> item { Text("Vision error: $error") } }
                    sessionStartError?.let { error -> item { Text("Cannot start session: $error") } }
                    item {
                        Text(
                            "Gesture mappings",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item { MappingRow("Thumb up →", AirAction.TAP) }
                    item { MappingRow("Victory / peace →", AirAction.BACK) }
                    item { MappingRow("Open palm →", AirAction.HOME) }
                    item { MappingRow("Fist →", AirAction.RECENTS) }
                    item { MappingRow("Pointing up →", AirAction.DOUBLE_TAP) }
                    item {
                        Button(
                            onClick = { if (runtime.running) stopSession() else startSession() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (runtime.running) "Stop capture" else "Start capture")
                        }
                    }
                    item {
                        Button(onClick = { openAccessibilitySettings() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Accessibility settings")
                        }
                    }
                    item {
                        Button(onClick = { openAppDetails() }, modifier = Modifier.fillMaxWidth()) {
                            Text("App settings")
                        }
                    }
                    item {
                        Text(
                            "Camera: ${if (runtime.cameraReady) "ready" else "not active"}",
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MappingRow(label: String, source: AirAction) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { cycleMapping(source) }) {
                Text(mapping(source, mappingsVersion).name.replace('_', ' '))
            }
        }
    }
}
