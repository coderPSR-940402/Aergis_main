package com.airgesture.control

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Two-page phone-test summary. Exact frame data travels in the accompanying ZIP. */
internal object DiagnosticReport {
    fun write(file: File, metadata: JSONObject, summary: JSONObject) {
        val doc = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            fun page(number: Int, content: (android.graphics.Canvas) -> Unit) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
                page.canvas.drawColor(Color.WHITE)
                try {
                    content(page.canvas)
                    paint.color = Color.GRAY; paint.textSize = 9f
                    page.canvas.drawText("Aergis testing report  |  ${file.parentFile?.name}  |  ${number}/3", 36f, 812f, paint)
                } finally { doc.finishPage(page) }
            }
            fun text(c: android.graphics.Canvas, value: String, x: Float, y: Float, size: Float = 11f, bold: Boolean = false) {
                paint.color = Color.rgb(25, 35, 50); paint.textSize = size
                paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                c.drawText(value, x, y, paint)
            }
            fun wrap(c: android.graphics.Canvas, value: String, y: Float): Float {
                paint.textSize = 10f; paint.typeface = Typeface.DEFAULT
                var line = ""; var position = y
                for (word in value.replace("\n", " ").split(' ')) {
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(candidate) > 515f && line.isNotEmpty()) {
                        text(c, line, 36f, position, 10f); position += 15f; line = word
                    } else line = candidate
                    // Break encoded configuration strings that have no word boundaries.
                    while (paint.measureText(line) > 515f) {
                        var end = line.length
                        while (end > 1 && paint.measureText(line.take(end)) > 515f) end--
                        text(c, line.take(end), 36f, position, 10f); position += 15f; line = line.drop(end)
                    }
                }
                if (line.isNotEmpty()) { text(c, line, 36f, position, 10f); position += 15f }
                return position
            }
            fun decimal(key: String) = String.format(Locale.US, "%.1f", summary.optDouble(key))
            page(1) { c ->
                text(c, "Aergis tracking test", 36f, 58f, 24f, true)
                text(c, metadata.optString("startedAt", "Synthetic / unspecified date"), 36f, 82f, 10f)
                text(c, "Duration: ${summary.optLong("durationMs") / 1000.0} s   |   Stop: ${summary.optString("stopReason")}", 36f, 109f)
                text(c, "${summary.optInt("frames")} frames   |   ${decimal("trackingPercent")}% tracked   |   ${summary.optInt("cameraSamples")} camera samples", 36f, 140f, 14f, true)
                text(c, "Inference: ${decimal("meanInferenceMs")} ms average / ${summary.optLong("maximumInferenceMs")} ms max", 36f, 163f)
                text(c, "Largest analyzed-frame gap: ${summary.optLong("maximumFrameGapMs")} ms", 36f, 182f)
                text(c, "Recorder drops: ${summary.optLong("droppedRecords")}   |   Camera sample failures: ${summary.optLong("imageFailures")}", 36f, 201f)
                text(c, "Pointer feedback", 36f, 238f, 14f, true)
                var y = 261f
                val reasons = summary.getJSONObject("reasons")
                for (reason in reasons.keys().asSequence().sorted()) {
                    text(c, reason.replace('_', ' '), 36f, y)
                    text(c, reasons.getInt(reason).toString(), 330f, y)
                    y += 19f
                }
                if (reasons.length() == 0) text(c, "No analyzed frames were recorded.", 36f, y)
                text(c, "Screen coverage while tracking", 36f, 460f, 14f, true)
                val cells = summary.getJSONArray("heatmap")
                val peak = (0 until cells.length()).maxOfOrNull { cells.optInt(it) }?.coerceAtLeast(1) ?: 1
                val left = 36f; val top = 485f; val cell = 23f
                for (row in 0..9) for (col in 0..9) {
                    val fraction = cells.optInt(row * 10 + col).toFloat() / peak
                    paint.color = if (fraction == 0f) Color.rgb(237, 241, 245) else Color.rgb((185 - 145 * fraction).toInt(), (222 - 95 * fraction).toInt(), 210)
                    c.drawRect(left + col * cell, top + row * cell, left + (col + 1) * cell - 1, top + (row + 1) * cell - 1, paint)
                }
                text(c, "Top of screen", 289f, 503f)
                text(c, "Bottom of screen", 289f, 711f)
                wrap(c, "Darker cells contain more tracked pointer samples. Empty cells can mean the area was not visited; this chart alone does not prove a detection fault.", 755f)
            }
            page(2) { c ->
                text(c, "Configuration and recording scope", 36f, 58f, 21f, true)
                var y = 91f
                val keys = listOf("appVersion", "device", "android", "startedAt", "handPreference", "orientation", "calibration",
                    "model", "imageSamplingFps", "previewFps", "maximumDurationMs", "maximumFrames")
                for (key in keys) y = wrap(c, "$key: ${metadata.opt(key) ?: "unspecified"}", y) + 5f
                y += 13f
                text(c, "Final frame state", 36f, y, 14f, true); y += 24f
                val frame = summary.optJSONObject("lastFrame")
                for (key in listOf("reason", "controlMode", "motion", "foregroundSafety", "handPreference", "selectedHandIndex", "commandOwnerId", "calibration", "pose")) {
                    if (frame?.has(key) == true) y = wrap(c, "$key: ${frame.opt(key)}", y) + 3f
                }
                val error = summary.optString("writeError").takeIf { it != "null" && it.isNotBlank() }
                if (error != null) y = wrap(c, "Recorder error (logs retained): $error", y + 8f)
                wrap(c, "Scope: all frames delivered to the analysis callback are eligible for telemetry recording. CameraX may discard camera frames before analysis. Camera JPEGs are sampled at up to 2 fps; this is not full-rate video. Recorder queue losses are counted above. Coordinates and images are local until you choose Share or Save. The ZIP includes metadata.json, summary.json, frames.jsonl, events.jsonl, images and this PDF.", maxOf(y + 20f, 671f))
            }
            page(3) { c ->
                text(c, "Pointer lineage comparison", 36f, 58f, 21f, true)
                var y = wrap(c, "APK source: ${metadata.optString("sourceCommit", "unspecified")}", 89f)
                y = wrap(c, "Historical reference: ${metadata.optString("historicalSource", "unspecified")}", y + 4f)
                y = wrap(c, "Filter at start: ${metadata.optString("filterMode", "CURRENT")}. Both algorithms consume the same input. Units: fraction of screen, not pixels. Jitter requires a labelled stationary segment at one fixed target.", y + 9f)
                y = wrap(c, "Visibility interruptions: ${summary.optInt("visibilityInterruptions")}. Pointer rejections: ${summary.optJSONObject("pointerRejections") ?: "none"}", y + 9f)
                val comparisons = summary.optJSONObject("lineageComparison")
                for (segment in comparisons?.keys()?.asSequence()?.sorted().orEmpty()) {
                    text(c, segment, 36f, y + 19f, 12f, true); y += 28f
                    val metrics = comparisons!!.getJSONObject(segment)
                    for (mode in listOf("current", "vc49")) {
                        val m = metrics.getJSONObject(mode)
                        val error = String.format(Locale.US, "%.5f", m.optDouble("meanDistanceToMeasurement"))
                        val jitter = if (m.optInt("stationarySamples") == 0) "unlabelled" else
                            String.format(Locale.US, "%.5f", m.optDouble("stationaryJitterRms"))
                        text(c, "$mode: ${m.optInt("samples")} samples / jitter $jitter / mean distance $error", 36f, y, 10f)
                        y += 14f
                    }
                }
                wrap(c, "The ZIP contains exact per-frame candidate outputs and overlay/action events. Lower distance to measured landmarks is not automatically better. These metrics cannot establish end-to-end camera/display latency or replace physical-device testing.", maxOf(y + 23f, 705f))
            }
            file.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
    }
}
