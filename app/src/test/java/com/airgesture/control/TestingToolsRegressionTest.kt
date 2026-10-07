package com.airgesture.control

import android.graphics.Bitmap
import android.graphics.RectF
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TestingToolsRegressionTest {
    // Reflection lets the initial regression run fail as an assertion when the feature is absent.
    private fun type(name: String): Class<*> {
        val value = runCatching { Class.forName("com.airgesture.control.$name") }.getOrNull()
        assertNotNull("Missing testing feature: $name", value)
        return value!!
    }
    private fun session(dir: File, limit: Int = 18000): Any =
        type("DiagnosticSession").getConstructor(File::class.java, JSONObject::class.java, Int::class.javaPrimitiveType)
            .newInstance(dir, JSONObject().put("startedAtMs", 1000L).put("imageSamplingFps", 2), limit)
    private fun append(session: Any, frame: JSONObject, jpeg: ByteArray? = null): Boolean =
        session.javaClass.getMethod("append", JSONObject::class.java, ByteArray::class.java).invoke(session, frame, jpeg) as Boolean
    private fun finish(session: Any): Any =
        session.javaClass.getMethod("finish", Long::class.javaPrimitiveType).invoke(session, 1200L)
    private fun frame(time: Long, reason: String, tracking: Boolean): JSONObject =
        JSONObject().put("timestampMs", time).put("reason", reason).put("inferenceMs", 12)
            .put("hands", org.json.JSONArray()).put("pointer", JSONObject().put("x", .5).put("y", .8).put("tracking", tracking))

    @Test fun noHandFramesAndCameraSamplesSurvivePdfAndZipExport() {
        val dir = File("build/reports/testing-tools/sample").apply { deleteRecursively(); mkdirs() }
        val recording = session(dir)
        assertTrue(append(recording, frame(1000, "TRACKING", true), byteArrayOf(1, 2, 3)))
        assertTrue(append(recording, frame(1033, "NO_HAND", false)))
        val export = finish(recording)
        val pdf = export.javaClass.getMethod("getPdf").invoke(export) as File
        val zip = export.javaClass.getMethod("getBundle").invoke(export) as File
        assertTrue(pdf.length() > 500)
        assertEquals("%PDF", pdf.inputStream().use { String(it.readNBytes(4)) })
        ZipFile(zip).use {
            val lines = it.getInputStream(it.getEntry("frames.jsonl")).bufferedReader().readLines()
            assertEquals(2, lines.size)
            assertEquals("NO_HAND", JSONObject(lines[1]).getString("reason"))
            assertNotNull(it.getEntry("images/frame-1000.jpg"))
            assertNotNull(it.getEntry("report.pdf"))
            val summary = JSONObject(it.getInputStream(it.getEntry("summary.json")).bufferedReader().readText())
            assertEquals(2, summary.getInt("frames"))
            assertEquals(1, summary.getInt("tracked"))
            assertEquals(1, summary.getJSONObject("reasons").getInt("NO_HAND"))
        }
    }

    @Test fun recordingLimitRejectsAdditionalFramesWithoutChangingSavedCount() {
        val dir = java.nio.file.Files.createTempDirectory("bounded-recording").toFile()
        try {
            val recording = session(dir, 1)
            assertTrue(append(recording, frame(1000, "TRACKING", true)))
            assertFalse(append(recording, frame(1033, "NO_HAND", false)))
            finish(recording)
            assertEquals(1, File(dir, "frames.jsonl").readLines().size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun emptyRecordingStillProducesReadableExport() {
        val dir = java.nio.file.Files.createTempDirectory("empty-recording").toFile()
        try {
            finish(session(dir))
            val summary = JSONObject(File(dir, "summary.json").readText())
            assertEquals(0, summary.getInt("frames"))
            assertEquals(0.0, summary.getDouble("trackingPercent"), 0.0)
            assertTrue(File(dir, "report.pdf").length() > 500)
        } finally { dir.deleteRecursively() }
    }

    @Test fun mirrorFitsWholeImageWithoutCropping() {
        val cls = type("MirrorGeometry")
        val rect = cls.getMethod("imageRect", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(cls.getField("INSTANCE").get(null), 200, 200, 100, 200) as RectF
        assertEquals(RectF(50f, 0f, 150f, 200f), rect)
    }

    @Test fun paddedRgbaRowsRotateClockwiseAndMirrorOnce() {
        val cls = type("DiagnosticCameraImage")
        val bytes = byteArrayOf(-1,0,0,-1, 0,-1,0,-1, 9,9,9,9, 0,0,-1,-1, -1,-1,-1,-1, 9,9,9,9)
        val bitmap = cls.getMethod("decodeRgba", ByteBuffer::class.java, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(cls.getField("INSTANCE").get(null), ByteBuffer.wrap(bytes), 2, 2, 12, 4, 90, 320) as Bitmap
        // [red green; blue white] -> clockwise [blue red; white green] -> mirror [red blue; green white]
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(0, 0))
        assertEquals(android.graphics.Color.BLUE, bitmap.getPixel(1, 0))
        assertEquals(android.graphics.Color.GREEN, bitmap.getPixel(0, 1))
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(1, 1))
    }
}
