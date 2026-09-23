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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var pointerEnabled by mutableStateOf(true)
    private var gesturesEnabled by mutableStateOf(true)
    private var running by mutableStateOf(false)
    private var handsDetected by mutableStateOf(0)
    private var lastGesture by mutableStateOf("None")
    private var pointerTracking by mutableStateOf(false)
    private var visionReady by mutableStateOf(false)
    private var visionError by mutableStateOf<String?>(null)
    private var accessibilityEnabled by mutableStateOf(false)
    private var mappingsVersion by mutableStateOf(0)
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mappings = ActionMappingStore(this)
        pointerEnabled = mappings.pointerEnabled()
        gesturesEnabled = mappings.gesturesEnabled()
        setContent { AirGestureScreen() }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        val mappings = ActionMappingStore(this)
        running = AirRuntime.running
        pointerEnabled = mappings.pointerEnabled()
        gesturesEnabled = mappings.gesturesEnabled()
        handsDetected = AirRuntime.handsDetected
        lastGesture = AirRuntime.lastGesture
        pointerTracking = AirRuntime.pointerTracking
        visionReady = AirRuntime.visionReady
        visionError = AirRuntime.visionError
        accessibilityEnabled = AirAccessibilityService.enabled()
    }

    private fun startSession() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, GestureCaptureService::class.java))
        refresh()
    }

    private fun stopSession() {
        stopService(Intent(this, GestureCaptureService::class.java))
        refresh()
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
        val next = options[(options.indexOf(current).coerceAtLeast(0) + 1) % options.size]
        store.setMapping(source, next)
        mappingsVersion++
    }

    private fun mapping(source: AirAction): AirAction {
        mappingsVersion
        return ActionMappingStore(this).mapping(source)
    }

    @androidx.compose.runtime.Composable
    private fun AirGestureScreen() {
        LaunchedEffect(Unit) {
            while (true) {
                refresh()
                delay(250)
            }
        }
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Aergis", style = MaterialTheme.typography.headlineLarge)
                    Text("Air gesture control • 0.10.0-preview")
                    Text(if (running) "Session: ACTIVE" else "Session: STOPPED")

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Pointer mode"); Text("Enable pointer tracking") }
                        Switch(checked = pointerEnabled, onCheckedChange = {
                            pointerEnabled = it
                            ActionMappingStore(this@MainActivity).setPointerEnabled(it)
                            AirRuntime.pointerEnabled = it
                            AirAccessibilityService.instance?.updatePointer(0f, 0f, it && AirRuntime.pointerTracking)
                        })
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Gesture actions"); Text("Enable gesture-triggered actions") }
                        Switch(checked = gesturesEnabled, onCheckedChange = {
                            gesturesEnabled = it
                            ActionMappingStore(this@MainActivity).setGesturesEnabled(it)
                            AirRuntime.gesturesEnabled = it
                        })
                    }

                    Text("Accessibility: ${if (accessibilityEnabled) "ENABLED" else "NOT ENABLED"}")
                    Text("Vision: ${if (visionReady) "READY" else "NOT READY"}")
                    Text("Hands detected: $handsDetected")
                    Text("Gesture: $lastGesture")
                    Text("Pointer tracking: ${if (pointerTracking) "TRACKING" else "NO HAND"}")
                    visionError?.let { Text("Vision error: $it") }

                    Text("Gesture mappings", style = MaterialTheme.typography.titleMedium)
                    MappingRow("Thumb up →", AirAction.TAP)
                    MappingRow("Victory / peace →", AirAction.BACK)
                    MappingRow("Open palm →", AirAction.HOME)
                    MappingRow("Fist →", AirAction.RECENTS)
                    MappingRow("Pointing up →", AirAction.DOUBLE_TAP)

                    Button(onClick = { if (running) stopSession() else startSession() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (running) "Stop capture" else "Start capture")
                    }
                    Button(onClick = { openAccessibilitySettings() }, modifier = Modifier.fillMaxWidth()) { Text("Accessibility settings") }
                    Button(onClick = { openAppDetails() }, modifier = Modifier.fillMaxWidth()) { Text("App settings") }
                    Text("Camera: ${if (AirRuntime.cameraReady) "ready" else "not active"}")
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun MappingRow(label: String, source: AirAction) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Button(onClick = { cycleMapping(source) }) {
                Text(mapping(source).name.replace('_', ' '))
            }
        }
    }
}
