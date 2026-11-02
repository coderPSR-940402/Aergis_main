package com.airgesture.control

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.ImageAnalysis

/** Native CameraX conversion avoids the per-pixel Kotlin YUV conversion on analyzed frames. */
internal object CameraLatencyPolicy {
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun builder(noiseModes: IntArray?, edgeModes: IntArray?): ImageAnalysis.Builder {
        val builder = ImageAnalysis.Builder().setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        val interop = Camera2Interop.Extender(builder)
        // Tracking consumes the sensor geometry, not a sharpened/denoised photograph.
        // Avoid optional ISP work when OFF is advertised; otherwise FAST is required
        // not to reduce capture rate. High-quality processing may buffer exposures.
        // Unsupported modes remain camera-controlled; never invent a camera request.
        if (noiseModes?.contains(CaptureRequest.NOISE_REDUCTION_MODE_OFF) == true)
            interop.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        else if (noiseModes?.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST) == true)
            interop.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_FAST)
        if (edgeModes?.contains(CaptureRequest.EDGE_MODE_OFF) == true)
            interop.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        else if (edgeModes?.contains(CaptureRequest.EDGE_MODE_FAST) == true)
            interop.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
        return builder
    }
}
