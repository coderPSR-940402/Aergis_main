package com.airgesture.control

import android.Manifest
import android.os.Looper
import android.view.Display
import android.view.Surface
import android.hardware.display.DisplayManager
import androidx.camera.core.ImageAnalysis
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GestureCaptureLifecycleTest {
    @Test fun stopQueuesNativeDisposalAfterInferenceWithoutInterruptingIt() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildService(GestureCaptureService::class.java).create()
        val service = controller.get()
        val executorField = field("executor")
        (executorField.get(service) as java.util.concurrent.ExecutorService).shutdown()
        val executor = QueuedExecutor()
        executorField.set(service, executor)
        val engine = field("visionEngine").get(service) as GestureRecognitionEngine
        var inferenceCompleted = false
        executor.execute { inferenceCompleted = true }
        controller.destroy()
        assertTrue(executor.isShutdown)
        assertFalse("Do not interrupt native inference", executor.interrupted)
        assertEquals("Disposal must follow the pending inference", 2, executor.tasks.size)
        assertFalse(AirRuntime.running)
        assertFalse(AirRuntime.visionReady)
        // Simulate a new session starting before the old executor finishes draining.
        AirRuntime.visionReady = true
        AirRuntime.setPointerState(.25f, .75f, true)
        executor.tasks.removeFirst().run()
        assertTrue(inferenceCompleted)
        executor.tasks.removeFirst().run()
        assertTrue("Old disposal must not reset the new session", AirRuntime.visionReady)
        assertEquals(.25f, AirRuntime.pointerX, 0f)
        assertTrue(AirRuntime.pointerTracking)
        engine.close() // Idempotent native cleanup must also preserve the new session.
        assertTrue(AirRuntime.pointerTracking)
        AirRuntime.visionReady = false
        AirRuntime.setPointerState(.5f, .5f, false)
    }

    @Test fun displayRotationUpdatesTheAnalysisUseCaseAndInvalidatesPendingActions() {
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildService(GestureCaptureService::class.java).create()
        val service = controller.get()
        val useCase = ImageAnalysis.Builder().setTargetRotation(Surface.ROTATION_0).build()
        field("analysis").set(service, useCase)
        try {
            val display = service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            shadowOf(display).setRotation(Surface.ROTATION_90)
            val epoch = AirRuntime.actionEpoch
            val listener = field("displayListener").get(service) as DisplayManager.DisplayListener
            listener.onDisplayChanged(Display.DEFAULT_DISPLAY)
            assertEquals(Surface.ROTATION_90, useCase.targetRotation)
            assertTrue(AirRuntime.actionEpoch > epoch)
            shadowOf(display).setRotation(Surface.ROTATION_180)
            listener.onDisplayChanged(Display.DEFAULT_DISPLAY)
            assertEquals("180-degree changes also need camera rotation", Surface.ROTATION_180, useCase.targetRotation)
        } finally {
            controller.destroy()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun field(name: String) = GestureCaptureService::class.java.getDeclaredField(name).apply { isAccessible = true }

    private class QueuedExecutor : AbstractExecutorService() {
        val tasks = ArrayDeque<Runnable>()
        var interrupted = false
        private var stopped = false
        override fun execute(command: Runnable) { check(!stopped); tasks.addLast(command) }
        override fun shutdown() { stopped = true }
        override fun shutdownNow(): MutableList<Runnable> { interrupted = true; stopped = true; return tasks.toMutableList() }
        override fun isShutdown() = stopped
        override fun isTerminated() = stopped && tasks.isEmpty()
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
    }
}
