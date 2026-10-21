package com.airgesture.control.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics

internal enum class AergisGlyph { CONTROL, TRACKING, GESTURES, SETTINGS, POWER, POINTER, MOTION, CAMERA, ARROW }

@Composable
internal fun AergisIcon(glyph: AergisGlyph, modifier: Modifier, color: Color = AergisColors.Cyan) {
    Canvas(modifier.clearAndSetSemantics {}) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (glyph) {
                AergisGlyph.ARROW -> {
                    drawLine(color, Offset(4f, 12f), Offset(20f, 12f), 1.5f, StrokeCap.Round)
                    val path = Path().apply { moveTo(14f, 6f); lineTo(20f, 12f); lineTo(14f, 18f) }
                    drawPath(path, color, style = stroke)
                }
                AergisGlyph.CAMERA -> {
                    val path = Path().apply {
                        moveTo(3f, 7f); lineTo(8f, 7f); lineTo(10f, 4f); lineTo(16f, 4f)
                        lineTo(18f, 7f); lineTo(21f, 7f); lineTo(21f, 20f); lineTo(3f, 20f); close()
                    }
                    drawPath(path, color, style = stroke)
                    drawCircle(color, 4f, Offset(12f, 13f), style = stroke)
                }
                AergisGlyph.CONTROL -> {
                    drawCircle(color, 8.5f, Offset(12f, 12f), style = stroke)
                    drawCircle(color, 3f, Offset(12f, 12f), style = stroke)
                    drawLine(color, Offset(12f, 1f), Offset(12f, 5f), 1.7f)
                }
                AergisGlyph.TRACKING -> {
                    val path = Path().apply {
                        moveTo(8f, 3f); lineTo(3f, 3f); lineTo(3f, 8f)
                        moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
                        moveTo(3f, 16f); lineTo(3f, 21f); lineTo(8f, 21f)
                        moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f)
                    }
                    drawPath(path, color, style = stroke)
                    drawCircle(color, 3f, Offset(12f, 12f), style = stroke)
                }
                AergisGlyph.GESTURES -> {
                    val path = Path().apply {
                        moveTo(5f, 17f); lineTo(5f, 8f); lineTo(9f, 8f); lineTo(9f, 3f)
                        lineTo(13f, 3f); lineTo(13f, 10f); lineTo(18f, 10f)
                        lineTo(18f, 17f); lineTo(14f, 21f); lineTo(9f, 21f); close()
                    }
                    drawPath(path, color, style = stroke)
                }
                AergisGlyph.SETTINGS -> {
                    for (y in listOf(5f, 12f, 19f)) drawLine(color, Offset(3f, y), Offset(21f, y), 1.7f, StrokeCap.Round)
                    drawCircle(AergisColors.Surface, 2.6f, Offset(9f, 5f))
                    drawCircle(color, 2.6f, Offset(9f, 5f), style = stroke)
                    drawCircle(AergisColors.Surface, 2.6f, Offset(16f, 12f))
                    drawCircle(color, 2.6f, Offset(16f, 12f), style = stroke)
                    drawCircle(AergisColors.Surface, 2.6f, Offset(7f, 19f))
                    drawCircle(color, 2.6f, Offset(7f, 19f), style = stroke)
                }
                AergisGlyph.POINTER -> {
                    val path = Path().apply { moveTo(5f, 3f); lineTo(20f, 13f); lineTo(12f, 14f); lineTo(9f, 21f); close() }
                    drawPath(path, color, style = stroke)
                }
                AergisGlyph.MOTION -> {
                    val path = Path().apply {
                        moveTo(12f, 2f); lineTo(12f, 22f); moveTo(2f, 12f); lineTo(22f, 12f)
                        moveTo(8f, 6f); lineTo(12f, 2f); lineTo(16f, 6f)
                        moveTo(18f, 8f); lineTo(22f, 12f); lineTo(18f, 16f)
                        moveTo(8f, 18f); lineTo(12f, 22f); lineTo(16f, 18f)
                        moveTo(6f, 8f); lineTo(2f, 12f); lineTo(6f, 16f)
                    }
                    drawPath(path, color, style = stroke)
                }
                AergisGlyph.POWER -> {
                    drawArc(color, -55f, 290f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    drawLine(color, Offset(12f, 2f), Offset(12f, 12f), 1.7f, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
internal fun AergisMark(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val path = Path().apply {
            moveTo(size.width * .15f, size.height * .88f)
            lineTo(size.width * .49f, size.height * .12f)
            lineTo(size.width * .82f, size.height * .88f)
        }
        drawPath(path, Brush.linearGradient(listOf(AergisColors.Yellow, AergisColors.SilverBright, AergisColors.Yellow)),
            style = Stroke(size.width * .075f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
