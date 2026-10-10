package com.airgesture.control

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.airgesture.control.filtering.Point3D
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

internal enum class PointerTestSegment { UNLABELLED, STATIONARY, TRAVEL, FAST, REACQUIRE, EDGES, CLICK }
internal enum class RecordingStatus { IDLE, RECORDING, EXPORTING, READY, ERROR }
internal data class TestingState(val mirror: Boolean = false, val filterMode: PointerFilterMode = PointerFilterMode.PRECISION, val segment: PointerTestSegment = PointerTestSegment.UNLABELLED, val status: RecordingStatus = RecordingStatus.IDLE,
    val frames: Int = 0, val dropped: Long = 0, val message: String? = null, val export: DiagnosticExport? = null,
    val recordings: List<SavedRecording> = emptyList(), val canRetry: Boolean = false)
internal data class MirrorFrame(val bitmap: Bitmap?, val hands: List<List<Point3D>>, val label: String,
    val selectedHandIndex: Int? = null, val detail: String = "", val timestampMs: Long = 0L,
    val calibration: PointerCalibrationProfile? = null) {
    fun isLive(nowMs: Long): Boolean = bitmap != null && nowMs - timestampMs in 0L..1000L
}

/** Opt-in local testing only. A bounded queue keeps disk and PDF work off the vision thread. */
internal object TestingTools {
    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor()
    private val capacity = Semaphore(32)
    private val _state = MutableStateFlow(TestingState())
    val state = _state.asStateFlow()
    private val _mirrorFrame = MutableStateFlow<MirrorFrame?>(null)
    val mirrorFrame = _mirrorFrame.asStateFlow()
    private var session: DiagnosticSession? = null
    private var failedSession: DiagnosticSession? = null
    private var startedAt = 0L
    @Volatile private var lastPreview = Long.MIN_VALUE
    private var lastImage = Long.MIN_VALUE
    private var accepted = 0
    private val dropped = AtomicLong()
    private val imageFailures = AtomicLong()
    private const val MAX_DURATION_MS = 600_000L
    private const val MAX_FRAMES = 18_000

    fun needsFrames(): Boolean = state.value.mirror || state.value.status == RecordingStatus.RECORDING

    fun setMirror(enabled: Boolean) = synchronized(lock) {
        _state.value = _state.value.copy(mirror = enabled)
        lastPreview = Long.MIN_VALUE
        if (!enabled) _mirrorFrame.value = null
    }

    fun setFilterMode(mode: PointerFilterMode) = synchronized(lock) {
        if (_state.value.filterMode != mode) AirRuntime.invalidatePendingActions()
        _state.value = _state.value.copy(filterMode = mode)
        event("filter_mode", JSONObject().put("mode", mode.name))
    }

    fun nextSegment() = synchronized(lock) {
        val entries = PointerTestSegment.entries
        _state.value = _state.value.copy(segment = entries[(state.value.segment.ordinal + 1) % entries.size])
        event("test_segment", JSONObject().put("segment", state.value.segment.name))
    }

    fun start(context: Context) = synchronized(lock) {
        if (!AirRuntime.running || !AirRuntime.cameraReady || state.value.status in listOf(RecordingStatus.RECORDING, RecordingStatus.EXPORTING)) return@synchronized
        val app = context.applicationContext
        runCatching {
            val root = File(app.filesDir, "testing-recordings").apply { mkdirs() }
            // Recordings are calibration evidence. Never silently delete previous or failed sessions.
            val now = System.currentTimeMillis()
            val directory = File(root, SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(now)) +
                "-" + java.util.UUID.randomUUID().toString().take(8))
            startedAt = SystemClock.uptimeMillis()
            val runtime = AirRuntime.state.value
            @Suppress("DEPRECATION")
            val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
            val landscape = app.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val calibration = PointerCalibrationStore(app).activeProfile(runtime.handPreference, landscape)
            val metadata = JSONObject().put("schema", 1).put("startedAtMs", startedAt)
                .put("startedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(now)))
                .put("sourceCommit", BuildConfig.SOURCE_COMMIT).put("historicalSource", "92eb773ab516df8955f987b874c6041b4d6e8cb2")
                .put("testSegment", state.value.segment.name).put("filterMode", state.value.filterMode.name).put("appVersion", version).put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("android", Build.VERSION.RELEASE).put("handPreference", runtime.handPreference.name)
                .put("orientation", if (landscape) "landscape" else "portrait")
                .put("calibration", calibration?.let(PointerCalibrationProfileCodec::encode) ?: "Default linear mapping")
                .put("model", "MediaPipe gesture_recognizer float16/1")
                .put("imageSamplingFps", 2).put("previewFps", 8).put("maximumDurationMs", MAX_DURATION_MS)
                .put("cameraSampleMaxEdge", 960).put("jpegQuality", 90)
                .put("maximumFrames", MAX_FRAMES).put("initialActions", runtime.actionDispatchTelemetry.toString())
                .put("scope", "All analyzed-frame metadata; sampled camera JPEGs, not full-rate video.")
            session = DiagnosticSession(directory, metadata, MAX_FRAMES)
            failedSession = null; accepted = 0; dropped.set(0); imageFailures.set(0); lastImage = Long.MIN_VALUE
            _state.value = _state.value.copy(status = RecordingStatus.RECORDING, frames = 0, dropped = 0,
                message = "Recording locally: frame data + camera samples (2 fps).", export = null, canRetry = false)
        }.onFailure { _state.value = _state.value.copy(status = RecordingStatus.ERROR, message = it.message ?: "Could not start recording") }
    }

