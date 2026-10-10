package com.airgesture.control

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DiagnosticExport(val pdf: File, val bundle: File)

/** Writer-thread confined. Incremental logs remain available if export fails. */
internal class DiagnosticSession private constructor(val directory: File, private val metadata: JSONObject,
    private val maxFrames: Int, private val recovered: Boolean) {
    constructor(directory: File, metadata: JSONObject, maxFrames: Int) : this(directory, metadata, maxFrames, false)
    private var writer: java.io.BufferedWriter? = null
    private var events: java.io.BufferedWriter? = null
    private var closed = recovered
    private var unreadableFrameRecords = 0
    private var stoppedAtMs: Long? = null
    private var frames = 0
    private var tracked = 0
    private var images = 0
    private var firstTimestamp: Long? = null
    private var previousTimestamp: Long? = null
    private var maximumGap = 0L
    private var inferenceTotal = 0L
    private var inferenceMax = 0L
    private var lastFrame: JSONObject? = null
    private val reasons = linkedMapOf<String, Int>()
    private val lineage = linkedMapOf<String, MutableMap<String, PointerLineageMetrics>>()
    private var comparisonContext: List<String?>? = null
    private var comparisonTimestamp: Long? = null
    private var comparisonRun: String? = null
    private val runCounts = mutableMapOf<String, Int>()
    private val pointerRejections = linkedMapOf<String, Int>()
    private var lastVisible: Boolean? = null
    private var visibilityInterruptions = 0
    private val heatmap = IntArray(100)
    var droppedRecords = 0L
    var imageFailures = 0L
    var stopReason = "User stopped"
    var writeError: String? = null

    init {
        require(maxFrames > 0)
        check(directory.mkdirs() || directory.isDirectory)
        if (!recovered) {
            check(!File(directory, "metadata.json").exists() && !File(directory, "frames.jsonl").exists()) {
                "Recording directory already contains evidence"
            }
            File(directory, "metadata.json").writeText(metadata.toString(2))
            writer = File(directory, "frames.jsonl").bufferedWriter()
            events = File(directory, "events.jsonl").bufferedWriter()
        }
    }

    fun append(frame: JSONObject, jpeg: ByteArray?): Boolean {
        check(!closed) { "Recording already stopped" }
        if (frames >= maxFrames) return false
        if (jpeg != null) {
            val filename = "images/frame-${frame.getLong("timestampMs")}.jpg"
            val image = File(directory, filename)
            image.parentFile?.mkdirs()
            image.writeBytes(jpeg)
            frame.put("image", filename)
        }
        writer!!.apply { write(frame.toString()); newLine(); flush() }
        accumulate(frame)
        return true
    }

    private fun accumulate(frame: JSONObject) {
        val timestamp = frame.getLong("timestampMs")
        val image = frame.optString("image")
        if (image.matches(Regex("images/frame-[0-9]+\\.jpg")) && File(directory, image).isFile) images++
        if (firstTimestamp == null) firstTimestamp = timestamp
        previousTimestamp?.let { maximumGap = maxOf(maximumGap, timestamp - it) }
        previousTimestamp = timestamp
        val inference = frame.optLong("inferenceMs").coerceAtLeast(0L)
        inferenceTotal += inference
        inferenceMax = maxOf(inferenceMax, inference)
        val reason = frame.optString("reason", "UNKNOWN")
        reasons[reason] = (reasons[reason] ?: 0) + 1
        val pointer = frame.optJSONObject("pointer")
        if (pointer?.optBoolean("tracking") == true) {
            tracked++
            val x = pointer.optDouble("x")
            val y = pointer.optDouble("y")
            if (x.isFinite() && y.isFinite()) heatmap[(y.coerceIn(0.0, 1.0) * 10).toInt().coerceAtMost(9) * 10 +
                (x.coerceIn(0.0, 1.0) * 10).toInt().coerceAtMost(9)]++
        }
        val rejection = frame.optString("pointerRejection", "UNSPECIFIED")
        pointerRejections[rejection] = (pointerRejections[rejection] ?: 0) + 1
        if (frame.has("cursorVisible")) {
            val visible = frame.optBoolean("cursorVisible")
            if (lastVisible == true && !visible) visibilityInterruptions++
            lastVisible = visible
        }
        val comparison = frame.optJSONObject("comparison")
        fun point(name: String): PointerCoordinateMapper.Point? {
            val value = comparison?.optJSONObject(name) ?: return null
            val x = value.optDouble("x"); val y = value.optDouble("y")
            return if (value.opt("x") is Number && value.opt("y") is Number &&
                x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0) PointerCoordinateMapper.Point(x.toFloat(), y.toFloat()) else null
        }
        val measured = point("mappedTip"); val current = point("current"); val vc49 = point("vc49")
        val precision = point("precision")
        if (measured != null && current != null && vc49 != null) {
            val segment = frame.optString("testSegment", "UNLABELLED")
            val context = listOf("testSegment", "filterMode", "commandOwnerId", "calibration", "actionEpoch", "rotation")
                .map { key -> frame.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString() } + (precision != null).toString()
            val gap = comparisonTimestamp?.let { timestamp - it }
            if (comparisonRun == null || context != comparisonContext || frame.optBoolean("ownerChanged") ||
                gap == null || gap !in 1L..150L) {
                val count = (runCounts[segment] ?: 0) + 1
                runCounts[segment] = count
                comparisonRun = if (count == 1) segment else "$segment#$count"
            }
            comparisonContext = context
            comparisonTimestamp = timestamp
            val metrics = lineage.getOrPut(comparisonRun!!) { linkedMapOf() }
            for ((mode, value) in listOf("current" to current, "vc49" to vc49, "precision" to precision)) {
                if (value != null) metrics.getOrPut(mode) { PointerLineageMetrics() }.sample(measured, value, segment == "STATIONARY")
            }
        } else {
            comparisonRun = null
        }
        frames++
        lastFrame = frame
    }

    fun event(value: JSONObject) {
        check(!closed)
        events!!.apply { write(value.toString()); newLine(); flush() }
    }

    fun finish(endedAtMs: Long): DiagnosticExport {
        val stoppedAt = stoppedAtMs ?: endedAtMs.also { stoppedAtMs = it }
        if (!closed) {
            // Closing is attempted independently; a write failure cannot prevent log preservation.
            runCatching { writer?.close() }
            runCatching { events?.close() }
            writer = null
            events = null
            closed = true
        }
        val startedAt = metadata.optLong("startedAtMs", firstTimestamp ?: stoppedAt)
        val summary = JSONObject().put("frames", frames).put("tracked", tracked).put("cameraSamples", images)
            .put("durationMs", (stoppedAt - startedAt).coerceAtLeast(0L))
            .put("trackingPercent", if (frames == 0) 0.0 else tracked * 100.0 / frames)
            .put("maximumFrameGapMs", maximumGap).put("meanInferenceMs", if (frames == 0) 0.0 else inferenceTotal.toDouble() / frames)
            .put("maximumInferenceMs", inferenceMax).put("reasons", JSONObject(reasons as Map<*, *>))
            .put("heatmap", JSONArray(heatmap.toList())).put("droppedRecords", if (recovered) JSONObject.NULL else droppedRecords)
            .put("imageFailures", if (recovered) JSONObject.NULL else imageFailures).put("stopReason", stopReason)
            .put("recovered", recovered).put("unreadableFrameRecords", unreadableFrameRecords)
            .put("writeError", writeError ?: JSONObject.NULL).put("lastFrame", lastFrame ?: JSONObject.NULL)
        val comparisons = JSONObject()
        lineage.forEach { (segment, metrics) ->
            val modes = JSONObject()
            metrics.forEach { (mode, values) -> modes.put(mode, JSONObject(values.snapshot() as Map<*, *>)) }
            comparisons.put(segment, modes)
        }
        summary.put("lineageComparison", comparisons).put("visibilityInterruptions", visibilityInterruptions)
            .put("pointerRejections", JSONObject(pointerRejections as Map<*, *>))
        File(directory, "summary.json").writeText(summary.toString(2))
        val pdf = File(directory, "report.pdf")
        DiagnosticReport.write(pdf, metadata, summary)
        val bundle = File(directory, "recording.zip")
        val temporary = File(directory, "recording.zip.partial")
        ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
            directory.walkTopDown().filter { it.isFile && it != bundle && it != temporary }.forEach { file ->
                zip.putNextEntry(ZipEntry(file.relativeTo(directory).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        check(temporary.renameTo(bundle)) { "Could not finalize recording ZIP" }
        return DiagnosticExport(pdf, bundle)
    }

    companion object {
        /** Rebuild derived reports only. Original metadata, logs and images are never opened for writing. */
        @JvmStatic
        fun recover(directory: File): DiagnosticSession {
            val metadata = JSONObject(File(directory, "metadata.json").readText())
            val session = DiagnosticSession(directory, metadata, 18_000, true)
            session.stopReason = "Recovered interrupted recording"
            var lastTimestamp = metadata.optLong("startedAtMs", 0L)
            File(directory, "frames.jsonl").useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    val frame = runCatching {
                        val value = JSONObject(line)
                        val time = value.opt("timestampMs")
                        require(time is Number && time.toDouble().isFinite() && time.toDouble() >= 0 &&
                            time.toDouble() == time.toLong().toDouble())
                        require(session.frames < session.maxFrames)
                        value
                    }.getOrNull()
                    if (frame == null) {
                        session.unreadableFrameRecords++
                        session.comparisonRun = null
                    } else {
                        session.accumulate(frame)
                        lastTimestamp = maxOf(lastTimestamp, frame.getLong("timestampMs"))
                    }
                }
            }
            // Uptime from a later process or reboot cannot measure this session's duration.
            session.stoppedAtMs = lastTimestamp
            session.writeError = "Recovered from retained logs; queue losses and image failures are unknown. " +
                "Unreadable frame records: ${session.unreadableFrameRecords}. Raw evidence is preserved."
            return session
        }
    }

}
