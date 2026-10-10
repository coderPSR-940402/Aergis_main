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
@Config(sdk = [35], shadows = [TestPdfDocumentShadow::class])
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

    @Test fun candidateOutputsAndRejectionsSurviveSummaryExport() {
        val dir = java.nio.file.Files.createTempDirectory("lineage-recording").toFile()
        try {
            val recording = session(dir)
            fun p(x: Double) = JSONObject().put("x", x).put("y", .5)
            for (i in 0..2) {
                append(recording, frame(1000L + i * 33, "TRACKING", true)
                    .put("testSegment", "STATIONARY").put("cursorVisible", true).put("pointerRejection", "NONE")
                    .put("comparison", JSONObject().put("mappedTip", p(.5)).put("current", p(.5 + .002 * i)).put("vc49", p(.5 + .001 * i))))
            }
            append(recording, frame(1131L, "NO_HAND", false).put("cursorVisible", false).put("pointerRejection", "RAW_LANDMARK_DROPOUT"))
            finish(recording)
            val summary = JSONObject(File(dir, "summary.json").readText())
            assertEquals(1, summary.getInt("visibilityInterruptions"))
            assertEquals(1, summary.getJSONObject("pointerRejections").getInt("RAW_LANDMARK_DROPOUT"))
            val stationary = summary.getJSONObject("lineageComparison").getJSONObject("STATIONARY")
            assertEquals(3, stationary.getJSONObject("vc49").getInt("samples"))
            assertTrue(stationary.getJSONObject("current").getDouble("stationaryJitterRms") >
                stationary.getJSONObject("vc49").getDouble("stationaryJitterRms"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun separatedStationaryRunsNeverCreateArtificialJitter() {
        val dir = java.nio.file.Files.createTempDirectory("separate-runs").toFile()
        try {
            val recording = session(dir)
            fun compared(time: Long, x: Double, segment: String): JSONObject {
                val p = JSONObject().put("x", x).put("y", .5)
                return frame(time, "TRACKING", true).put("testSegment", segment)
                    .put("comparison", JSONObject().put("mappedTip", p).put("current", p).put("vc49", p))
            }
            append(recording, compared(1000, .2, "STATIONARY"))
            append(recording, compared(1033, .2, "STATIONARY"))
            append(recording, compared(1066, .5, "TRAVEL"))
            append(recording, compared(1099, .8, "STATIONARY"))
            append(recording, compared(1132, .8, "STATIONARY"))
            finish(recording)
            val runs = JSONObject(File(dir, "summary.json").readText()).getJSONObject("lineageComparison")
            assertEquals(3, runs.length())
            for (key in listOf("STATIONARY", "STATIONARY#2")) {
                assertEquals(0.0, runs.getJSONObject(key).getJSONObject("current").getDouble("stationaryJitterRms"), 0.0)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun recoveringInterruptedRecordingPreservesRawEvidenceAndReportsPartialLine() {
        val dir = java.nio.file.Files.createTempDirectory("recover-recording").toFile()
        try {
            val metadata = "{\"startedAtMs\":1000,\"maximumFrames\":18000}"
            File(dir, "metadata.json").writeText(metadata)
            File(dir, "images").mkdir()
            File(dir, "images/frame-1000.jpg").writeBytes(byteArrayOf(1, 2, 3))
            val raw = frame(1000, "TRACKING", true).put("image", "images/frame-1000.jpg").toString() + "\n" +
                frame(1033, "NO_HAND", false).toString() + "\n{\"timestampMs\":"
            File(dir, "frames.jsonl").writeText(raw)
            File(dir, "events.jsonl").writeText("{\"event\":\"test\"}\n")
            val method = runCatching { type("DiagnosticSession").getMethod("recover", File::class.java) }.getOrNull()
            assertNotNull("Interrupted recordings need recovery after process death", method)
            val recovered = method!!.invoke(null, dir)!!
            finish(recovered)
            assertEquals(raw, File(dir, "frames.jsonl").readText())
            assertEquals(metadata, File(dir, "metadata.json").readText())
            val summary = JSONObject(File(dir, "summary.json").readText())
            assertEquals(2, summary.getInt("frames"))
            assertEquals(33L, summary.getLong("durationMs"))
            assertEquals(1, summary.getInt("cameraSamples"))
            assertEquals(1, summary.getInt("unreadableFrameRecords"))
            assertTrue(summary.getBoolean("recovered"))
            assertTrue(summary.isNull("droppedRecords"))
            ZipFile(File(dir, "recording.zip")).use { zip ->
                assertEquals(raw, zip.getInputStream(zip.getEntry("frames.jsonl")).bufferedReader().readText())
                assertNotNull(zip.getEntry("images/frame-1000.jpg"))
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun libraryFindsOlderAndInterruptedRecordingsAndOpensChosenEvidence() {
        val root = java.nio.file.Files.createTempDirectory("recording-library").toFile()
        try {
            val old = File(root, "20260101")
            val current = session(old)
            append(current, frame(1000, "NO_HAND", false))
            finish(current)
            val interrupted = File(root, "20260201").apply { mkdirs() }
            File(interrupted, "metadata.json").writeText("{\"startedAtMs\":1000}")
            File(interrupted, "frames.jsonl").writeText(frame(1033, "TRACKING", true).toString() + "\n")
            assertEquals(listOf(SavedRecording("20260201", false), SavedRecording("20260101", true)), RecordingLibrary.list(root))
            val oldBytes = File(old, "recording.zip").readBytes()
            assertArrayEquals(oldBytes, RecordingLibrary.open(root, "20260101").bundle.readBytes())
            val recovered = RecordingLibrary.open(root, "20260201")
            assertTrue(recovered.bundle.length() > 0)
            assertTrue(RecordingLibrary.list(root).all { it.complete })
            assertArrayEquals(oldBytes, File(old, "recording.zip").readBytes())
            assertThrows(IllegalArgumentException::class.java) { RecordingLibrary.open(root, "../elsewhere") }
            assertThrows(IllegalStateException::class.java) { DiagnosticSession(old, JSONObject(), 10) }
        } finally { root.deleteRecursively() }
    }

    @Test fun openingOlderRecordingCannotInterruptActiveCapture() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val root = File(context.filesDir, "testing-recordings").apply { deleteRecursively(); mkdirs() }
        val old = File(root, "older")
        finish(session(old))
        AirRuntime.running = true; AirRuntime.cameraReady = true
        try {
            TestingTools.start(context)
            assertEquals(RecordingStatus.RECORDING, TestingTools.state.value.status)
            TestingTools.openRecording(context, "older")
            assertEquals(RecordingStatus.RECORDING, TestingTools.state.value.status)
            assertNull(TestingTools.state.value.export)
            TestingTools.stop()
            fun awaitExport() {
                val deadline = System.nanoTime() + 10_000_000_000L
                while (TestingTools.state.value.status == RecordingStatus.EXPORTING && System.nanoTime() < deadline) Thread.sleep(10)
                assertEquals(RecordingStatus.READY, TestingTools.state.value.status)
            }
            awaitExport()
            TestingTools.openRecording(context, "older")
            awaitExport()
            assertEquals(old, TestingTools.state.value.export!!.bundle.parentFile)
            assertEquals(0, TestingTools.state.value.frames)
        } finally {
            TestingTools.stop()
            AirRuntime.running = false; AirRuntime.cameraReady = false
        }
    }

    @Test fun comparisonRunsSplitAtMissingFramesAndEveryContextReset() {
        val changes = listOf("filterMode" to "VC49", "commandOwnerId" to "new-owner", "calibration" to "new",
            "actionEpoch" to 2, "rotation" to 90, "ownerChanged" to true, "timestampMs" to 1500L,
            "timestampMs" to 1000L, "comparison" to JSONObject.NULL)
        for ((key, value) in changes) {
            val dir = java.nio.file.Files.createTempDirectory("reset-run").toFile()
            try {
                val recording = session(dir)
                fun compared(t: Long, x: Double): JSONObject {
                    val p = JSONObject().put("x", x).put("y", .5)
                    return frame(t, "TRACKING", true).put("testSegment", "STATIONARY")
                        .put("comparison", JSONObject().put("mappedTip", p).put("current", p).put("vc49", p))
                }
                append(recording, compared(1000, .2))
                append(recording, compared(1033, .8).put(key, value))
                if (key == "comparison") append(recording, compared(1066, .8))
                finish(recording)
                val runs = JSONObject(File(dir, "summary.json").readText()).getJSONObject("lineageComparison")
                assertEquals(key, 2, runs.length())
                for (run in runs.keys()) assertEquals(key, 0.0,
                    runs.getJSONObject(run).getJSONObject("current").getDouble("pathLength"), 0.0)
            } finally { dir.deleteRecursively() }
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

    @Test fun retryingExportKeepsOriginalRecordingDuration() {
        val dir = java.nio.file.Files.createTempDirectory("retry-recording").toFile()
        try {
            val recording = session(dir)
            append(recording, frame(1000, "NO_HAND", false))
            finish(recording)
            recording.javaClass.getMethod("finish", Long::class.javaPrimitiveType).invoke(recording, 1800L)
            assertEquals(200L, JSONObject(File(dir, "summary.json").readText()).getLong("durationMs"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun movedMirrorStaysReachableAfterScreenRotation() {
        val cls = type("MirrorGeometry")
        val method = runCatching { cls.getMethod("windowRect", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) }.getOrNull()
        assertNotNull("Mirror needs reachable bounds after rotation", method)
        val rect = method!!.invoke(cls.getField("INSTANCE").get(null), 800, 900, 200, 276, 700, 400) as android.graphics.Rect
        assertEquals(android.graphics.Rect(500, 124, 700, 400), rect)
    }

    @Test fun stopWaitsForFramesAndEventsAndRejectsLaterFrames() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        File(context.filesDir, "testing-recordings").deleteRecursively()
        AirRuntime.running = true; AirRuntime.cameraReady = true
        AirRuntime.pointerFeedback = PointerFeedback.NO_HAND
        val cls = type("TestingTools")
        val tools = cls.getField("INSTANCE").get(null)
        cls.getMethod("start", android.content.Context::class.java).invoke(tools, context)
        val proxy = image(640, 360)
        val timestamp = android.os.SystemClock.uptimeMillis()
        val onFrame = cls.getMethod("onFrame", android.content.Context::class.java, androidx.camera.core.ImageProxy::class.java,
            com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult::class.java,
            Long::class.javaPrimitiveType, Long::class.javaPrimitiveType, JSONObject::class.java, String::class.java)
        onFrame.invoke(tools, context, proxy, emptyResult(timestamp), timestamp, 12L, null, null)
        cls.getMethod("event", String::class.java, JSONObject::class.java).invoke(tools, "action_outcome", JSONObject().put("outcome", "CANCELLED"))
        cls.getMethod("setMirror", Boolean::class.javaPrimitiveType).invoke(tools, false)
        cls.getMethod("stop", String::class.java).invoke(tools, "User stopped")
        onFrame.invoke(tools, context, proxy, null, timestamp + 33, 12L, null, null)
        val state = cls.getMethod("getState").invoke(tools) as kotlinx.coroutines.flow.StateFlow<*>
        val deadline = System.nanoTime() + 10_000_000_000L
        while (state.value!!.javaClass.getMethod("getStatus").invoke(state.value).toString() == "EXPORTING" && System.nanoTime() < deadline) Thread.sleep(10)
        assertEquals("READY", state.value!!.javaClass.getMethod("getStatus").invoke(state.value).toString())
        val export = state.value!!.javaClass.getMethod("getExport").invoke(state.value)
        val zip = export.javaClass.getMethod("getBundle").invoke(export) as File
        ZipFile(zip).use {
            val lines = it.getInputStream(it.getEntry("frames.jsonl")).bufferedReader().readLines()
            assertEquals(1, lines.size)
            assertEquals("NO_HAND", JSONObject(lines.single()).getString("reason"))
            assertNotNull(it.getEntry("images/frame-$timestamp.jpg"))
            val sample = it.getInputStream(it.getEntry("images/frame-$timestamp.jpg")).use(android.graphics.BitmapFactory::decodeStream)
            assertEquals("Recording must preserve detail beyond the old 320px preview", 640, sample!!.width)
            assertEquals(360, sample.height)
            val event = JSONObject(it.getInputStream(it.getEntry("events.jsonl")).bufferedReader().readLines().single())
            assertEquals("CANCELLED", event.getString("outcome"))
        }
        AirRuntime.running = false; AirRuntime.cameraReady = false
    }

    private fun emptyResult(timestamp: Long): com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult {
        val cls = com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult::class.java
        val factory = cls.getDeclaredMethod("create", List::class.java, List::class.java, List::class.java, List::class.java, Long::class.javaPrimitiveType)
        factory.isAccessible = true
        return factory.invoke(null, emptyList<Any>(), emptyList<Any>(), emptyList<Any>(), emptyList<Any>(), timestamp) as com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
    }

    private fun image(width: Int = 2, height: Int = 2): androidx.camera.core.ImageProxy {
        fun proxy(cls: Class<*>, values: Map<String, Any?>): Any = java.lang.reflect.Proxy.newProxyInstance(cls.classLoader, arrayOf(cls)) { _, method, _ ->
            if (values.containsKey(method.name)) values[method.name] else when (method.name) {
                "close" -> null
                else -> error("Unexpected camera method: ${method.name}")
            }
        }
        val plane = proxy(androidx.camera.core.ImageProxy.PlaneProxy::class.java, mapOf("getBuffer" to ByteBuffer.wrap(ByteArray(width * height * 4) { -1 }), "getRowStride" to width * 4, "getPixelStride" to 4))
        val info = proxy(androidx.camera.core.ImageInfo::class.java, mapOf<String, Any?>("getRotationDegrees" to 0, "getTimestamp" to 123456L))
        return proxy(androidx.camera.core.ImageProxy::class.java, mapOf("getWidth" to width, "getHeight" to height,
            "getPlanes" to arrayOf(plane as androidx.camera.core.ImageProxy.PlaneProxy), "getImageInfo" to info,
            "getCropRect" to android.graphics.Rect(0, 0, width, height))) as androidx.camera.core.ImageProxy
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

    @Test fun previewReadsWholePlaneAfterAnotherConsumerAdvancesBuffer() {
        val buffer = ByteBuffer.wrap(byteArrayOf(-1, 0, 0, -1, 0, -1, 0, -1))
        buffer.position(buffer.limit())
        val bitmap = DiagnosticCameraImage.decodeRgba(buffer, 2, 1, 8, 4, 0, 320)
        assertEquals(android.graphics.Color.GREEN, bitmap.getPixel(0, 0))
        assertEquals(android.graphics.Color.RED, bitmap.getPixel(1, 0))
        assertEquals("Preview must not mutate another consumer's position", buffer.limit(), buffer.position())
    }

    @Test fun corruptCameraPlaneReplacesOldPreviewWithVisibleError() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        TestingTools.setMirror(true)
        try {
            val image = image()
            TestingTools.onFrame(context, image, null, 1000, 0, null, null)
            assertNotNull(TestingTools.mirrorFrame.value)
            image.planes[0].buffer.limit(1)
            TestingTools.onFrame(context, image, null, 1200, 0, null, null)
            val failed = TestingTools.mirrorFrame.value
            assertNotNull("A decode failure must be visible", failed)
            assertNull("Never leave a previous image looking live", failed!!.bitmap)
            assertTrue(failed.label.contains("unavailable", ignoreCase = true))
            assertTrue(failed.detail.isNotBlank())
        } finally { TestingTools.setMirror(false) }
    }

    @Test fun startingRecordingPreservesOlderAndUnexportedSessions() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val root = File(context.filesDir, "testing-recordings").apply { deleteRecursively(); mkdirs() }
        val recordings = (0..5).map { index ->
            File(root, "valuable-$index").apply { mkdirs(); File(this, "frames.jsonl").writeText("recorded evidence"); setLastModified(index + 1L) }
        }
        AirRuntime.running = true; AirRuntime.cameraReady = true
        try {
            TestingTools.start(context)
            assertTrue("Starting a new session must not delete valuable recordings", recordings.all { File(it, "frames.jsonl").isFile })
        } finally {
            TestingTools.stop()
            val deadline = System.nanoTime() + 10_000_000_000L
            while (TestingTools.state.value.status == RecordingStatus.EXPORTING && System.nanoTime() < deadline) Thread.sleep(10)
            AirRuntime.running = false; AirRuntime.cameraReady = false
        }
    }

    @Test fun unmirroredCalibrationBoundsAreReflectedIntoTheMirror() {
        val method = runCatching { MirrorGeometry::class.java.getMethod("calibrationRect", RectF::class.java,
            PointerCalibrationProfile::class.java) }.getOrNull()
        assertNotNull("Mirror must show the actual calibrated reach", method)
        val profile = PointerCalibrationProfile(left = .1f, right = .6f, top = .2f, bottom = .8f, mirrorX = false)
        val rect = method!!.invoke(MirrorGeometry, RectF(10f, 20f, 210f, 120f), profile) as RectF
        assertEquals(90f, rect.left, .001f)
        assertEquals(190f, rect.right, .001f)
        assertEquals(40f, rect.top, .001f)
        assertEquals(100f, rect.bottom, .001f)
    }
}