    fun onFrame(context: Context, image: ImageProxy, result: GestureRecognizerResult?, timestamp: Long,
        inferenceMs: Long, trace: JSONObject?, error: String?) {
        val diagnosticStarted = SystemClock.uptimeMillis()
        val frameSession = synchronized(lock) { session }
        val recording = frameSession != null
        val previewDue = state.value.mirror && (lastPreview == Long.MIN_VALUE || timestamp - lastPreview >= 125)
        val imageDue = recording && (lastImage == Long.MIN_VALUE || timestamp - lastImage >= 500)
        var bitmap: Bitmap? = null
        var imageError: String? = null
        if (previewDue || imageDue) {
            bitmap = runCatching {
                val plane = image.planes.first()
                DiagnosticCameraImage.decodeRgba(plane.buffer, image.width, image.height, plane.rowStride,
                    plane.pixelStride, image.imageInfo.rotationDegrees, if (imageDue) 960 else 480)
            }.getOrElse {
                imageError = it.message ?: it.javaClass.simpleName
                if (imageDue) imageFailures.incrementAndGet()
                null
            }
            if (previewDue) {
                lastPreview = timestamp
                synchronized(lock) {
                    if (state.value.mirror) _mirrorFrame.value = MirrorFrame(bitmap,
                        if (bitmap == null) emptyList() else DiagnosticFrameData.mirrorHands(result, image.imageInfo.rotationDegrees),
                        when {
                            bitmap == null -> context.getString(R.string.testing_mirror_unavailable)
                            error != null -> context.getString(R.string.testing_mirror_inference_error)
                            result == null -> context.getString(R.string.testing_mirror_model_wait)
                            else -> "${AirRuntime.pointerFeedback.name.replace('_', ' ')} · ${AirRuntime.lastGesture}"
                        },
                        trace?.optInt("selectedHandIndex", -1)?.takeIf { it >= 0 },
                        imageError ?: mirrorDetail(trace), timestamp,
                        PointerCalibrationStore(context).activeProfile(AirRuntime.handPreference,
                            context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE))
                }
            }
            if (imageDue) lastImage = timestamp
        }
        if (!recording) return
        val frame = DiagnosticFrameData.encode(context, image, result, timestamp, inferenceMs, trace, error)
        frame.put("diagnosticOverheadMs", SystemClock.uptimeMillis() - diagnosticStarted)
            .put("cameraSampleRequested", imageDue).put("imageError", imageError ?: JSONObject.NULL)
        val cameraSample = if (imageDue) bitmap else null
        synchronized(lock) {
            val current = session ?: return
            if (current !== frameSession || timestamp < startedAt) return
            if (timestamp - startedAt >= MAX_DURATION_MS || accepted >= MAX_FRAMES) {
                stop("Recording limit reached")
                return
            }
            if (!capacity.tryAcquire()) { dropped.incrementAndGet(); return }
            accepted++
            writer.execute {
                try {
                    val jpeg = cameraSample?.let { sample ->
                        runCatching {
                            ByteArrayOutputStream().use { output ->
                                check(sample.compress(Bitmap.CompressFormat.JPEG, 90, output))
                                output.toByteArray()
                            }
                        }.getOrElse { imageFailures.incrementAndGet(); null }
                    }
                    current.append(frame, jpeg)
                    synchronized(lock) {
                        if (session === current) _state.value = _state.value.copy(frames = accepted, dropped = dropped.get())
                    }
                } catch (t: Exception) {
                    current.writeError = t.message ?: t.javaClass.simpleName
                    synchronized(lock) { if (session === current) stop("Recorder write error") }
                } finally { capacity.release() }
            }
        }
    }

