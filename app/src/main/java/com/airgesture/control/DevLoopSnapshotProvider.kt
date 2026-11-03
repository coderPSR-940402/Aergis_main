package com.airgesture.control

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Read-only redacted evidence through Toolbox's existing authorized Shizuku shell. */
class DevLoopSnapshotProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val caller = Binder.getCallingUid()
        if (caller != Process.myUid() && caller % 100000 != 2000) throw SecurityException("Shell or app UID required")
        require(uri.path == "/snapshot") { "Unknown evidence view" }
        val payload = AergisDevLoop.readEvidence { state -> snapshot(uri, state) }
        return MatrixCursor(arrayOf("payload")).apply {
            addRow(arrayOf(Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)))
        }
    }

    private fun snapshot(uri: Uri, captureState: String): JSONObject {
        val root = File(requireNotNull(context).filesDir, "devloop")
        fun read(file: File): JSONObject = runCatching {
            require(file.length() <= 16 * 1024 * 1024)
            JSONObject(file.readText())
        }.getOrElse { JSONObject() }
        val offset = uri.getQueryParameter("offset")?.toIntOrNull() ?: 0
        val expected = uri.getQueryParameter("snapshot")
        if (expected != null) {
            require(expected.matches(Regex("[a-f0-9]{64}"))) { "Invalid snapshot identity" }
            val frozen = read(findEvidenceFile(File(root, "exports"), expected))
            if (!frozen.has("snapshot_id")) return JSONObject().put("schema", 2).put("state", "snapshot_expired")
            val requestedId = uri.getQueryParameter("session_id")
            require(requestedId == null || requestedId == frozen.optString("session_id"))
            require((uri.getQueryParameter("after_seq")?.toLongOrNull() ?: 0L) == frozen.optLong("after_seq"))
            return page(frozen, offset)
        }
        val current = read(File(root, "session.json"))
        val id = uri.getQueryParameter("session_id") ?: current.optString("session_id")
        require(id.isEmpty() || id.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}"))) { "Invalid session identity" }
        val manifest = if (id == current.optString("session_id")) current
            else read(findEvidenceFile(File(root, "sessions"), id))
        fun recover(value: JSONObject): JSONObject {
            if (value.optString("state") == "recording" &&
                (value.optString("session_id") != current.optString("session_id") || captureState == "inactive")) {
                value.put("state", "unclean").put("clean", false)
                    .put("seq", value.optLong("seq") + 1)
                    .put("reason_code", "capture_inactive_without_terminal")
                // Discovery time is deliberately not represented as the session's end time.
            }
            return value
        }
        recover(manifest)
        val history = JSONArray()
        File(root, "sessions").listFiles()?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }?.take(10)?.reversed()?.forEach { history.put(recover(read(it))) }
        if (history.length() == 0 && manifest.has("session_id")) history.put(manifest)
        val traceFile = findEvidenceFile(File(root, "traces"), id)
        val trace = if (traceFile.isFile) read(traceFile) else read(File(root, "trace.json")).let {
            if (it.optString("session_id") == id) it else JSONObject()
        }
        val after = uri.getQueryParameter("after_seq")?.toLongOrNull() ?: 0L
        require(after >= 0)
        val records = trace.optJSONArray("records") ?: JSONArray()
        val rows = (0 until records.length()).map { records.getJSONObject(it) }
            .filter { it.optLong("seq") > after && it.optLong("seq") <= manifest.optLong("seq", Long.MAX_VALUE) }
        val fingerprint = manifest.toString() + trace.toString() + after + captureState
        val revision = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val result = JSONObject().put("schema", 2).put("capture_state", captureState)
            .put("manifest", manifest).put("session_history", history).put("snapshot_id", revision)
            .put("trace_truncated", trace.optBoolean("truncated", true)).put("total_records", rows.size)
            .put("session_id", id).put("after_seq", after).put("records", JSONArray(rows))
        // An active session can checkpoint every five seconds while a large replay is paged.
        // Freeze this revision on disk so a slow shell cannot continually restart at page one.
        val exports = File(root, "exports").apply { mkdirs() }
        val target = File(exports, "$revision.json")
        if (!target.isFile) {
            val temporary = File(exports, ".$revision.tmp")
            temporary.writeText(result.toString())
            java.nio.file.Files.move(temporary.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        exports.listFiles()?.filter { it.extension == "json" }?.sortedBy { it.lastModified() }
            ?.dropLast(16)?.forEach { it.delete() }
        return page(result, offset)
    }

    private fun page(snapshot: JSONObject, offset: Int): JSONObject {
        val rows = snapshot.getJSONArray("records")
        require(offset in 0..rows.length()) { "Invalid evidence offset" }
        val result = JSONObject(snapshot.toString()).apply { remove("records") }
        val page = JSONArray()
        for (index in offset until minOf(offset + 24, rows.length())) {
            val row = rows.getJSONObject(index)
            if (result.toString().length + page.toString().length + row.toString().length > 22000) break
            page.put(row)
        }
        val end = offset + page.length()
        return result.put("records", page).put("offset", offset).put("next_offset", if (end < rows.length()) end else JSONObject.NULL)
    }

    /** Resolve only an existing direct JSON child; caller-controlled text never becomes a path. */
    private fun findEvidenceFile(directory: File, identity: String): File =
        directory.listFiles()?.firstOrNull { file ->
            file.isFile && file.extension == "json" && file.nameWithoutExtension == identity &&
                file.canonicalFile.parentFile == directory.canonicalFile
        } ?: File(directory, ".missing")

    override fun getType(uri: Uri) = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri = throw SecurityException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw SecurityException("Read only")
}
