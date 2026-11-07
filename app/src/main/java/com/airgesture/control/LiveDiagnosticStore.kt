package com.airgesture.control

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong

/**
 * Small bounded persistence layer for promoted DevLoop evidence.
 *
 * This store is deliberately Android-free so its retention/restart behaviour is deterministic in
 * JVM tests. Callers are responsible for invoking it off latency-sensitive capture/dispatch paths.
 */
internal class LiveDiagnosticStore(
    private val rootDir: File,
    private val maxEvents: Int = 20
) : LiveDiagnosticEventSink {
    private var buildId: String? = null
    private var startedWallClockMs: Long? = null
    private var eventCount: Long = 0L
    private val sequence = AtomicLong(0L)

    init {
        require(maxEvents >= 1) { "maxEvents must be at least 1" }
    }

    @Synchronized
    override fun publish(snapshot: LiveDiagnosticSnapshot) {
        ensureDirectories()
        val json = snapshotJson(snapshot)
        val ordinal = sequence.incrementAndGet()
        val filename = "%020d-%06d.json".format(snapshot.trigger.wallClockMs, ordinal)
        atomicWrite(File(eventsDir(), filename), json)
        atomicWrite(File(rootDir, "latest.json"), json)
        eventCount += 1L
        rotateEvents()
    }

    /**
     * Opens a new diagnostic session and returns true when a previous session marker was still
     * present, which is authoritative evidence that the prior session did not stop cleanly.
     */
    @Synchronized
    fun startSession(buildId: String, startedWallClockMs: Long): Boolean {
        require(startedWallClockMs >= 0L) { "startedWallClockMs must be non-negative" }
        ensureDirectories()
        val marker = File(rootDir, OPEN_MARKER)
        val previousWasUnclean = marker.exists()
        this.buildId = buildId
        this.startedWallClockMs = startedWallClockMs
        this.eventCount = 0L
        atomicWrite(
            marker,
            "{" +
                "\"schema\":1," +
                "\"build_id\":${quote(buildId)}," +
                "\"started_wall_clock_ms\":$startedWallClockMs" +
                "}"
        )
        return previousWasUnclean
    }

    @Synchronized
    fun stopSession(clean: Boolean, stoppedWallClockMs: Long) {
        require(stoppedWallClockMs >= 0L) { "stoppedWallClockMs must be non-negative" }
        ensureDirectories()
        val currentBuild = buildId.orEmpty()
        val started = startedWallClockMs ?: stoppedWallClockMs
        val manifest = "{" +
            "\"schema\":1," +
            "\"build_id\":${quote(currentBuild)}," +
            "\"started_wall_clock_ms\":$started," +
            "\"stopped_wall_clock_ms\":$stoppedWallClockMs," +
            "\"clean\":$clean," +
            "\"event_count\":$eventCount" +
            "}"
        atomicWrite(File(rootDir, "session.json"), manifest)
        if (clean) {
            runCatching { File(rootDir, OPEN_MARKER).delete() }
        }
    }

    private fun ensureDirectories() {
        if (!rootDir.exists() && !rootDir.mkdirs() && !rootDir.isDirectory) {
            error("Unable to create DevLoop root: ${rootDir.absolutePath}")
        }
        val events = eventsDir()
        if (!events.exists() && !events.mkdirs() && !events.isDirectory) {
            error("Unable to create DevLoop events dir: ${events.absolutePath}")
        }
    }

    private fun eventsDir(): File = File(rootDir, "events")

    private fun rotateEvents() {
        val files = eventsDir()
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            .orEmpty()
        val removeCount = files.size - maxEvents
        if (removeCount <= 0) return
        files.take(removeCount).forEach { file ->
            if (!file.delete() && file.exists()) {
                error("Unable to rotate DevLoop event: ${file.absolutePath}")
            }
        }
    }

    private fun atomicWrite(target: File, content: String) {
        target.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                error("Unable to create parent: ${parent.absolutePath}")
            }
        }
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        temp.writeText(content, Charsets.UTF_8)
        try {
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun snapshotJson(snapshot: LiveDiagnosticSnapshot): String = buildString {
        append('{')
        append("\"schema\":1,")
        append("\"trigger\":")
        append(triggerJson(snapshot.trigger))
        append(",\"request_id\":")
        append(snapshot.trigger.requestId?.toString() ?: "null")
        append(",\"before\":[")
        snapshot.before.forEachIndexed { index, sample ->
            if (index > 0) append(',')
            append(sampleJson(sample))
        }
        append("],\"after\":[")
        snapshot.after.forEachIndexed { index, sample ->
            if (index > 0) append(',')
            append(sampleJson(sample))
        }
        append("]}")
    }

    private fun triggerJson(trigger: LiveDiagnosticTrigger): String = buildString {
        append('{')
        append("\"kind\":${quote(trigger.kind.name)}")
        append(",\"severity\":${quote(trigger.severity.name)}")
        append(",\"uptime_ms\":${trigger.uptimeMs}")
        append(",\"wall_clock_ms\":${trigger.wallClockMs}")
        trigger.requestId?.let { append(",\"request_id\":$it") }
        trigger.action?.let { append(",\"action\":${quote(it)}") }
        trigger.reasonCode?.let { append(",\"reason_code\":${quote(it)}") }
        trigger.sessionEpoch?.let { append(",\"session_epoch\":$it") }
        trigger.inputEpoch?.let { append(",\"input_epoch\":$it") }
        trigger.frozenTargetX?.takeIf { it.isFinite() }?.let { append(",\"target_x\":$it") }
        trigger.frozenTargetY?.takeIf { it.isFinite() }?.let { append(",\"target_y\":$it") }
        append('}')
    }

    private fun sampleJson(sample: LiveDiagnosticSample): String = buildString {
        append('{')
        append("\"uptime_ms\":${sample.uptimeMs}")
        append(",\"wall_clock_ms\":${sample.wallClockMs}")
        sample.frameId?.let { append(",\"frame_id\":$it") }
        sample.sessionEpoch?.let { append(",\"session_epoch\":$it") }
        sample.inputEpoch?.let { append(",\"input_epoch\":$it") }
        sample.requestId?.let { append(",\"request_id\":$it") }
        sample.handId?.let { append(",\"hand_id\":${quote(it)}") }
        sample.pointerX?.takeIf { it.isFinite() }?.let { append(",\"pointer_x\":$it") }
        sample.pointerY?.takeIf { it.isFinite() }?.let { append(",\"pointer_y\":$it") }
        sample.rawPointerX?.takeIf { it.isFinite() }?.let { append(",\"raw_x\":$it") }
        sample.rawPointerY?.takeIf { it.isFinite() }?.let { append(",\"raw_y\":$it") }
        sample.rawTracking?.let { append(",\"raw_tracking\":$it") }
        sample.frozenTargetX?.takeIf { it.isFinite() }?.let { append(",\"target_x\":$it") }
        sample.frozenTargetY?.takeIf { it.isFinite() }?.let { append(",\"target_y\":$it") }
        sample.gesture?.let { append(",\"gesture\":${quote(it)}") }
        sample.contactState?.let { append(",\"contact_state\":${quote(it)}") }
        sample.action?.let { append(",\"action\":${quote(it)}") }
        sample.dispatchState?.let { append(",\"dispatch_state\":${quote(it)}") }
        sample.completionState?.let { append(",\"completion_state\":${quote(it)}") }
        sample.cameraAgeMs?.let { append(",\"camera_age_ms\":$it") }
        sample.analyzedFrameGapMs?.let { append(",\"analyzed_frame_gap_ms\":$it") }
        sample.inferenceMs?.let { append(",\"inference_ms\":$it") }
        sample.sensorDeliveryMs?.let { append(",\"sensor_delivery_ms\":$it") }
        sample.workerQueueMs?.let { append(",\"worker_queue_ms\":$it") }
        append('}')
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        for (c in value) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) {
                    append("\\u")
                    append(c.code.toString(16).padStart(4, '0'))
                } else {
                    append(c)
                }
            }
        }
        append('"')
    }

    companion object {
        private const val OPEN_MARKER = "session.open"
    }
}
