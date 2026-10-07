package com.airgesture.control

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class DiagnosticExport(val pdf: File, val bundle: File)

/** Writer-thread confined. Incremental logs remain available if export fails. */
internal class DiagnosticSession(val directory: File, private val metadata: JSONObject, private val maxFrames: Int) {
    private var writer: java.io.BufferedWriter? = null
    private var events: java.io.BufferedWriter? = null
    private var closed = false
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
    private val heatmap = IntArray(100)
    var droppedRecords = 0L
    var imageFailures = 0L
    var stopReason = "User stopped"
    var writeError: String? = null

    init {
        require(maxFrames > 0)
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "metadata.json").writeText(metadata.toString(2))
        writer = File(directory, "frames.jsonl").bufferedWriter()
        events = File(directory, "events.jsonl").bufferedWriter()
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
            images++
        }
        writer!!.apply { write(frame.toString()); newLine(); flush() }
        val timestamp = frame.getLong("timestampMs")
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
        frames++
        lastFrame = frame
        return true
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
            .put("heatmap", JSONArray(heatmap.toList())).put("droppedRecords", droppedRecords)
            .put("imageFailures", imageFailures).put("stopReason", stopReason)
            .put("writeError", writeError ?: JSONObject.NULL).put("lastFrame", lastFrame ?: JSONObject.NULL)
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
}
