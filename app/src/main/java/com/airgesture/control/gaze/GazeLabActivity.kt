package com.airgesture.control.gaze

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.airgesture.control.AirRuntime
import com.airgesture.control.BuildConfig
import com.airgesture.control.ControlMode
import com.airgesture.control.GestureCaptureService
import com.airgesture.control.ui.*
import java.util.Locale

/**
 * Optional, on-device gaze calibration and held-out validation.
 * Does not submit input, touch, selection, or AccessibilityService actions.
 */
class GazeLabActivity : ComponentActivity() {
    private val fit = GazeCalibration()
    private val samples = ArrayList<GazeCalibration.Sample>()
    private val recent = ArrayList<GazeLab.Observation>()
    private val errors = ArrayList<GazeValidation.Error?>()
    private val targets = listOf(.15f, .5f, .85f).flatMap { y ->
        listOf(.15f, .5f, .85f).map { x -> x to y }
    }
    private val validationTargets = GazeValidation.targets
    private var calibrationCount by mutableIntStateOf(0)
    private var validationIndex by mutableIntStateOf(0)
    private var calibrated by mutableStateOf(false)
    private var complete by mutableStateOf(false)
    private var automatic by mutableStateOf(true)
    private var estimate by mutableStateOf<Pair<Float, Float>?>(null)
    private var statusText by mutableStateOf("Start camera capture and look at the yellow target.")
    private var observedCount by mutableIntStateOf(0)
    private var resultText by mutableStateOf<String?>(null)
    private var cameraStarted by mutableStateOf(false)
    private var targetShownAt = 0L
    private var lastSample = 0L
    private var active = false
    private var generation = 0L
    private lateinit var canvasView: View

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else statusText = "Camera permission is needed for local gaze sampling."
    }

    private val staleGuard = object : Runnable {
        override fun run() {
            if (!active) return
            if (SystemClock.uptimeMillis() - lastSample > 350L) {
                estimate = null
                recent.clear()
                observedCount = 0
                if (!complete) statusText = "Waiting for fresh face/eye observations."
                canvasView.invalidate()
            }
            canvasView.postDelayed(this, 150L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The lab never inherits permission to issue a gesture or click.
        AirRuntime.controlMode = ControlMode.READY
        cameraStarted = AirRuntime.running
        canvasView = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.rgb(16, 18, 28))
                val cx = width / 2f
                val cy = height / 2f
                paint.color = Color.rgb(56, 61, 78)
                paint.strokeWidth = 1.2f * resources.displayMetrics.density
                canvas.drawLine(cx, 0f, cx, height.toFloat(), paint)
                canvas.drawLine(0f, cy, width.toFloat(), cy, paint)
                val target = if (calibrated) validationTargets.getOrNull(validationIndex)?.let { it.x to it.y }
                    else targets.getOrNull(calibrationCount)
                target?.let {
                    paint.color = Color.rgb(246, 223, 112)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f * resources.displayMetrics.density
                    val x = it.first * width
                    val y = it.second * height
                    canvas.drawCircle(x, y, 15f * resources.displayMetrics.density, paint)
                    canvas.drawCircle(x, y, 5f * resources.displayMetrics.density, paint)
                    paint.style = Paint.Style.FILL
                }
                estimate?.let {
                    paint.color = Color.rgb(158, 215, 236)
                    canvas.drawCircle(it.first * width, it.second * height,
                        8f * resources.displayMetrics.density, paint)
                }
            }
        }
        setContent { AergisTheme { LabScreen() } }
    }

    @Composable
    private fun LabScreen() {
        Column(Modifier.fillMaxSize().background(AergisColors.Background).safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("GAZE LAB", color = AergisColors.Yellow, style = AergisType.Brand)
                    Text("Eyes aim. Gestures remain in control.", color = AergisColors.Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = ::finish) { Text("Back") }
            }
            AergisStatusChip(when {
                complete -> "Validation complete"
                calibrated -> "Validation " + (validationIndex + 1) + "/" + validationTargets.size
                else -> "Calibration " + (calibrationCount + 1) + "/9"
            }, if (complete) AergisColors.Success else AergisColors.Purple)
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = AergisColors.Text)
            if (!cameraStarted) {
                AergisButton(::startCamera, Modifier.fillMaxWidth(), primary = true) {
                    Text("Start camera for Gaze Lab")
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = AergisShapes.Card, color = AergisColors.Deep,
                border = BorderStroke(1.dp, AergisColors.Outline)
            ) {
                AndroidView(factory = { canvasView }, modifier = Modifier.fillMaxSize())
            }
            if (complete) {
                Text(resultText ?: "Validation completed", color = AergisColors.Cyan,
                    style = MaterialTheme.typography.bodyMedium)
                AergisButton(::copyResults, Modifier.fillMaxWidth(), primary = true) {
                    Text("Copy accuracy report")
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AergisButton({ capture(manual = true) }, Modifier.weight(1f)) {
                        Text(if (calibrated) "Validate point" else "Capture target")
                    }
                    AergisButton({ automatic = !automatic }, Modifier.weight(1f)) {
                        Text(if (automatic) "Auto: ON" else "Auto: OFF")
                    }
                }
                Text("Fresh stable eye frames: " + observedCount + "/5. " +
                    "Auto capture waits for a steady gaze. Yellow is the target, cyan is the estimate.",
                    color = AergisColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AergisButton(::resetLab, Modifier.weight(1f)) { Text("Restart calibration") }
                AergisButton(::finish, Modifier.weight(1f)) { Text("Leave lab") }
            }
            Text("Experimental accuracy assessment only. Local face frames are not saved. " +
                "No gaze clicks, pointer movement or Android actions.",
                color = AergisColors.Muted, style = MaterialTheme.typography.labelSmall)
        }
    }

    private fun copyResults() {
        if (!complete || errors.size != validationTargets.size) return
        val details = errors.mapIndexed { index, error ->
            val label = validationTargets[index].label
            if (error == null) "$label: unavailable"
            else String.format(Locale.US, "%s: %.1f%% (dx=%+.0fpx, dy=%+.0fpx)",
                label, error.diagonalPercent, error.horizontalPx, error.verticalPx)
        }.joinToString("; ")
        val report = "Aergis Gaze Lab | build " + BuildConfig.VERSION_CODE +
            " | source " + BuildConfig.SOURCE_COMMIT +
            " | metric v2: pixel-weighted full-screen diagonal | " + (resultText ?: "") +
            " | held-out " + details + " | positive dx=right, positive dy=down"
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Aergis gaze validation", report))
        statusText = "Accuracy report copied. No face images or landmarks included."
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }
        AirRuntime.controlMode = ControlMode.READY
        runCatching {
            if (!AirRuntime.running) ContextCompat.startForegroundService(
                this, Intent(this, GestureCaptureService::class.java))
        }.onSuccess {
            cameraStarted = true
            statusText = "Camera starting. Keep your head comfortably still and watch the yellow target."
        }.onFailure {
            statusText = "Could not start camera: " + (it.message ?: "Open Control to check camera setup.")
        }
    }

    private fun resetLab() {
        fit.fit(emptyList())
        samples.clear()
        recent.clear()
        errors.clear()
        calibrationCount = 0
        validationIndex = 0
        calibrated = false
        complete = false
        estimate = null
        observedCount = 0
        resultText = null
        lastSample = 0L
        targetShownAt = SystemClock.uptimeMillis()
        statusText = "Look at the first yellow target. Keep your head comfortable."
        canvasView.invalidate()
    }

    private fun capture(manual: Boolean) {
        if (complete || !active || !AirRuntime.running || canvasView.width <= 0 || canvasView.height <= 0) {
            if (manual) statusText = "Start camera capture and wait for the target to appear."
            return
        }
        val now = SystemClock.uptimeMillis()
        val values = GazeCapturePolicy.stableMedian(recent, now, targetShownAt)
        if (values == null) {
            if (manual) statusText = "Need five distinct, steady, recent eye samples. Look at the yellow target."
            return
        }
        val location = IntArray(2)
        canvasView.getLocationOnScreen(location)
        val display = realDisplaySize()
        val target = if (calibrated) validationTargets[validationIndex].let { it.x to it.y }
            else targets[calibrationCount]
        val x = ((location[0] + target.first * canvasView.width) / display.first).coerceIn(0f, 1f)
        val y = ((location[1] + target.second * canvasView.height) / display.second).coerceIn(0f, 1f)
        if (calibrated) {
            val predicted = fit.estimate(values)
            // A rejected estimate is a measured failure, not an invitation to retry until
            // this target happens to succeed. Keep the original target index in the report.
            val error = predicted?.let {
                GazeValidation.measure(it.first, it.second, x, y, display.first, display.second)
            }
            if (predicted != null && error == null) {
                statusText = "Invalid display geometry; validation not captured."
                return
            }
            val targetLabel = validationTargets[validationIndex].label
            errors.add(error)
            validationIndex++
            if (validationIndex == validationTargets.size) {
                val summary = GazeValidation.summarizeAvailable(errors)
                complete = true
                resultText = if (summary.measurableCount == 0) {
                    "Held-out 0/${errors.size} measurable; all estimates unavailable. " +
                        "No accuracy median can be reported. Not a device-control certification."
                } else String.format(Locale.getDefault(),
                    "Held-out %d/%d measurable, %d unavailable. Median of measured %.1f%% " +
                        "of screen pixel diagonal; worst %.1f%% at %s. " +
                        "Canvas validation only; not a device-control accuracy certification.",
                    summary.measurableCount, errors.size, summary.unavailableCount,
                    summary.medianPercent!!, summary.worstPercent!!,
                    validationTargets[summary.worstIndex!!].label)
                statusText = "Validation finished. Gaze does not yet control Android."
            } else statusText = (if (error == null) "No estimate at $targetLabel; recorded as unavailable. "
                else "Validated $targetLabel. ") + "Next: " +
                validationTargets[validationIndex].label + "."
        } else {
            samples.add(GazeCalibration.Sample(values, x.toDouble(), y.toDouble()))
            calibrationCount = samples.size
            if (samples.size == targets.size) {
                calibrated = fit.fit(samples)
                statusText = if (calibrated)
                    "Fit ready. Validate nine new canvas positions, including edges and center."
                else "Fit was unreliable. Restart with steady eyes and better lighting."
                if (!calibrated) {
                    samples.clear()
                    calibrationCount = 0
                }
            } else statusText = "Captured. Look at target " + (calibrationCount + 1) + " of 9."
        }
        recent.clear()
        observedCount = 0
        targetShownAt = now
        canvasView.invalidate()
    }

    @Suppress("DEPRECATION")
    private fun realDisplaySize(): Pair<Float, Float> {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels.toFloat().coerceAtLeast(1f) to
            metrics.heightPixels.toFloat().coerceAtLeast(1f)
    }

    override fun onResume() {
        super.onResume()
        AirRuntime.controlMode = ControlMode.READY
        active = true
        cameraStarted = AirRuntime.running
        targetShownAt = SystemClock.uptimeMillis()
        val token = ++generation
        GazeLab.start(this) { observation -> runOnUiThread {
            if (!active || token != generation) return@runOnUiThread
            lastSample = observation.uptimeMs
            if (observation.features == null) {
                recent.clear()
                observedCount = 0
                estimate = null
                if (!complete) statusText = observation.message
            } else {
                recent.add(observation)
                while (recent.size > 8) recent.removeAt(0)
                observedCount = recent.count { it.uptimeMs >= targetShownAt }
                val full = if (calibrated) fit.estimate(observation.features) else null
                val location = IntArray(2)
                canvasView.getLocationOnScreen(location)
                val display = realDisplaySize()
                estimate = full?.let {
                    ((it.first * display.first - location[0]) / canvasView.width.coerceAtLeast(1)) to
                    ((it.second * display.second - location[1]) / canvasView.height.coerceAtLeast(1))
                }
                if (!complete && automatic && GazeCapturePolicy.automaticCapture(
                        recent, SystemClock.uptimeMillis(), targetShownAt) != null) {
                    capture(manual = false)
                } else if (!complete) statusText = if (calibrated)
                    "Look at " + validationTargets[validationIndex].label +
                        " validation target. " + observedCount + " recent samples."
                else "Look steadily at target " + (calibrationCount + 1) + "/9."
            }
            canvasView.invalidate()
        } }
        canvasView.post(staleGuard)
    }

    override fun onPause() {
        active = false
        generation++
        canvasView.removeCallbacks(staleGuard)
        GazeLab.stop()
        recent.clear()
        estimate = null
        observedCount = 0
        super.onPause()
    }
}
