package com.airgesture.control

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.airgesture.control.ui.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var pointerEnabled by mutableStateOf(true)
    private var gesturesEnabled by mutableStateOf(true)
    private var handPreference by mutableStateOf(ControlHandPreference.EITHER)
    private var sessionStartError by mutableStateOf<String?>(null)
    private var accessibilityEnabled by mutableStateOf(false)
    private var mappingsVersion by mutableIntStateOf(0)
    private var calibrationState by mutableStateOf(PointerCalibrationSession.State.IDLE)
    private var calibrationSampleCount by mutableIntStateOf(0)
    private var calibrationEnabled by mutableStateOf(false)
    private var calibrationDraft by mutableStateOf(PointerCalibrationProfile.COMFORTABLE_REACH)
    private var calibrationMessage by mutableStateOf<String?>(null)
    private val calibrationSession = PointerCalibrationSession()
    private var pendingPdf: File? = null
    private var pendingZip: File? = null
    private val savePdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file = pendingPdf
        pendingPdf = null
        if (uri != null && file != null) writeReport(file, uri)
    }
    private val saveZip = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = pendingZip
        pendingZip = null
        if (uri != null && file != null) writeReport(file, uri)
    }

    private fun saveReport(file: File) {
        val prefix = "Aergis-${file.parentFile?.name}"
        if (file.extension == "pdf") { pendingPdf = file; savePdf.launch("$prefix.pdf") }
        else { pendingZip = file; saveZip.launch("$prefix.zip") }
    }

    private fun writeReport(file: File, uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val output = contentResolver.openOutputStream(uri) ?: error("Could not open selected destination")
                output.use { target -> file.inputStream().use { it.copyTo(target) } }
            } }
            android.widget.Toast.makeText(this@MainActivity,
                if (result.isSuccess) getString(R.string.testing_saved) else "Save failed: ${result.exceptionOrNull()?.message}",
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun shareReport(report: DiagnosticExport) {
        runCatching {
            val uris = arrayListOf(report.pdf, report.bundle).map { FileProvider.getUriForFile(this, "$packageName.testing-files", it) }
            val clip = android.content.ClipData.newUri(contentResolver, "Aergis report", uris.first())
            clip.addItem(android.content.ClipData.Item(uris.last()))
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.testing_share)))
        }.onFailure { android.widget.Toast.makeText(this, "Share failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            refreshSettings()
            startSession()
        } else {
            sessionStartError = getString(R.string.camera_permission_required)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        pendingPdf = savedInstanceState?.getString("testing_pending_pdf")?.let(::File)
        pendingZip = savedInstanceState?.getString("testing_pending_zip")?.let(::File)
        refreshSettings()
        TestingTools.restoreLatest(this)
        setContent { AirGestureScreen() }
    }

    override fun onResume() {
        super.onResume()
        refreshSettings()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // A chooser/protected-screen event may be the last accessibility event.
            // Actual window focus proves our own activity is foreground again.
            AirRuntime.setForegroundContext(ForegroundContextPolicy.evaluate(packageName, javaClass.name))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingPdf?.let { outState.putString("testing_pending_pdf", it.absolutePath) }
        pendingZip?.let { outState.putString("testing_pending_zip", it.absolutePath) }
        super.onSaveInstanceState(outState)
    }

    private fun refreshSettings() {
        val mappings = ActionMappingStore(this)
        pointerEnabled = mappings.pointerEnabled()
        gesturesEnabled = mappings.gesturesEnabled()
        handPreference = mappings.handPreference()
        refreshCalibrationSettings()
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

    private fun isLandscape(): Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun refreshCalibrationSettings() {
        val store = PointerCalibrationStore(this)
        calibrationEnabled = store.enabled(handPreference, isLandscape())
        calibrationDraft = if (store.hasValidProfile(handPreference, isLandscape())) {
            store.profile(handPreference, isLandscape())
        } else {
            PointerCalibrationProfile.COMFORTABLE_REACH
        }
    }

    private fun applyCalibration(profile: PointerCalibrationProfile): Boolean {
        if (PointerCalibrationStore(this).save(profile, handPreference, isLandscape())) {
            calibrationDraft = profile
            calibrationEnabled = true
            calibrationMessage = getString(R.string.calibration_enabled)
            return true
        }
        return false
    }

    private fun startCalibration() {
        AirRuntime.controlMode = ControlMode.READY
        calibrationSession.start()
        calibrationState = calibrationSession.state()
        calibrationSampleCount = calibrationSession.sampleCount()
        calibrationMessage = getString(R.string.calibration_capture_guidance)
    }

    private fun captureCalibrationSample() {
        if (AirRuntime.controlMode != ControlMode.READY) return
        val rawPointer = AirRuntime.rawPointerSnapshot()
        val accepted = rawPointer.tracking && calibrationSession.addSample(rawPointer.x, rawPointer.y)
        calibrationState = calibrationSession.state()
        calibrationSampleCount = calibrationSession.sampleCount()
        calibrationMessage = if (accepted) {
            getString(R.string.calibration_sample_captured, calibrationSampleCount)
        } else {
            getString(R.string.calibration_sample_rejected)
        }
    }

    private fun completeCalibration() {
        if (AirRuntime.controlMode != ControlMode.READY) return
        val profile = calibrationSession.complete()
        calibrationState = calibrationSession.state()
        calibrationSampleCount = calibrationSession.sampleCount()
        if (profile == null) {
            calibrationMessage = getString(R.string.calibration_invalid)
            return
        }
        if (applyCalibration(profile.copy(curveX = calibrationDraft.curveX, curveY = calibrationDraft.curveY))) {
            calibrationSession.reset()
            calibrationState = calibrationSession.state()
            calibrationSampleCount = 0
        }
    }

    private fun cancelCalibration() {
        calibrationSession.reset()
        calibrationState = calibrationSession.state()
        calibrationSampleCount = 0
        calibrationMessage = getString(R.string.calibration_cancelled)
    }

    private fun disableCalibration() {
        PointerCalibrationStore(this).setEnabled(false, handPreference, isLandscape())
        calibrationEnabled = false
        calibrationMessage = getString(R.string.calibration_disabled)
    }

    private fun resetCalibration() {
        PointerCalibrationStore(this).reset(handPreference, isLandscape())
        calibrationDraft = PointerCalibrationProfile.COMFORTABLE_REACH
        calibrationSession.reset()
        calibrationState = calibrationSession.state()
        calibrationSampleCount = calibrationSession.sampleCount()
        calibrationEnabled = false
        calibrationMessage = getString(R.string.calibration_reset)
    }

    private fun mapping(source: RegisteredGesture, version: Int): AirAction {
        return ActionMappingStore(this).mapping(source)
    }

    @Composable
    private fun AirGestureScreen() {
        AergisTheme {
            AergisShell(gesturesEnabled) { destination, navigate ->
                when (destination) {
                    AergisDestination.HOME -> ControlScreen(
                        accessibilityEnabled = accessibilityEnabled,
                        pointerEnabled = pointerEnabled,
                        gesturesEnabled = gesturesEnabled,
                        startError = sessionStartError,
                        onCapture = { if (AirRuntime.running) stopSession() else startSession() },
                        onArm = {
                            AirRuntime.controlMode = if (AirRuntime.controlMode == ControlMode.ARMED) {
                                ControlMode.READY
                            } else {
                                ControlMode.ARMED
                            }
                        },
                        onAccessibility = ::openAccessibilitySettings,
                        onNavigate = navigate
                    )
                    AergisDestination.LIVE -> TrackingScreen()
                    AergisDestination.CONTROLS -> ControlsScreen()
                    AergisDestination.MORE -> MoreScreen()
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @Composable
    private fun TrackingScreen() {
        // Diagnostic numbers refresh at 4 Hz; the pointer preview has its own draw-phase state.
        val flow = remember { AirRuntime.uiState.sample(250L) }
        val runtime by flow.collectAsStateWithLifecycle(initialValue = AirRuntime.uiStateSnapshot())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AergisSection(stringResource(R.string.ui_live_title), stringResource(R.string.ui_live_description)) }
            item {
                AergisPanel {
                    AergisStatusChip(stringResource(if (runtime.pointerTracking) R.string.ui_tracking_live else R.string.ui_waiting_for_hand),
                        if (runtime.pointerTracking) AergisColors.Cyan else AergisColors.Muted)
                    Text(stringResource(runtime.pointerFeedback.label()), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.hands_detected, runtime.handsDetected))
                    Text(stringResource(R.string.tracking_hand, runtime.handedness))
                    Text(stringResource(R.string.gesture, runtime.lastGesture))
                    Text(stringResource(R.string.camera_status, stringResource(if (runtime.cameraReady) R.string.status_ready else R.string.status_not_active)))
                    Text(stringResource(R.string.vision_status, stringResource(if (runtime.visionReady) R.string.status_ready else R.string.status_not_ready)))
                    Text(stringResource(R.string.foreground_status, stringResource(when (runtime.foregroundSafety) {
                        ForegroundSafety.SAFE -> R.string.foreground_safe
                        ForegroundSafety.PROTECTED -> R.string.foreground_protected
                        ForegroundSafety.UNKNOWN -> R.string.foreground_unknown
                    })))
                    runtime.visionError?.let { Text(it, color = AergisColors.Error) }
                }
            }
            item { TestingToolsCard(::saveReport, ::shareReport) }
        }
    }

    /** Controls: Pointer, Gestures and Calibration. Existing screens are reused unchanged inside the tabs. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @Composable
    private fun ControlsScreen() {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                AergisSegmentedTabs(
                    listOf(
                        stringResource(R.string.ui_controls_tab_pointer),
                        stringResource(R.string.ui_controls_tab_gestures),
                        stringResource(R.string.ui_controls_tab_calibration)
                    ), tab, { tab = it }
                )
            }
            when (tab) {
                0 -> PointerSettingsScreen()
                1 -> GesturesScreen()
                else -> CalibrationScreen()
            }
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @Composable
    private fun CalibrationScreen() {
        DisposableEffect(Unit) {
            onDispose { if (calibrationState != PointerCalibrationSession.State.IDLE) cancelCalibration() }
        }
        val flow = remember { AirRuntime.uiState.sample(250L) }
        val runtime by flow.collectAsStateWithLifecycle(initialValue = AirRuntime.uiStateSnapshot())
        val rawTrackingFlow = remember { AirRuntime.state.map { it.rawPointer.tracking }.distinctUntilChanged() }
        val rawTracking by rawTrackingFlow.collectAsStateWithLifecycle(initialValue = AirRuntime.rawPointerSnapshot().tracking)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { CalibrationCard(rawTracking, runtime.controlMode, runtime.running && runtime.cameraReady) }
            item { PracticeCard(runtime.controlMode) }
        }
    }

    /** More: diagnostics, permissions and about. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @Composable
    private fun MoreScreen() {
        val flow = remember { AirRuntime.uiState.sample(250L) }
        val runtime by flow.collectAsStateWithLifecycle(initialValue = AirRuntime.uiStateSnapshot())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AergisSection(stringResource(R.string.ui_more_title), stringResource(R.string.ui_more_description)) }
            item { DiagnosticsCard(runtime) }
            item {
                AergisPanel {
                    Text(stringResource(R.string.ui_permissions_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.accessibility_status, stringResource(if (accessibilityEnabled) R.string.status_enabled else R.string.status_not_enabled)))
                    AergisButton(onClick = ::openAccessibilitySettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.accessibility_settings)) }
                    AergisButton(onClick = ::openAppDetails, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.app_settings)) }
                }
            }
            item {
                AergisPanel {
                    Text(stringResource(R.string.ui_about_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.app_tagline), color = AergisColors.Muted)
                    Text(stringResource(R.string.ui_privacy_description), style = MaterialTheme.typography.bodyMedium, color = AergisColors.Muted)
                }
            }
        }
    }

    @Composable
    private fun GesturesScreen() {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AergisSection(stringResource(R.string.ui_gestures_title), stringResource(R.string.ui_gestures_screen_description)) }
            item {
                AergisPanel {
                    AergisStatusChip(stringResource(R.string.ui_implemented), AergisColors.Success)
                    Text(stringResource(R.string.ui_pointer_gestures), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_pointer_gestures_description), color = AergisColors.Muted)
                }
            }
            item { Text(stringResource(R.string.gesture_tuning_help), style = MaterialTheme.typography.bodyMedium) }
            item { Text(stringResource(R.string.ui_mapping_selection_help), color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium) }
            RegisteredGesture.entries.forEach { gesture ->
                item(key = gesture.name) { MappingRow(gesture) }
            }
            item {
                AergisPanel {
                    Text(stringResource(R.string.practice_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.practice_description), color = AergisColors.Muted)
                    Text(stringResource(R.string.practice_safety_note), style = MaterialTheme.typography.bodyMedium, color = AergisColors.Muted)
                }
            }
        }
    }

    @Composable
    private fun PointerSettingsScreen() {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AergisSection(stringResource(R.string.ui_controls_title), stringResource(R.string.ui_controls_description)) }
            item {
                AergisPanel {
                    AergisSettingRow(stringResource(R.string.pointer_mode), stringResource(R.string.pointer_mode_description), pointerEnabled) {
                        pointerEnabled = it
                        ActionMappingStore(this@MainActivity).setPointerEnabled(it)
                        AirRuntime.pointerEnabled = it
                        AirAccessibilityService.instance?.updatePointer(
                            AirRuntime.pointerX,
                            AirRuntime.pointerY,
                            it && AirRuntime.pointerTracking
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(color = AergisColors.Outline)
                    AergisSettingRow(stringResource(R.string.gesture_actions), stringResource(R.string.gesture_actions_description), gesturesEnabled) {
                        gesturesEnabled = it
                        ActionMappingStore(this@MainActivity).setGesturesEnabled(it)
                        AirRuntime.gesturesEnabled = it
                    }
                }
            }
            item {
                AergisPanel {
                    Text(stringResource(R.string.tracking_hand_preference), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ui_hand_preference_description), color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    ControlHandSelector(
                        selected = handPreference,
                        onSelected = { selected ->
                            handPreference = selected
                            ActionMappingStore(this@MainActivity).setHandPreference(selected)
                            AirRuntime.handPreference = selected
                            calibrationSession.reset()
                            calibrationState = calibrationSession.state()
                            calibrationSampleCount = 0
                            calibrationMessage = null
                            refreshCalibrationSettings()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun CalibrationCard(sampleAvailable: Boolean, controlMode: ControlMode, cameraAvailable: Boolean) {
        var advanced by rememberSaveable { mutableStateOf(false) }
        val capturing = calibrationState != PointerCalibrationSession.State.IDLE
        AergisPanel {
            Text(stringResource(R.string.calibration_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.calibration_context,
                stringResource(when (handPreference) {
                    ControlHandPreference.LEFT -> R.string.hand_left
                    ControlHandPreference.RIGHT -> R.string.hand_right
                    ControlHandPreference.EITHER -> R.string.hand_either
                }), stringResource(if (isLandscape()) R.string.orientation_landscape else R.string.orientation_portrait)))
            Text(stringResource(R.string.calibration_status,
                stringResource(if (calibrationEnabled) R.string.calibration_status_enabled else R.string.calibration_status_disabled),
                calibrationSampleCount))
            Text(stringResource(R.string.calibration_raw_preview), color = AergisColors.Muted,
                style = MaterialTheme.typography.labelMedium)
            CalibrationPreview(rawCoordinates = true)
            calibrationMessage?.let { Text(it) }
            if (capturing) {
                Text(stringResource(if (controlMode == ControlMode.READY) R.string.calibration_actions_disarmed
                    else R.string.calibration_restart_disarmed), color = AergisColors.Caution)
                Text(stringResource(if (calibrationState == PointerCalibrationSession.State.INVALID) R.string.calibration_invalid
                    else when (calibrationSampleCount) {
                    0 -> R.string.calibration_corner_top_left
                    1 -> R.string.calibration_corner_top_right
                    2 -> R.string.calibration_corner_bottom_right
                    3 -> R.string.calibration_corner_bottom_left
                    else -> R.string.calibration_review_corners
                }), style = MaterialTheme.typography.titleMedium)
                if (!sampleAvailable) Text(stringResource(R.string.calibration_wait_for_tracking), color = AergisColors.Caution)
                if (calibrationState != PointerCalibrationSession.State.INVALID) {
                    AergisButton(onClick = ::captureCalibrationSample, enabled = sampleAvailable && controlMode == ControlMode.READY,
                        primary = calibrationSampleCount < 4, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (calibrationSampleCount < 4) R.string.calibration_capture_corner else R.string.calibration_add_sample))
                    }
                    AergisButton(onClick = ::completeCalibration,
                        enabled = calibrationState == PointerCalibrationSession.State.READY && controlMode == ControlMode.READY,
                        primary = calibrationSampleCount >= 4, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.calibration_save))
                    }
                }
                AergisButton(onClick = ::startCalibration, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.calibration_restart))
                }
                AergisButton(onClick = ::cancelCalibration, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.calibration_cancel))
                }
            } else {
                Text(stringResource(R.string.calibration_guided_intro))
                if (!sampleAvailable) Text(stringResource(R.string.calibration_wait_for_tracking), color = AergisColors.Caution)
                AergisButton(onClick = { advanced = false; startCalibration() }, enabled = cameraAvailable,
                    primary = true, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.calibration_begin_guided))
                }
                AergisButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (advanced) R.string.calibration_hide_advanced else R.string.calibration_show_advanced))
                }
                if (advanced) {
                    Text(stringResource(R.string.calibration_reach_guidance))
                    AergisButton(onClick = { applyCalibration(PointerCalibrationProfile.COMFORTABLE_REACH) },
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calibration_comfortable)) }
                    CalibrationSlider(R.string.calibration_left_bound, calibrationDraft.left, 0f..1f) {
                        applyCalibration(calibrationDraft.copy(left = it.coerceAtMost(calibrationDraft.right - PointerCalibrationProfile.MIN_ACTIVE_SPAN)))
                    }
                    CalibrationSlider(R.string.calibration_right_bound, calibrationDraft.right, 0f..1f) {
                        applyCalibration(calibrationDraft.copy(right = it.coerceAtLeast(calibrationDraft.left + PointerCalibrationProfile.MIN_ACTIVE_SPAN)))
                    }
                    CalibrationSlider(R.string.calibration_top_bound, calibrationDraft.top, 0f..1f) {
                        applyCalibration(calibrationDraft.copy(top = it.coerceAtMost(calibrationDraft.bottom - PointerCalibrationProfile.MIN_ACTIVE_SPAN)))
                    }
                    CalibrationSlider(R.string.calibration_bottom_bound, calibrationDraft.bottom, 0f..1f) {
                        applyCalibration(calibrationDraft.copy(bottom = it.coerceAtLeast(calibrationDraft.top + PointerCalibrationProfile.MIN_ACTIVE_SPAN)))
                    }
                    CalibrationSlider(R.string.calibration_curve_x, calibrationDraft.curveX,
                        PointerCalibrationProfile.MIN_CURVE..PointerCalibrationProfile.MAX_CURVE) {
                        applyCalibration(calibrationDraft.copy(curveX = it))
                    }
                    CalibrationSlider(R.string.calibration_curve_y, calibrationDraft.curveY,
                        PointerCalibrationProfile.MIN_CURVE..PointerCalibrationProfile.MAX_CURVE) {
                        applyCalibration(calibrationDraft.copy(curveY = it))
                    }
                    AergisButton(onClick = ::disableCalibration, enabled = calibrationEnabled,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calibration_disable)) }
                    AergisButton(onClick = ::resetCalibration, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.calibration_reset_button))
                    }
                }
            }
        }
    }

    @Composable
    private fun CalibrationSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, onChanged: (Float) -> Unit) {
        AergisSlider(stringResource(label, value), value, range, onChanged)
    }

    @Composable
    private fun PracticeCard(controlMode: ControlMode) {
        AergisPanel {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.practice_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.practice_description))
                Text(stringResource(R.string.practice_safety_note))
                AergisButton(
                    enabled = controlMode == ControlMode.ARMED,
                    onClick = { AirRuntime.controlMode = ControlMode.READY },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.practice_disarm))
                }
            }
        }
    }

    @Composable
    private fun DiagnosticsCard(runtime: AirRuntimeUiState) {
        val pose = runtime.poseEvidence
        val vision = runtime.visionTelemetry
        val dispatch = runtime.actionDispatchTelemetry
        AergisPanel {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.diagnostics_vision_counts, vision.acceptedResults, vision.rejectedResults))
                Text(
                    stringResource(
                        R.string.diagnostics_last_rejection,
                        vision.lastRejectionReason ?: stringResource(R.string.diagnostics_none)
                    )
                )
                if (pose == null) {
                    Text(stringResource(R.string.diagnostics_pose_waiting))
                } else {
                    Text(
                        stringResource(
                            R.string.diagnostics_pose_status,
                            if (pose.accepted) {
                                stringResource(R.string.diagnostics_accepted)
                            } else {
                                stringResource(R.string.diagnostics_rejected)
                            },
                            pose.finiteLandmarkCount,
                            pose.landmarkCount
                        )
                    )
                    Text(stringResource(R.string.diagnostics_pose_reason, pose.rejectionReason.name))
                }
                Text(
                    stringResource(
                        R.string.diagnostics_dispatch_counts,
                        dispatch.completed,
                        dispatch.cancelled,
                        dispatch.platformRejected,
                        dispatch.policyDenied
                    )
                )
                Text(stringResource(R.string.diagnostics_privacy_note))
            }
        }
    }

    @Composable
    private fun MappingRow(source: RegisteredGesture) {
        var showPicker by rememberSaveable(source.name) { mutableStateOf(false) }
        val current = mapping(source, mappingsVersion)
        val gestureName = stringResource(when (source) {
            RegisteredGesture.THUMB_UP -> R.string.mapping_thumb_up
            RegisteredGesture.VICTORY -> R.string.mapping_victory
            RegisteredGesture.OPEN_PALM -> R.string.mapping_open_palm
            RegisteredGesture.CLOSED_FIST -> R.string.mapping_fist
            RegisteredGesture.POINTING_UP -> R.string.mapping_pointing_up
            RegisteredGesture.THUMB_DOWN -> R.string.mapping_thumb_down
            RegisteredGesture.I_LOVE_YOU -> R.string.mapping_i_love_you
            RegisteredGesture.OK -> R.string.mapping_ok
            RegisteredGesture.THREE_FINGERS -> R.string.mapping_three
            RegisteredGesture.FOUR_FINGERS -> R.string.mapping_four
            RegisteredGesture.SHAKA -> R.string.mapping_shaka
            RegisteredGesture.PINKY_ONLY -> R.string.mapping_pinky
        })
        AergisPanel {
            Text(gestureName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(when (source) {
                RegisteredGesture.OK -> R.string.pose_ok_help
                RegisteredGesture.THREE_FINGERS -> R.string.pose_three_help
                RegisteredGesture.FOUR_FINGERS -> R.string.pose_four_help
                RegisteredGesture.SHAKA -> R.string.pose_shaka_help
                RegisteredGesture.PINKY_ONLY -> R.string.pose_pinky_help
                else -> R.string.pose_builtin_help
            }), color = AergisColors.Muted, style = MaterialTheme.typography.bodySmall)
            AergisButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_change_mapping, actionLabel(current)))
            }
            if (current != AirAction.NONE && !GestureActionPolicy.isClassifierActionEnabled(
                    true, current, pointerEnabled)) {
                Text(stringResource(R.string.ui_mapping_pointer_restriction),
                    color = AergisColors.Caution, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (showPicker) ActionMappingPicker(
            gestureName = gestureName, source = source, current = current,
            pointerEnabled = pointerEnabled,
            tuning = ActionMappingStore(this).tuning(source),
            onSave = { target, tuning ->
                ActionMappingStore(this).saveBinding(source, target, tuning)
                mappingsVersion++
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}
