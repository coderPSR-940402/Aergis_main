package com.airgesture.control

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.ceil
import kotlin.math.hypot

/** Single-writer production session. Only the diagnostic worker may call this class. */
internal class DevLoopSession(
    private val root: File,
    private val buildId: String,
    private val sessionId: String,
    private val relay: (String) -> Unit
) {
    private val store = LiveDiagnosticStore(root)
    private val buffer = LiveDiagnosticBuffer(3600, 40_000)
    private val trace = ArrayDeque<String>()
    private val frameTrace = ArrayDeque<LiveDiagnosticSample>()
    private val requests = LinkedHashMap<Long, String>()
    private val seenRequests = LinkedHashSet<Long>()
    private val counts = linkedMapOf("click_attempts" to 0L, "click_success" to 0L, "missed_clicks" to 0L,
        "action_failures" to 0L, "cancelled" to 0L, "submitted_only" to 0L, "frame_count" to 0L,
        "hand_switches" to 0L, "edge_samples" to 0L, "edge_tracked" to 0L, "trace_dropped" to 0L, "queue_dropped" to 0L,
        "contact_observation_frames" to 0L, "contact_candidates" to 0L, "contacts_linked" to 0L,
        "contacts_without_action" to 0L, "contacts_interrupted" to 0L,
        "pointer_reversals" to 0L, "slow_frame_count" to 0L)
    private val ages = ArrayDeque<Long>()
    private val inference = ArrayDeque<Long>()
    private val recovery = ArrayDeque<Long>()
    private val gaps = ArrayDeque<Long>()
    private var lastPointerDelta: Pair<Double, Double>? = null
    private var pointerStepMax: Double? = null
    private data class Contact(val id: Long, val owner: String, val epoch: Long, val input: Long,
        var frameId: Long, var linked: Boolean = false, var geometryGate: String = "", var decisionGate: String = "")
    private var contact: Contact? = null
    private var contactSequence = 0L
    private var lastContactTraceFrame: Long? = null
    private var lastOwner: String? = null
    private var lostAt: Long? = null
    private var lastFrame: LiveDiagnosticSample? = null
    private var pinchJumpMax: Double? = null
    private var startedWall = 0L
    private var lastWall = 0L
    private var lastUptime = 0L
    private var lastCheckpoint = 0L
    private var seq = 0L
    private var stopped = false
    private var evidenceComplete = true
    private var traceTruncated = false
    private var heldRelay: MutableList<String>? = null
    private var previousSessionId: String? = null
    private val diagnostics = LiveDiagnostics(buffer, LiveDiagnosticEventSink { snapshot ->
        runCatching { store.publish(snapshot) }
        val t = snapshot.trigger
        val context = (snapshot.before + snapshot.after).filter { it.frameId != null || it.handId != null || it.cameraAgeMs != null }
            .fold(mutableListOf<LiveDiagnosticSample>()) { kept, sample ->
                if (kept.lastOrNull()?.let { sample.uptimeMs - it.uptimeMs >= 100 } != false) kept.add(sample)
                kept
            }
        val contextEventSeq = seq + 1
        emit("event", linkedMapOf("event_kind" to t.kind.name, "severity" to t.severity.name,
            "event_uptime_ms" to t.uptimeMs, "wall_clock_ms" to t.wallClockMs,
            "request_id" to t.requestId, "action" to t.action, "reason_code" to t.reasonCode,
            "target_x" to t.frozenTargetX, "target_y" to t.frozenTargetY,
            "session_epoch" to t.sessionEpoch, "input_epoch" to t.inputEpoch,
            "before_count" to snapshot.before.size, "after_count" to snapshot.after.size,
            "pre_window_ms" to 30_000, "post_window_ms" to 10_000,
            "post_window_complete" to (lastUptime >= t.uptimeMs + 10_000),
            "evidence_complete" to evidenceComplete, "context_sample_count" to context.size,
            "previous_session_id" to if (t.kind == LiveDiagnosticKind.PROCESS_RESTART) previousSessionId else null))
        for (sample in context) emitSample(sample, contextEventSeq)
    }, interactionTimeoutMs = 2500, onTerminal = ::terminal)

    fun start(uptimeMs: Long, wallClockMs: Long) {
        require(sessionId.matches(Regex("[A-Za-z0-9_.-]{1,100}")))
        val previous = File(root, "session.json").takeIf { it.isFile }?.readText()
        val oldId = previous?.let {
            Regex("\"session_id\":\"([A-Za-z0-9_.-]+)\"").find(it)?.groupValues?.get(1)
        }
        // v1 only kept the active replay at trace.json. Archive it before the first
        // v2 checkpoint can replace that file with the new session's evidence.
        if (oldId != null) {
            val legacyTrace = File(root, "trace.json").takeIf { it.isFile }?.readText()
            val traceId = legacyTrace?.let {
                Regex("\"session_id\":\"([A-Za-z0-9_.-]+)\"").find(it)?.groupValues?.get(1)
            }
            if (legacyTrace != null && traceId == oldId) {
                write(File(root, "traces/$oldId.json"), legacyTrace)
            }
        }
        val unclean = store.startSession(buildId, wallClockMs)
        if (unclean && previous != null) {
            if (oldId != null) {
                previousSessionId = oldId
                val oldSeq = Regex("\"seq\":([0-9]+)").find(previous)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val recovered = previous.replace(Regex("\"state\":\"[^\"]*\""), "\"state\":\"unclean\"")
                    .replace("\"clean\":true", "\"clean\":false")
                    .replace(Regex("\"seq\":[0-9]+"), "\"seq\":${oldSeq + 1}")
                write(File(root, "sessions/$oldId.json"), recovered)
                runCatching { relay(LiveDiagnosticRelay.PREFIX + recovered) }
            }
        }
        startedWall = wallClockMs
        lastWall = wallClockMs
        lastUptime = uptimeMs
        diagnostics.sample(LiveDiagnosticSample(uptimeMs, wallClockMs))
        checkpoint(false)
        if (unclean) diagnostics.trigger(LiveDiagnosticTrigger(LiveDiagnosticKind.PROCESS_RESTART,
            LiveDiagnosticSeverity.WARNING, uptimeMs, wallClockMs, reasonCode = "unclean_prior_session"))
    }

    fun open(id: Long, uptimeMs: Long, action: String, x: Float, y: Float, epoch: Long, input: Long) {
        if (stopped || !evidenceComplete || id in seenRequests) return
        if (requests.size >= 4096) { invalidateEvidence(1); return }
        seenRequests.add(id)
        if (seenRequests.size > 4096) seenRequests.remove(seenRequests.first())
        requests[id] = action
        if (action == "TAP") increment("click_attempts")
        val matchingContact = contact?.takeIf { action == "TAP" && it.epoch == epoch && it.input == input }
        if (matchingContact != null && !matchingContact.linked) {
            matchingContact.linked = true
            increment("contacts_linked")
        }
        emit("trace", linkedMapOf("step" to "open", "uptime_ms" to uptimeMs, "request_id" to id,
            "contact_id" to matchingContact?.id,
            "action" to action, "target_x" to x, "target_y" to y, "session_epoch" to epoch, "input_epoch" to input))
        diagnostics.openInteraction(id, uptimeMs, action, x, y, epoch, input,
            completionTimeoutMs = if (action == "POINTER_DRAG") null else 2500)
    }

    /** Geometric episodes are observations, never a count of inferred user intentions. */
    fun contactGeometry(frameId: Long, uptimeMs: Long, owner: String?, epoch: Long, input: Long,
        distance: Float, depth: Float, gate: String) {
        if (stopped) return
        increment("contact_observation_frames")
        val old = contact
        if (old != null && (old.owner != owner || old.epoch != epoch || old.input != input ||
                frameId <= old.frameId || frameId - old.frameId > 350 || !distance.isFinite() || !depth.isFinite())) {
            finishContact(uptimeMs, "interrupted")
        }
        if (owner == null || !distance.isFinite() || !depth.isFinite() || distance < 0 || depth < 0) return
        if (distance >= .32f) {
            finishContact(uptimeMs, "released")
            return
        }
        if (contact == null && distance < .18f) {
            contact = Contact(++contactSequence, owner, epoch, input, frameId)
            increment("contact_candidates")
            lastContactTraceFrame = null
        }
        val active = contact ?: return
        active.frameId = frameId
        if (active.geometryGate != gate || lastContactTraceFrame?.let { frameId - it >= 100 } != false) {
            emit("trace", linkedMapOf("step" to "contact_geometry", "uptime_ms" to uptimeMs,
                "source_frame_id" to frameId, "contact_id" to active.id, "hand_id" to owner,
                "session_epoch" to epoch, "input_epoch" to input, "contact_distance" to distance,
                "contact_depth" to depth, "gate" to gate))
            lastContactTraceFrame = frameId
        }
        active.geometryGate = gate
    }

    fun contactDecision(frameId: Long, uptimeMs: Long, gate: String) {
        val active = contact?.takeIf { it.frameId == frameId } ?: return
        if (active.decisionGate == gate) return
        emit("trace", linkedMapOf("step" to "contact_decision", "uptime_ms" to uptimeMs,
            "source_frame_id" to frameId, "contact_id" to active.id, "gate" to gate))
        active.decisionGate = gate
    }

    private fun finishContact(uptimeMs: Long, reason: String) {
        val active = contact ?: return
        val outcome = when {
            active.linked -> "linked_to_request"
            reason == "released" -> "no_action_observed"
            else -> "interrupted"
        }
        if (outcome == "no_action_observed") increment("contacts_without_action")
        if (outcome == "interrupted") increment("contacts_interrupted")
        emit("trace", linkedMapOf("step" to "contact_end", "uptime_ms" to uptimeMs,
            "source_frame_id" to active.frameId, "contact_id" to active.id, "outcome" to outcome,
            "reason_code" to reason))
        contact = null
        lastContactTraceFrame = null
    }

    fun outcome(outcome: ActionDispatchOutcome, id: Long, uptimeMs: Long) {
        if (stopped || id !in requests) return
        emit("trace", linkedMapOf("step" to "outcome", "uptime_ms" to uptimeMs,
            "request_id" to id, "outcome" to outcome.name))
        if (outcome == ActionDispatchOutcome.SUBMITTED) {
            diagnostics.recordDispatch(id, uptimeMs)
            if (isSubmissionOnly(requests[id].orEmpty())) {
                diagnostics.forgetInteraction(id)
                requests.remove(id)
                increment("submitted_only")
            }
        } else {
            val terminal = when (outcome) {
                ActionDispatchOutcome.COMPLETED -> LiveInteractionTerminal.SUCCESS
                ActionDispatchOutcome.CANCELLED -> LiveInteractionTerminal.CANCELLED
                else -> LiveInteractionTerminal.FAILED
            }
            diagnostics.recordOutcome(id, terminal, uptimeMs, outcome.name.lowercase())
        }
    }

    fun sample(sample: LiveDiagnosticSample, rawX: Float? = null, rawY: Float? = null, tracking: Boolean = false) {
        if (stopped) return
        val observedConditions = mutableListOf<Pair<LiveDiagnosticKind, String>>()
        increment("frame_count")
        sample.cameraAgeMs?.let { bounded(ages, it) }
        sample.inferenceMs?.let { bounded(inference, it) }
        sample.analyzedFrameGapMs?.let { bounded(gaps, it) }
        if ((sample.cameraAgeMs ?: 0) > 100) increment("slow_frame_count")
        if ((sample.cameraAgeMs ?: 0) > 600) observedConditions += LiveDiagnosticKind.CAMERA_DELAY to "pipeline_age_above_600ms"
        if ((sample.inferenceMs ?: 0) > 250) observedConditions += LiveDiagnosticKind.INFERENCE_STALL to "inference_above_250ms"
        val old = lastFrame
        if (sample.handId != null) {
            if (lastOwner != null && lastOwner != sample.handId) increment("hand_switches")
            lastOwner = sample.handId
            lostAt?.let { bounded(recovery, (sample.uptimeMs - it).coerceAtLeast(0)); lostAt = null }
        } else if (lastOwner != null && lostAt == null) {
            lostAt = sample.uptimeMs
            observedConditions += LiveDiagnosticKind.HAND_LOST to "controlling_owner_not_observed"
        }
        if (rawX != null && rawY != null && rawX.isFinite() && rawY.isFinite() &&
            (rawX < .1f || rawX > .9f || rawY < .1f || rawY > .9f)) {
            increment("edge_samples")
            if (tracking) increment("edge_tracked")
        }
        val continuous = old != null && old.handId == sample.handId && sample.handId != null &&
            old.sessionEpoch == sample.sessionEpoch && old.inputEpoch == sample.inputEpoch &&
            sample.uptimeMs - old.uptimeMs in 1..350
        if (continuous && old != null && old.pointerX != null && old.pointerY != null &&
            sample.pointerX != null && sample.pointerY != null) {
            val dx = (sample.pointerX - old.pointerX).toDouble()
            val dy = (sample.pointerY - old.pointerY).toDouble()
            val jump = hypot(dx, dy)
            if (jump.isFinite()) {
                pointerStepMax = maxOf(pointerStepMax ?: 0.0, jump)
                val prior = lastPointerDelta
                if (sample.uptimeMs - old.uptimeMs <= 150 && jump >= .002 && prior != null && hypot(prior.first, prior.second) >= .002 &&
                    dx * prior.first + dy * prior.second < 0) increment("pointer_reversals")
                lastPointerDelta = if (sample.uptimeMs - old.uptimeMs <= 150) dx to dy else null
                if (sample.contactState in PINCH_PHASES) {
                    pinchJumpMax = maxOf(pinchJumpMax ?: 0.0, jump)
                    if (jump > .15) observedConditions += LiveDiagnosticKind.TRACKING_JUMP to "pinch_pointer_step_above_0.15"
                }
            } else lastPointerDelta = null
        } else lastPointerDelta = null
        lastFrame = sample
        if (frameTrace.lastOrNull()?.let { sample.uptimeMs - it.uptimeMs >= 100 } != false) {
            frameTrace.addLast(sample)
            if (frameTrace.size > 600) { frameTrace.removeFirst(); increment("trace_dropped") }
        }
        // Observations from different producer threads may arrive out of timestamp order.
        // Preserve their original frame clock in trace; never rewrite it to grant control authority.
        if (sample.uptimeMs >= lastUptime) {
            lastUptime = sample.uptimeMs
            lastWall = sample.wallClockMs
            diagnostics.sample(sample)
            for ((kind, reason) in observedConditions) diagnostics.trigger(LiveDiagnosticTrigger(kind,
                LiveDiagnosticSeverity.WARNING, sample.uptimeMs, sample.wallClockMs,
                reasonCode = reason, sessionEpoch = sample.sessionEpoch, inputEpoch = sample.inputEpoch))
        }
    }

    fun selectionUnconfirmed(id: Long, uptimeMs: Long, reason: String) {
        if (stopped || id !in requests) return
        emit("trace", linkedMapOf("step" to "selection_unconfirmed", "uptime_ms" to uptimeMs,
            "request_id" to id, "reason_code" to reason))
        if (reason == "no_matching_click_event")
            diagnostics.recordOutcome(id, LiveInteractionTerminal.TIMED_OUT, uptimeMs, reason)
        else diagnostics.recordOutcome(id, LiveInteractionTerminal.CANCELLED, uptimeMs, reason)
    }

    fun tick(uptimeMs: Long, wallClockMs: Long) {
        if (stopped || uptimeMs < lastUptime) return
        lastUptime = uptimeMs
        lastWall = wallClockMs
        diagnostics.sample(LiveDiagnosticSample(uptimeMs, wallClockMs))
        if (uptimeMs - lastCheckpoint >= 5000) {
            checkpoint(false)
            lastCheckpoint = uptimeMs
        }
    }

    fun checkpoint(uptimeMs: Long, wallClockMs: Long) {
        tick(uptimeMs, wallClockMs)
        if (!stopped) checkpoint(false)
    }

    fun invalidateEvidence(dropped: Long) {
        counts["queue_dropped"] = counts.getValue("queue_dropped") + dropped
        evidenceComplete = false
        finishContact(lastUptime, "observation_loss")
        // A dropped callback is missing recorder evidence, never evidence of a missed click.
        requests.keys.toList().forEach(diagnostics::forgetInteraction)
        requests.clear()
    }

    fun stop(uptimeMs: Long, wallClockMs: Long) {
        if (stopped) return
        // Persist the complete terminal snapshot before a burst can overflow logcat or lose rish.
        val finalRecords = mutableListOf<String>()
        heldRelay = finalRecords
        finishContact(uptimeMs, "capture_stopped")
        emit("trace", linkedMapOf("step" to "stop", "uptime_ms" to uptimeMs))
        diagnostics.finish(uptimeMs)
        lastUptime = maxOf(lastUptime, uptimeMs)
        lastWall = wallClockMs
        stopped = true
        for (sample in frameTrace) emitSample(sample)
        runCatching { store.stopSession(true, wallClockMs) }
        checkpoint(true)
        heldRelay = null
        for (line in finalRecords) runCatching { relay(line) }
    }

    private fun terminal(outcome: LiveInteractionOutcome) {
        requests.remove(outcome.requestId)
        when (outcome.terminal) {
            LiveInteractionTerminal.SUCCESS -> if (outcome.action == "TAP") increment("click_success")
            LiveInteractionTerminal.TIMED_OUT -> increment(if (outcome.action == "TAP") "missed_clicks" else "action_failures")
            LiveInteractionTerminal.FAILED -> increment("action_failures")
            LiveInteractionTerminal.CANCELLED -> increment("cancelled")
        }
        emit("trace", linkedMapOf("step" to "terminal", "uptime_ms" to outcome.finishedUptimeMs,
            "request_id" to outcome.requestId, "outcome" to outcome.terminal.name, "reason_code" to outcome.reason))
    }

    private fun emitSample(s: LiveDiagnosticSample, contextEventSeq: Long? = null) = emit("trace", linkedMapOf("step" to "sample",
        "context_event_seq" to contextEventSeq, "uptime_ms" to s.uptimeMs, "wall_clock_ms" to s.wallClockMs, "frame_id" to s.frameId,
        "hand_id" to s.handId, "pointer_x" to s.pointerX, "pointer_y" to s.pointerY,
        "contact_state" to s.contactState, "camera_age_ms" to s.cameraAgeMs, "inference_ms" to s.inferenceMs,
        "frame_gap_ms" to s.analyzedFrameGapMs, "session_epoch" to s.sessionEpoch, "input_epoch" to s.inputEpoch))

    private fun checkpoint(clean: Boolean) {
        val fields = linkedMapOf<String, Any?>("state" to if (clean) "stopped" else "recording", "clean" to clean,
            "started_wall_clock_ms" to startedWall, "observed_wall_clock_ms" to lastWall,
            "stopped_wall_clock_ms" to if (clean) lastWall else null)
        fields.putAll(counts)
        fields["frame_age_p95_ms"] = percentile(ages)
        fields["inference_p95_ms"] = percentile(inference)
        fields["hand_loss_recovery_p95_ms"] = percentile(recovery)
        fields["pinch_jump_max"] = pinchJumpMax
        fields["pointer_step_max"] = pointerStepMax
        fields["frame_gap_p95_ms"] = percentile(gaps)
        val json = envelope("session", fields)
        runCatching {
            val records = trace.joinToString(",")
            val traceJson = "{\"schema\":1,\"session_id\":${value(sessionId)},\"build_id\":${value(buildId)},\"truncated\":$traceTruncated,\"records\":[$records]}"
            write(File(root, "traces/$sessionId.json"), traceJson)
            write(File(root, "trace.json"), traceJson)
            write(File(root, "sessions/$sessionId.json"), json)
            write(File(root, "session.json"), json)
            File(root, "sessions").listFiles()?.filter { it.extension == "json" }?.sortedBy { it.lastModified() }
                ?.dropLast(10)?.forEach { old -> File(root, "traces/${old.name}").delete(); old.delete() }
        }
        runCatching { relay(LiveDiagnosticRelay.PREFIX + json) }
    }

    private fun emit(record: String, fields: Map<String, Any?>) {
        val json = envelope(record, fields)
        if (record == "trace" || record == "event") {
            trace.addLast(json)
            if (trace.size > 3600) { trace.removeFirst(); traceTruncated = true; increment("trace_dropped") }
        }
        val line = LiveDiagnosticRelay.PREFIX + json
        heldRelay?.add(line) ?: runCatching { relay(line) }
    }

    private fun envelope(record: String, fields: Map<String, Any?>): String {
        val all = linkedMapOf<String, Any?>("schema" to 1, "record" to record,
            "session_id" to sessionId, "build_id" to buildId, "seq" to ++seq)
        all.putAll(fields)
        return all.filterValues { it != null }.entries.joinToString(",", "{", "}") { (k, v) -> "${value(k)}:${value(v)}" }
    }
    private fun increment(key: String) { counts[key] = counts.getValue(key) + 1 }
    private fun bounded(values: ArrayDeque<Long>, v: Long) { values.addLast(v); if (values.size > 3600) values.removeFirst() }
    private fun percentile(values: ArrayDeque<Long>): Long? = values.sorted().let { if (it.isEmpty()) null else it[(ceil(it.size * .95).toInt() - 1).coerceAtLeast(0)] }

    companion object {
        internal fun isSubmissionOnly(action: String): Boolean = action in SUBMISSION_ONLY
        private val PINCH_PHASES = setOf("PINCHING", "PRESSED", "HOLDING", "DRAGGING")
        private val SUBMISSION_ONLY = setOf("VOLUME_UP", "VOLUME_DOWN", "MEDIA_PLAY_PAUSE", "MEDIA_NEXT", "MEDIA_PREVIOUS",
            "APP_SEARCH", "WEB_SEARCH", "WIFI_CONTROLS", "BLUETOOTH_SETTINGS", "CALIBRATE_POINTER", "ROTATION_TOGGLE", "SCROLL_UP", "SCROLL_DOWN",
            "FOCUS_NEXT", "FOCUS_PREVIOUS", "ACTIVATE_FOCUSED")
        private fun value(v: Any?): String = when (v) {
            null -> "null"
            is Float -> if (v.isFinite()) v.toString() else "null"
            is Double -> if (v.isFinite()) v.toString() else "null"
            is Number, is Boolean -> v.toString()
            else -> "\"" + v.toString().flatMap { c -> when(c) {
                '\\' -> "\\\\".toList(); '"' -> "\\\"".toList()
                else -> if (c.code < 32) "\\u${c.code.toString(16).padStart(4, '0')}".toList() else listOf(c)
            }}.joinToString("") + "\""
        }
        private fun write(target: File, content: String) {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, ".${target.name}.tmp")
            tmp.writeText(content)
            try { Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            finally { tmp.delete() }
        }
    }
}
