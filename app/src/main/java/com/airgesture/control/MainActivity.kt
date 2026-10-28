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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var requestedDestination by mutableStateOf<AergisDestination?>(null)
    private var pointerEnabled by mutableStateOf(true)
    private var gesturesEnabled by mutableStateOf(true)
    private var pointerClickingEnabled by mutableStateOf(true)
    private var handPreference by mutableStateOf(ControlHandPreference.EITHER)
    private var sessionStartError by mutableStateOf<String?>(null)
    private var accessibilityEnabled by mutableStateOf(false)
    private var cameraAllowed by mutableStateOf(false)
    private var mappingsVersion by mutableIntStateOf(0)
    private var calibrationState by mutableStateOf(PointerCalibrationSession.State.IDLE)
    private var calibrationSampleCount by mutableIntStateOf(0)
    private var calibrationEnabled by mutableStateOf(false)
    private var calibrationDraft by mutableStateOf(PointerCalibrationProfile.COMFORTABLE_REACH)
    private var calibrationMessage by mutableStateOf<String?>(null)
    private val calibrationSession = PointerCalibrationSession()
    private val touchFreeCalibration = TouchFreeCalibration()
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

    // Setup asks only for permission. Starting capture remains an explicit Control action.
    private val setupCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        refreshSettings()
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
        handleCalibrationIntent(intent)
        setContent { AirGestureScreen() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCalibrationIntent(intent)
    }

    private fun handleCalibrationIntent(request: Intent) {
        if (request.action != ACTION_OPEN_CALIBRATION) return
        // Consume once: recreation restores the route without restarting capture.
        request.action = Intent.ACTION_MAIN
        startCalibration()
        requestedDestination = AergisDestination.CALIBRATION
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
        pointerClickingEnabled = mappings.pointerClickingEnabled()
        AirRuntime.pointerClickingEnabled = pointerClickingEnabled
        handPreference = mappings.handPreference()
        refreshCalibrationSettings()
        AirRuntime.handPreference = handPreference
        accessibilityEnabled = AirAccessibilityService.enabled()
        cameraAllowed = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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
        touchFreeCalibration.start()
        calibrationState = calibrationSession.state()
        calibrationSampleCount = calibrationSession.sampleCount()
        calibrationMessage = getString(R.string.calibration_capture_guidance)
    }

    private fun captureCalibrationSample() {
        if (AirRuntime.controlMode != ControlMode.READY) return
        val rawPointer = AirRuntime.rawPointerSnapshot()
        val accepted = rawPointer.tracking && calibrationSession.addSample(rawPointer.x, rawPointer.y)
        if (accepted) touchFreeCalibration.manualSample(rawPointer.x, rawPointer.y)
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
            touchFreeCalibration.stop()
            calibrationSession.reset()
            calibrationState = calibrationSession.state()
            calibrationSampleCount = 0
        }
    }

    private fun cancelCalibration() {
        touchFreeCalibration.stop()
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
        touchFreeCalibration.stop()
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
        // Gesture-dispatched pointer changes must update the open controls too.
        DisposableEffect(Unit) {
            val prefs = getSharedPreferences("aergis_mappings", MODE_PRIVATE)
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                pointerEnabled = ActionMappingStore(this@MainActivity).pointerEnabled()
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        var editingName by rememberSaveable { mutableStateOf<String?>(null) }
        val edit: (RegisteredGesture) -> Unit = { editingName = it.name }
        AergisTheme {
            AergisShell(
                gesturesEnabled || (pointerEnabled && pointerClickingEnabled),
                initialDestination = if (accessibilityEnabled && cameraAllowed) AergisDestination.CONTROL else AergisDestination.WELCOME,
                onEntering = { if (it.disarmsOnEntry) AirRuntime.controlMode = ControlMode.READY },
                requestedDestination = requestedDestination,
                onDestinationHandled = { requestedDestination = null; editingName = null }
            ) { destination, navigate ->
                when (destination) {
                    AergisDestination.CONTROL -> ControlScreen(
                        accessibilityEnabled, pointerEnabled, pointerClickingEnabled, gesturesEnabled,
                        RegisteredGesture.entries.count { mapping(it, mappingsVersion) != AirAction.NONE }, sessionStartError,
                        onCapture = { if (AirRuntime.running) stopSession() else startSession() },
                        onArm = { AirRuntime.controlMode = if (AirRuntime.controlMode == ControlMode.ARMED) ControlMode.READY else ControlMode.ARMED },
                        onGestures = ::setGestureActions, onNavigate = navigate
                    )
                    AergisDestination.GESTURES -> GesturesScreen(edit, navigate)
                    AergisDestination.SETTINGS -> SettingsScreen(navigate)
                    AergisDestination.WELCOME -> WelcomeScreen(cameraAllowed, accessibilityEnabled,
                        { setupCameraPermission.launch(Manifest.permission.CAMERA) }, ::openAccessibilitySettings, ::openAppDetails,
                        handSelector = { HandPreferencePanel() }, onNavigate = navigate)
                    AergisDestination.PERMISSIONS -> ProductPage(R.string.product_permissions) {
                        PermissionsPanel(cameraAllowed, accessibilityEnabled,
                            { setupCameraPermission.launch(Manifest.permission.CAMERA) }, ::openAccessibilitySettings, ::openAppDetails)
                        PrivacyPanel()
                    }
                    AergisDestination.PRECISION -> PrecisionScreen(navigate)
                    AergisDestination.CALIBRATION -> CalibrationScreen()
                    AergisDestination.PRACTICE -> ProductPage(R.string.product_practice) {
                        RehearsalStatus()
                        Text(stringResource(R.string.product_practice_help), color = AergisColors.Muted)
                        if (!pointerEnabled) Text(stringResource(R.string.product_practice_enable_pointer), color = AergisColors.Caution)
                        CalibrationPreview()
                        ProductLink(AergisDestination.CALIBRATION, stringResource(R.string.product_precision_help), navigate)
                    }
                    AergisDestination.LIVE -> TrackingScreen(navigate)
                    AergisDestination.MOTION -> MotionScreen(
                        RegisteredGesture.entries.filter { mapping(it, mappingsVersion) == AirAction.ARM_MOTION },
                        onChoose = { navigate(AergisDestination.GESTURES) }, onNavigate = navigate)
                    AergisDestination.MOTION_PRACTICE -> MotionWalkthrough(navigate)
                    AergisDestination.LEARN -> LearnGesturesScreen({ mapping(it, mappingsVersion) }, edit, navigate)
                    AergisDestination.DIAGNOSTICS -> ProductPage(R.string.product_diagnostics) {
                        Text(stringResource(R.string.product_diagnostics_help), color = AergisColors.Muted)
                        TestingToolsCard(::saveReport, ::shareReport)
                        ProductLink(AergisDestination.ADVANCED, stringResource(R.string.product_advanced_help), navigate)
                        PrivacyPanel()
                    }
                    AergisDestination.ADVANCED -> AdvancedScreen()
                    AergisDestination.ABOUT -> ProductPage(R.string.product_about) {
                        Text(stringResource(R.string.product_about_help), color = AergisColors.Muted)
                        PrivacyPanel()
                        Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            val editing = RegisteredGesture.entries.firstOrNull { it.name == editingName }
            if (editing != null) ActionMappingPicker(
                gestureName = stringResource(gestureLabelRes(editing)), source = editing,
                current = mapping(editing, mappingsVersion), pointerEnabled = pointerEnabled,
                tuning = ActionMappingStore(this).tuning(editing),
                onSave = { target, tuning ->
                    ActionMappingStore(this).saveBinding(editing, target, tuning)
                    mappingsVersion++
                    editingName = null
                }, onDismiss = { editingName = null }
            )
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @Composable
    private fun TrackingScreen(navigate: (AergisDestination) -> Unit) {
        val flow = remember { AirRuntime.uiState.sample(250L) }
        val runtime by flow.collectAsStateWithLifecycle(initialValue = AirRuntime.uiStateSnapshot())
        val testing by TestingTools.state.collectAsStateWithLifecycle()
        ProductPage(R.string.product_live) {
            AergisPanel {
                Text(stringResource(R.string.camera_status, stringResource(if (runtime.cameraReady) R.string.status_ready else R.string.status_not_active)))
                Text(stringResource(R.string.vision_status, stringResource(if (runtime.visionReady) R.string.status_ready else R.string.status_not_ready)))
                Text(stringResource(R.string.hands_detected, runtime.handsDetected))
                Text(stringResource(R.string.tracking_hand, runtime.handedness))
                Text(stringResource(R.string.gesture, runtime.lastGesture))
                Text(stringResource(runtime.pointerFeedback.label()), color = AergisColors.Muted)
                Text(stringResource(R.string.foreground_status, stringResource(when (runtime.foregroundSafety) {
                    ForegroundSafety.SAFE -> R.string.foreground_safe
                    ForegroundSafety.PROTECTED -> R.string.foreground_protected
                    ForegroundSafety.UNKNOWN -> R.string.foreground_unknown
                })))
                runtime.visionError?.let { Text(it, color = AergisColors.Error) }
            }
            AergisButton({ TestingTools.setMirror(!testing.mirror) }, Modifier.fillMaxWidth(),
                enabled = testing.mirror || (runtime.running && runtime.cameraReady && accessibilityEnabled)) {
                Text(stringResource(if (testing.mirror) R.string.testing_mirror_hide else R.string.testing_mirror_show))
            }
            ProductLink(AergisDestination.DIAGNOSTICS, stringResource(R.string.product_diagnostics_help), navigate)
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @Composable
    private fun CalibrationScreen() {
        DisposableEffect(Unit) {
            onDispose { if (calibrationState != PointerCalibrationSession.State.IDLE) cancelCalibration() }
        }
        LaunchedEffect(Unit) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    AirRuntime.state.map { it.calibrationGesture }.distinctUntilChanged().collect { frame ->
                        processCalibrationGesture(frame)
                    }
                } finally { touchFreeCalibration.resetEvidence() }
            }
        }
        val flow = remember { AirRuntime.uiState.sample(250L) }
        val runtime by flow.collectAsStateWithLifecycle(initialValue = AirRuntime.uiStateSnapshot())
        val rawTrackingFlow = remember { AirRuntime.state.map { it.rawPointer.tracking }.distinctUntilChanged() }
        val rawTracking by rawTrackingFlow.collectAsStateWithLifecycle(initialValue = AirRuntime.rawPointerSnapshot().tracking)
        ProductPage(R.string.product_calibration) {
            Text(stringResource(R.string.product_calibration_optional), color = AergisColors.Muted)
            CalibrationCard(rawTracking, runtime.controlMode, runtime.running && runtime.cameraReady)
        }
    }

    private fun processCalibrationGesture(frame: CalibrationGestureFrame?) {
        val safe = AirRuntime.controlMode == ControlMode.READY &&
            AirRuntime.state.value.foregroundContext.safety == ForegroundSafety.SAFE &&
            frame != null && frame.epoch == AirRuntime.actionEpoch &&
            android.os.SystemClock.uptimeMillis() - frame.observedAtMs in 0L..350L
        when (val event = touchFreeCalibration.update(if (safe) frame else null)) {
            is TouchFreeCalibration.Event.Capture -> {
                if (calibrationSession.addSample(event.x, event.y)) {
                    calibrationState = calibrationSession.state()
                    calibrationSampleCount = calibrationSession.sampleCount()
                    calibrationMessage = getString(R.string.calibration_sample_captured, calibrationSampleCount)
                }
            }
            TouchFreeCalibration.Event.Confirm -> completeCalibration()
            TouchFreeCalibration.Event.Cancel -> cancelCalibration()
            TouchFreeCalibration.Event.WrongCorner -> calibrationMessage = getString(R.string.calibration_wrong_corner)
            null -> Unit
        }
    }

    @Composable
    private fun SettingsScreen(navigate: (AergisDestination) -> Unit) {
        ProductPage(R.string.product_settings) {
            AergisPanel { HandPreferencePanel() }
            ProductLink(AergisDestination.PRECISION, stringResource(R.string.product_precision_help), navigate)
            ProductLink(AergisDestination.PERMISSIONS, stringResource(R.string.product_setup_permissions_help), navigate)
            ProductLink(AergisDestination.DIAGNOSTICS, stringResource(R.string.product_diagnostics_help), navigate)
            ProductLink(AergisDestination.WELCOME, stringResource(R.string.product_setup_start), navigate)
            ProductLink(AergisDestination.ABOUT, stringResource(R.string.app_tagline), navigate)
            // Preserve PR #68's experimental activity; no gaze runtime is embedded in this UI.
            AergisPanel {
                Text(stringResource(R.string.product_gaze_help), color = AergisColors.Muted)
                AergisButton({ startActivity(Intent(this@MainActivity, com.airgesture.control.gaze.GazeLabActivity::class.java)) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gaze_lab))
                }
            }
        }
    }

    @Composable
    private fun GesturesScreen(onEdit: (RegisteredGesture) -> Unit, navigate: (AergisDestination) -> Unit) {
        var query by rememberSaveable { mutableStateOf("") }
        var filter by rememberSaveable { mutableIntStateOf(0) }
        var presetApplied by rememberSaveable { mutableStateOf(false) }
        val missingPresetActions = remember(mappingsVersion) {
            TouchFreeNavigationPreset.missingActions(ActionMappingStore(this@MainActivity))
        }
        val missingPresetLabels = missingPresetActions.map { actionLabel(it) }.joinToString(", ")
        val rows = RegisteredGesture.entries.map { gesture ->
            Triple(gesture, stringResource(gestureLabelRes(gesture)), actionLabel(mapping(gesture, mappingsVersion)))
        }.filter { (gesture, name, action) ->
            (name.contains(query, ignoreCase = true) || action.contains(query, ignoreCase = true)) &&
                (filter == 0 || (filter == 1) == (mapping(gesture, mappingsVersion) != AirAction.NONE))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ProductHeading(R.string.product_gestures) }
            item { GestureLibraryIntro() }
            item { Text(stringResource(R.string.navigation_gesture_help), color = AergisColors.Muted) }
            item {
                AergisPanel {
                    Text(stringResource(R.string.touch_free_preset_help), color = AergisColors.Muted)
                    AergisButton(onClick = {
                        TouchFreeNavigationPreset.apply(ActionMappingStore(this@MainActivity))
                        presetApplied = true
                        mappingsVersion++
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.touch_free_preset_apply))
                    }
                    if (presetApplied) {
                        Text(
                            if (missingPresetActions.isEmpty()) stringResource(R.string.touch_free_preset_complete)
                            else stringResource(R.string.touch_free_preset_missing, missingPresetLabels),
                            color = if (missingPresetActions.isEmpty()) AergisColors.Muted else AergisColors.Caution
                        )
                    }
                }
            }
            item { ProductLink(AergisDestination.LEARN, stringResource(R.string.product_learn_help), navigate) }
            item { androidx.compose.material3.OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.product_search_gestures)) }, singleLine = true) }
            item { AergisSegmentedTabs(listOf(stringResource(R.string.product_filter_all),
                stringResource(R.string.product_filter_assigned), stringResource(R.string.product_filter_unassigned)), filter, { filter = it }) }
            if (rows.isEmpty()) item { Text(stringResource(R.string.product_no_matches), color = AergisColors.Muted) }
            rows.forEach { (gesture, _, _) -> item(key = gesture.name) { MappingRow(gesture, onEdit) } }
        }
    }

    private fun setGestureActions(enabled: Boolean) {
        gesturesEnabled = enabled
        ActionMappingStore(this).setGesturesEnabled(enabled)
        AirRuntime.gesturesEnabled = enabled
    }

    @Composable
    private fun PrecisionScreen(navigate: (AergisDestination) -> Unit) {
        ProductPage(R.string.product_precision) {
            Text(stringResource(R.string.product_pointer_explainer), color = AergisColors.Muted)
            AergisPanel {
                AergisSettingRow(stringResource(R.string.pointer_mode), stringResource(R.string.pointer_mode_description), pointerEnabled) {
                    pointerEnabled = it
                    ActionMappingStore(this@MainActivity).setPointerEnabled(it)
                    AirRuntime.pointerEnabled = it
                    AirAccessibilityService.instance?.updatePointer(AirRuntime.pointerX, AirRuntime.pointerY, it && AirRuntime.pointerTracking)
                }
                HorizontalProductDivider()
                AergisSettingRow(stringResource(R.string.pointer_clicking), stringResource(R.string.pointer_clicking_help), pointerClickingEnabled) {
                    pointerClickingEnabled = it
                    ActionMappingStore(this@MainActivity).setPointerClickingEnabled(it)
                    AirRuntime.pointerClickingEnabled = it
                    AirAccessibilityService.instance?.cancelPointerTouch()
                }
            }
            ProductLink(AergisDestination.CALIBRATION, stringResource(R.string.product_calibration_optional), navigate)
            ProductLink(AergisDestination.PRACTICE, stringResource(R.string.product_practice_help), navigate)
            ProductLink(AergisDestination.LIVE, stringResource(R.string.product_live_help), navigate)
        }
    }

    @Composable
    private fun HorizontalProductDivider() { androidx.compose.material3.HorizontalDivider(color = AergisColors.Outline) }

    @Composable
    private fun HandPreferencePanel() {
        Text(stringResource(R.string.product_select_hand), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.ui_hand_preference_description), color = AergisColors.Muted)
        ControlHandSelector(handPreference) { selected ->
            handPreference = selected
            ActionMappingStore(this@MainActivity).setHandPreference(selected)
            AirRuntime.handPreference = selected
            touchFreeCalibration.stop()
            calibrationSession.reset()
            calibrationState = calibrationSession.state()
            calibrationSampleCount = 0
            calibrationMessage = null
            refreshCalibrationSettings()
        }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @Composable
    private fun AdvancedScreen() {
        val flow = remember { AirRuntime.uiState.sample(250L) }
        val runtime by flow.collectAsStateWithLifecycle(initialValue = AirRuntime.uiStateSnapshot())
        ProductPage(R.string.product_advanced) {
            DiagnosticsCard(runtime)
            DiagnosticFilterPanel()
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
            CalibrationStepPreview(calibrationSampleCount, capturing)
            if (capturing) Text(stringResource(R.string.calibration_touch_free), color = AergisColors.Muted)
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
                        dispatch.policyDenied,
                        dispatch.submitted,
                        dispatch.platformUnavailable,
                        dispatch.executionFailed
                    )
                )
                Text(stringResource(R.string.diagnostics_privacy_note))
            }
        }
    }

    @Composable
    private fun MappingRow(source: RegisteredGesture, onEdit: (RegisteredGesture) -> Unit) {
        val current = mapping(source, mappingsVersion)
        val restriction = if (current != AirAction.NONE &&
            !GestureActionPolicy.isClassifierActionEnabled(true, current, pointerEnabled, source))
            stringResource(R.string.ui_mapping_pointer_restriction) else null
        GestureMappingRow(source, stringResource(gestureLabelRes(source)), actionLabel(current),
            stringResource(gestureHelpRes(source)), restriction) { onEdit(source) }
    }

    companion object {
        const val ACTION_OPEN_CALIBRATION = "com.airgesture.control.OPEN_CALIBRATION"
    }
}
