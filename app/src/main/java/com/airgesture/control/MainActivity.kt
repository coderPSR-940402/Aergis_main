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

    private fun cycleMapping(source: AirAction) {
        val options = AirAction.entries.filter { it != AirAction.NONE }
        val store = ActionMappingStore(this)
        val current = store.mapping(source)
        val index = options.indexOf(current).coerceAtLeast(0)
        store.setMapping(source, options[(index + 1) % options.size])
        mappingsVersion++
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

    private fun applyCalibration(profile: PointerCalibrationProfile) {
        if (PointerCalibrationStore(this).save(profile, handPreference, isLandscape())) {
            calibrationDraft = profile
            calibrationEnabled = true
            calibrationMessage = getString(R.string.calibration_enabled)
        }
    }

    private fun startCalibration() {
        calibrationSession.start()
        calibrationState = calibrationSession.state()
        calibrationSampleCount = calibrationSession.sampleCount()
        calibrationMessage = getString(R.string.calibration_capture_guidance)
    }

    private fun captureCalibrationSample() {
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
        val profile = calibrationSession.complete()
        calibrationState = calibrationSession.state()
        calibrationSampleCount = calibrationSession.sampleCount()
        if (profile == null) {
            calibrationMessage = getString(R.string.calibration_invalid)
            return
        }
        applyCalibration(profile.copy(curveX = calibrationDraft.curveX, curveY = calibrationDraft.curveY))
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

    private fun mapping(source: AirAction, version: Int): AirAction {
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
                    Text(stringResource(R.string.motion_status, stringResource(when {
                        !runtime.motionSensorsAvailable -> R.string.motion_unavailable
                        runtime.motionActive -> R.string.motion_active
                        else -> R.string.motion_inactive
                    })))
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
        val flow = remember { AirRuntime.uiState.sample(250L) }
        val runtime by flow.collectAsStateWithLifecycle(initialValue = AirRuntime.uiStateSnapshot())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { CalibrationCard(runtime.pointerTracking) }
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
            item { Text(stringResource(R.string.ui_mapping_cycle), color = AergisColors.Muted, style = MaterialTheme.typography.bodyMedium) }
            item { MappingRow(R.string.mapping_thumb_up, AirAction.TAP) }
            item { MappingRow(R.string.mapping_victory, AirAction.BACK) }
            item { MappingRow(R.string.mapping_open_palm, AirAction.HOME) }
            item { MappingRow(R.string.mapping_fist, AirAction.RECENTS) }
            item { MappingRow(R.string.mapping_pointing_up, AirAction.DOUBLE_TAP) }
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
    private fun CalibrationCard(pointerTracking: Boolean) {
        AergisPanel {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.calibration_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        R.string.calibration_status,
                        if (calibrationEnabled) {
                            stringResource(R.string.calibration_status_enabled)
                        } else {
                            stringResource(R.string.calibration_status_disabled)
                        },
                        calibrationSampleCount
                    )
                )
                Text(stringResource(
                    R.string.calibration_context,
                    stringResource(when (handPreference) {
                        ControlHandPreference.LEFT -> R.string.hand_left
                        ControlHandPreference.RIGHT -> R.string.hand_right
                        ControlHandPreference.EITHER -> R.string.hand_either
                    }),
                    stringResource(if (isLandscape()) R.string.orientation_landscape else R.string.orientation_portrait)
                ))
                Text(stringResource(R.string.calibration_reach_guidance))
                Text(stringResource(R.string.ui_pointer_preview), color = AergisColors.Muted, style = MaterialTheme.typography.labelMedium)
                CalibrationPreview()
                calibrationMessage?.let { Text(it) }
                AergisButton(
                    onClick = { applyCalibration(PointerCalibrationProfile.COMFORTABLE_REACH) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.calibration_comfortable)) }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AergisButton(
                        onClick = { startCalibration() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.calibration_start))
                    }
                    AergisButton(
                        onClick = { captureCalibrationSample() },
                        enabled = (calibrationState == PointerCalibrationSession.State.COLLECTING ||
                            calibrationState == PointerCalibrationSession.State.READY) &&
                            pointerTracking,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.calibration_capture))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AergisButton(
                        onClick = { completeCalibration() },
                        enabled = calibrationState == PointerCalibrationSession.State.READY,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.calibration_complete))
                    }
                    AergisButton(
                        onClick = { disableCalibration() },
                        enabled = calibrationEnabled,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.calibration_disable))
                    }
                }
                AergisButton(
                    onClick = { resetCalibration() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.calibration_reset_button))
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
    private fun MappingRow(labelRes: Int, source: AirAction) {
        AergisPanel {
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
            AergisButton(onClick = { cycleMapping(source) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_change_mapping, actionLabel(mapping(source, mappingsVersion))))
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

}
