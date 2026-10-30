package com.airgesture.control

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.impl.Camera2ImplConfig
import androidx.camera.core.ImageAnalysis
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@android.annotation.SuppressLint("RestrictedApi")
class CameraLatencyPolicyTest {
    @Test fun advertisedOffModesAvoidOptionalIspWork() {
        val analysis = CameraLatencyPolicy.builder(intArrayOf(0, 1, 2), intArrayOf(0, 1, 2)).build()
        assertEquals(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888, analysis.outputImageFormat)
        val requests = Camera2ImplConfig(analysis.currentConfig).captureRequestOptions
        assertEquals(0, requests.getCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE))
        assertEquals(0, requests.getCaptureRequestOption(CaptureRequest.EDGE_MODE))
    }

    @Test fun fastFallbackStillAvoidsHighQualityProcessingWhenOffIsUnavailable() {
        val analysis = CameraLatencyPolicy.builder(intArrayOf(1, 2), intArrayOf(1, 2)).build()
        val requests = Camera2ImplConfig(analysis.currentConfig).captureRequestOptions
        assertEquals(1, requests.getCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE))
        assertEquals(1, requests.getCaptureRequestOption(CaptureRequest.EDGE_MODE))
    }

    @Test fun unavailableFastModesLeaveDeviceDefaultsIntact() {
        val analysis = CameraLatencyPolicy.builder(null, intArrayOf(2)).build()
        val requests = Camera2ImplConfig(analysis.currentConfig).captureRequestOptions
        assertNull(requests.getCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE))
        assertNull(requests.getCaptureRequestOption(CaptureRequest.EDGE_MODE))
    }
}
