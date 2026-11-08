package com.airgesture.control.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airgesture.control.AirRuntime
import com.airgesture.control.ControlMode
import com.airgesture.control.PointerCalibrationProfile
import com.airgesture.control.PointerCoordinateMapper
import com.airgesture.control.R

@Composable
internal fun CalibrationPreview(rawCoordinates: Boolean, profile: PointerCalibrationProfile? = null) {
    val preview = AirRuntime.state.collectAsStateWithLifecycle()
    val description = stringResource(if (rawCoordinates) R.string.calibration_raw_preview else R.string.ui_pointer_preview)
    Canvas(Modifier.fillMaxWidth().height(160.dp).clip(AergisShapes.Control)
        .background(AergisColors.Background).semantics { contentDescription = description }) {
        for (i in 1..3) {
            drawLine(AergisColors.Outline.copy(alpha = .4f), Offset(size.width * i / 4, 0f), Offset(size.width * i / 4, size.height))
            drawLine(AergisColors.Outline.copy(alpha = .4f), Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4))
        }
        // Read state in the draw phase: coordinate changes redraw only this preview.
        val state = preview.value
        val raw = state.rawPointer
        if (state.running && state.cameraReady && raw.tracking &&
            (rawCoordinates || state.controlMode == ControlMode.READY)) {
            val mapped = when {
                rawCoordinates -> PointerCoordinateMapper.Point(1f - raw.x, raw.y)
                profile != null -> PointerCoordinateMapper.map(raw.x, raw.y, profile)
                else -> PointerCoordinateMapper.map(raw.x, raw.y)
            }
            val center = Offset(mapped.x * size.width, mapped.y * size.height)
            val color = AergisColors.Cyan
            drawCircle(color.copy(alpha = .2f), 14.dp.toPx(), center)
            drawCircle(color, 9.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            drawCircle(color, 4.dp.toPx(), center)
        }
    }
}
