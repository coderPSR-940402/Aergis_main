package com.airgesture.control

import android.content.Context
import android.content.res.Configuration
import androidx.camera.core.ImageProxy
import com.airgesture.control.filtering.Point3D
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import org.json.JSONArray
import org.json.JSONObject

internal object DiagnosticFrameData {
    fun number(value: Float?): Any = value?.takeIf { it.isFinite() } ?: JSONObject.NULL
    fun pointer(value: PointerSnapshot) = JSONObject().put("x", number(value.x)).put("y", number(value.y)).put("tracking", value.tracking)
    fun mirrorHands(result: GestureRecognizerResult?, rotation: Int): List<List<Point3D>> =
        result?.landmarks()?.map { hand -> hand.map { lm ->
            val upright = CameraCoordinateTransform.toUpright(lm.x(), lm.y(), lm.z(), rotation)
            Point3D(1f - upright.x, upright.y, upright.z)
        } } ?: emptyList()

    fun encode(context: Context, image: ImageProxy, result: GestureRecognizerResult?, timestamp: Long,
        inferenceMs: Long, trace: JSONObject?, error: String?): JSONObject {
        val state = AirRuntime.state.value
        val rotation = image.imageInfo.rotationDegrees
        val hands = JSONArray()
        result?.landmarks()?.forEachIndexed { index, landmarks ->
            val raw = JSONArray(); val upright = JSONArray()
            landmarks.forEach { lm ->
                raw.put(JSONArray().put(number(lm.x())).put(number(lm.y())).put(number(lm.z())))
                val p = CameraCoordinateTransform.toUpright(lm.x(), lm.y(), lm.z(), rotation)
                upright.put(JSONArray().put(number(p.x)).put(number(p.y)).put(number(p.z)))
            }
            val world = JSONArray()
            result.worldLandmarks().getOrNull(index)?.forEach { lm ->
                world.put(JSONArray().put(number(lm.x())).put(number(lm.y())).put(number(lm.z())))
            }
            fun categories(values: List<com.google.mediapipe.tasks.components.containers.Category>?) =
                JSONArray().apply { values?.forEach { put(JSONObject().put("label", it.categoryName()).put("score", number(it.score()))) } }
            hands.put(JSONObject().put("rawLandmarks", raw).put("uprightLandmarks", upright).put("worldLandmarks", world)
                .put("handedness", categories(result.handedness().getOrNull(index)))
                .put("gestures", categories(result.gestures().getOrNull(index))))
        }
        val pose = state.poseEvidence?.let {
            JSONObject().put("accepted", it.accepted).put("reason", it.rejectionReason.name)
                .put("landmarks", it.landmarkCount).put("finiteLandmarks", it.finiteLandmarkCount)
                .put("palmWidth", number(it.normalizedPalmWidth)).put("indexBoneRatio", number(it.indexBoneToPalmRatio))
                .put("fingertipRatio", number(it.fingertipToPalmRatio))
        }
        val calibration = PointerCalibrationStore(context).activeProfile(state.handPreference,
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val vision = state.visionTelemetry
        val actions = state.actionDispatchTelemetry
        return (trace ?: JSONObject()).put("timestampMs", timestamp).put("cameraTimestampNs", image.imageInfo.timestamp)
            .put("resultTimestampMs", result?.timestampMs() ?: JSONObject.NULL).put("inferenceMs", inferenceMs)
            .put("width", image.width).put("height", image.height).put("rotation", rotation)
            .put("cropRect", image.cropRect.toShortString()).put("imageCoordinates", "upright + horizontal mirror; full frame, no crop")
            .put("hands", hands).put("reason", when { error != null -> "INFERENCE_ERROR"; result == null -> "MODEL_WAIT"; else -> state.pointerFeedback.name })
            .put("sourceCommit", BuildConfig.SOURCE_COMMIT).put("filterMode", TestingTools.state.value.filterMode.name).put("testSegment", TestingTools.state.value.segment.name)
            .put("error", error ?: JSONObject.NULL).put("pointer", pointer(state.pointer)).put("rawUprightTip", pointer(state.rawPointer))
            .put("handPreference", state.handPreference.name).put("controlMode", state.controlMode.name)
            .put("pointerEnabled", state.pointerEnabled).put("gesturesEnabled", state.gesturesEnabled)
            .put("motion", JSONObject().put("active", state.motionActive).put("magnitude", number(state.motionMagnitude)).put("reason", state.motionReason ?: JSONObject.NULL))
            .put("foregroundSafety", state.foregroundContext.safety.name).put("safetyReason", state.foregroundContext.reason)
            .put("pose", pose ?: JSONObject.NULL).put("actionEpoch", state.actionEpoch)
            .put("calibration", calibration?.let(PointerCalibrationProfileCodec::encode) ?: "Default linear full-frame mapping")
            .put("vision", JSONObject().put("accepted", vision.acceptedResults).put("rejected", vision.rejectedResults)
                .put("lastRejection", vision.lastRejectionReason ?: JSONObject.NULL))
            .put("actions", JSONObject().put("completed", actions.completed).put("cancelled", actions.cancelled)
                .put("policyDenied", actions.policyDenied).put("platformRejected", actions.platformRejected))
    }
}