    private fun mirrorDetail(trace: JSONObject?): String {
        fun coordinate(point: JSONObject?) = if (point == null) "—" else
            String.format(Locale.US, "%.2f,%.2f", point.optDouble("x"), point.optDouble("y"))
        val comparison = trace?.optJSONObject("comparison")
        val pointer = AirRuntime.pointerSnapshot()
        val selected = trace?.optInt("selectedHandIndex", -1)?.takeIf { it >= 0 }?.toString() ?: "—"
        val pinch = trace?.optDouble("thumbMiddleDistance")?.takeIf { it.isFinite() }
            ?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
        return "hand $selected · ${trace?.optString("pointerRejection", "waiting") ?: "waiting"}\n" +
            "raw ${coordinate(trace?.optJSONObject("uprightTip"))} map ${coordinate(comparison?.optJSONObject("mappedTip"))}\n" +
            "pinch $pinch palm · ${AirRuntime.pointerInteraction.name}\n" +
            String.format(Locale.US, "cursor %.2f,%.2f · actions %s", pointer.x, pointer.y,
                AirRuntime.gesturesEnabled && (trace?.optBoolean("commandsAllowed") == true))
    }

    fun event(kind: String, details: JSONObject) = synchronized(lock) {
        val current = session ?: return@synchronized
        if (!capacity.tryAcquire()) { dropped.incrementAndGet(); return@synchronized }
        val event = details.put("event", kind).put("timestampMs", SystemClock.uptimeMillis())
        writer.execute {
            try { current.event(event) }
            catch (t: Exception) {
                current.writeError = t.message ?: t.javaClass.simpleName
                synchronized(lock) { if (session === current) stop("Recorder write error") }
            } finally { capacity.release() }
        }
    }

    fun stop(reason: String = "User stopped") = synchronized(lock) {
        val current = session ?: return@synchronized
        session = null
        current.stopReason = reason
        _state.value = _state.value.copy(status = RecordingStatus.EXPORTING, message = "Creating PDF and ZIP…")
        export(current, SystemClock.uptimeMillis())
    }

    private fun export(current: DiagnosticSession, endedAt: Long) {
        writer.execute {
            runCatching {
                current.droppedRecords = dropped.get()
                current.imageFailures = imageFailures.get()
                current.finish(endedAt) to RecordingLibrary.list(current.directory.parentFile!!)
            }.onSuccess { (export, recordings) ->
                synchronized(lock) {
                    failedSession = null
                    _state.value = _state.value.copy(status = RecordingStatus.READY, export = export, frames = accepted,
                        dropped = dropped.get(), message = "Report ready. Save PDF, save ZIP or share.",
                        recordings = recordings, canRetry = false)
                }
            }.onFailure {
                synchronized(lock) {
                    failedSession = current
                    _state.value = _state.value.copy(status = RecordingStatus.ERROR, message = "Export failed; logs retained. ${it.message}", canRetry = true)
                }
            }
        }
    }

    fun retryExport() = synchronized(lock) {
        val current = failedSession ?: return@synchronized
        if (state.value.status == RecordingStatus.EXPORTING) return@synchronized
        _state.value = _state.value.copy(status = RecordingStatus.EXPORTING, message = "Retrying export…")
        export(current, SystemClock.uptimeMillis())
    }

    fun refreshRecordings(context: Context, restoreLatest: Boolean = false) {
        val root = File(context.applicationContext.filesDir, "testing-recordings")
        writer.execute {
            runCatching { RecordingLibrary.list(root) }.onSuccess { recordings ->
                synchronized(lock) {
                    _state.value = _state.value.copy(recordings = recordings)
                    if (restoreLatest && state.value.status == RecordingStatus.IDLE) {
                        recordings.firstOrNull { it.complete }?.let { openRecording(context, it.id) }
                    }
                }
            }.onFailure {
                synchronized(lock) { _state.value = _state.value.copy(message = "Could not read recordings: ${it.message}") }
            }
        }
    }

    fun openRecording(context: Context, id: String) = synchronized(lock) {
        if (state.value.status in listOf(RecordingStatus.RECORDING, RecordingStatus.EXPORTING)) return@synchronized
        val root = File(context.applicationContext.filesDir, "testing-recordings")
        failedSession = null
        _state.value = _state.value.copy(status = RecordingStatus.EXPORTING, export = null, frames = 0,
            dropped = 0, canRetry = false, message = "Opening recording…")
        writer.execute {
            runCatching { RecordingLibrary.open(root, id) to RecordingLibrary.list(root) }.onSuccess { (export, recordings) ->
                synchronized(lock) {
                    _state.value = _state.value.copy(status = RecordingStatus.READY, export = export,
                        recordings = recordings, message = "Recording $id ready. Save or share the report and images.")
                }
            }.onFailure {
                synchronized(lock) {
                    _state.value = _state.value.copy(status = RecordingStatus.ERROR,
                        message = "Could not open $id; original files retained. ${it.message}")
                }
            }
        }
    }

    fun restoreLatest(context: Context) = refreshRecordings(context, restoreLatest = true)
}
