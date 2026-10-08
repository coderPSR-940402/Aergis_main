package com.airgesture.control

import java.io.File

internal data class SavedRecording(val id: String, val complete: Boolean)

/** Local evidence catalog. Called on the recording worker, never on the camera/UI thread. */
internal object RecordingLibrary {
    fun list(root: File): List<SavedRecording> = root.listFiles().orEmpty()
        .filter { it.isDirectory && (File(it, "frames.jsonl").isFile || complete(it)) }
        .sortedByDescending { it.name }
        .map { SavedRecording(it.name, complete(it)) }

    fun open(root: File, id: String): DiagnosticExport {
        require(id.isNotBlank() && id == File(id).name && id != "." && id != "..") { "Invalid recording ID" }
        val directory = File(root, id)
        require(directory.canonicalFile.parentFile == root.canonicalFile && directory.isDirectory) { "Recording not found" }
        return if (complete(directory)) DiagnosticExport(File(directory, "report.pdf"), File(directory, "recording.zip"))
        else DiagnosticSession.recover(directory).finish(0L)
    }

    private fun complete(directory: File): Boolean = listOf("recording.zip", "report.pdf").all {
        File(directory, it).let { file -> file.isFile && file.length() > 0 }
    }
}
