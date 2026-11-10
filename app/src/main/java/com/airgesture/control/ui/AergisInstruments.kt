package com.airgesture.control.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.airgesture.control.RegisteredGesture
import kotlin.math.min

/** Pose diagrams illustrate the written instructions. They do not display recognition data. */
@Composable
internal fun HandDiagram(
    gesture: RegisteredGesture,
    modifier: Modifier,
    color: Color = AergisColors.Purple,
    accent: Color = color
) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val unit = min(size.width, size.height) / 112f
        translate(center.x - 56f * unit, center.y - 56f * unit) {
            scale(unit, unit, Offset.Zero) { drawPose(gesture, color, accent) }
        }
    }
}

@Composable
internal fun CommandField(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val spacing = 20.dp.toPx()
        val columns = (size.width / spacing).toInt()
        val rows = (size.height / spacing).toInt()
        val grid = AergisColors.StageHint.copy(alpha = .24f)
        for (column in 0..columns) for (row in 0..rows) {
            drawCircle(grid, 1.dp.toPx(), Offset(column * spacing, row * spacing))
        }
        val radius = min(size.width, size.height) * .38f
        val focus = Offset(size.width * .65f, size.height * .56f)
        drawCircle(grid, radius, focus, style = Stroke(1.dp.toPx()))
        drawCircle(grid, radius * .76f, focus, style = Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun DirectionInstrument(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val radius = min(size.width, size.height) * .46f
        val c = center
        drawRoundRect(AergisColors.Raised, c - Offset(radius, radius), Size(radius * 2, radius * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()))
        drawCircle(AergisColors.Purple.copy(alpha = .22f), radius * .72f, c, style = Stroke(1.dp.toPx()))
        for (direction in 0..3) rotate(direction * 90f, c) {
            val tip = c - Offset(0f, radius * .72f)
            val end = c - Offset(0f, radius * .23f)
            val wing = radius * .15f
            val arrow = Path().apply {
                moveTo(tip.x - wing, tip.y + wing)
                lineTo(tip.x, tip.y)
                lineTo(tip.x + wing, tip.y + wing)
            }
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round)
            drawLine(AergisColors.Purple, end, tip, stroke.width, StrokeCap.Round)
            drawPath(arrow, AergisColors.Purple, style = stroke)
        }
        drawCircle(AergisColors.Purple, radius * .10f, c)
        drawCircle(AergisColors.Yellow, radius * .05f, c)
    }
}

private fun DrawScope.drawPose(gesture: RegisteredGesture, color: Color, accent: Color) {
    val extended = when (gesture) {
        RegisteredGesture.VICTORY, RegisteredGesture.THUMB_INDEX_MIDDLE,
        RegisteredGesture.DOUBLE_GUN_LEFT, RegisteredGesture.DOUBLE_GUN_RIGHT,
        RegisteredGesture.DOUBLE_GUN_UP, RegisteredGesture.DOUBLE_GUN_DOWN -> setOf(0, 1)
        RegisteredGesture.CLOSED_FIST, RegisteredGesture.THUMB_UP, RegisteredGesture.THUMB_DOWN -> emptySet()
        RegisteredGesture.POINTING_UP, RegisteredGesture.L_SHAPE -> setOf(0)
        RegisteredGesture.PINKY_ONLY, RegisteredGesture.SHAKA -> setOf(3)
        RegisteredGesture.I_LOVE_YOU, RegisteredGesture.HORNS -> setOf(0, 3)
        RegisteredGesture.THREE_FINGERS, RegisteredGesture.FOUR_LITTLE_FOLDED -> setOf(0, 1, 2)
        RegisteredGesture.OK, RegisteredGesture.THREE_OUTER_FINGERS, RegisteredGesture.FOUR_INDEX_FOLDED -> setOf(1, 2, 3)
        RegisteredGesture.OPEN_PALM, RegisteredGesture.FOUR_FINGERS -> setOf(0, 1, 2, 3)
    }
    val rotation = when (gesture) {
        RegisteredGesture.DOUBLE_GUN_LEFT -> -90f
        RegisteredGesture.DOUBLE_GUN_RIGHT -> 90f
        RegisteredGesture.DOUBLE_GUN_DOWN -> 180f
        else -> 0f
    }
    val wrist = Offset(54f, 94f)
    val bases = listOf(Offset(38f, 57f), Offset(53f, 52f), Offset(67f, 54f), Offset(80f, 61f))
    val tips = listOf(Offset(28f, 18f), Offset(51f, 9f), Offset(72f, 15f), Offset(94f, 33f))
    val thumbBase = Offset(29f, 73f)
    val thumbTip = when (gesture) {
        RegisteredGesture.THUMB_UP -> Offset(20f, 27f)
        RegisteredGesture.THUMB_DOWN -> Offset(18f, 101f)
        RegisteredGesture.OK -> Offset(34f, 44f)
        RegisteredGesture.OPEN_PALM, RegisteredGesture.I_LOVE_YOU, RegisteredGesture.SHAKA,
        RegisteredGesture.L_SHAPE, RegisteredGesture.THUMB_INDEX_MIDDLE, RegisteredGesture.FOUR_LITTLE_FOLDED,
        RegisteredGesture.FOUR_INDEX_FOLDED, RegisteredGesture.DOUBLE_GUN_LEFT, RegisteredGesture.DOUBLE_GUN_RIGHT,
        RegisteredGesture.DOUBLE_GUN_UP, RegisteredGesture.DOUBLE_GUN_DOWN -> Offset(11f, 57f)
        else -> Offset(47f, 69f)
    }
    rotate(rotation, Offset(56f, 56f)) {
        val palm = Path().apply {
            moveTo(wrist.x, wrist.y)
            lineTo(thumbBase.x, thumbBase.y)
            for (point in bases) lineTo(point.x, point.y)
            close()
        }
        drawPath(palm, color.copy(alpha = .16f))
        drawPath(palm, color, style = Stroke(1.9f))
        drawChain(listOf(wrist, thumbBase, (thumbBase + thumbTip) * .5f, thumbTip), color, accent)
        for ((index, base) in bases.withIndex()) {
            val tip = tips[index]
            val delta = tip - base
            val points = when {
                index in extended -> listOf(base, base + delta * .42f, base + delta * .73f, tip)
                gesture == RegisteredGesture.OK && index == 0 ->
                    listOf(base, Offset(44f, 40f), Offset(40f, 36f), thumbTip)
                else -> listOf(base, base + Offset(0f, -10f), base + Offset(5f, -8f), base + Offset(4f, 1f))
            }
            drawChain(points, color, accent)
        }
        drawCircle(accent, 3.5f, wrist)
    }
}

private fun DrawScope.drawChain(points: List<Offset>, color: Color, accent: Color) {
    for (index in 1..points.lastIndex) drawLine(color, points[index - 1], points[index], 2.5f, StrokeCap.Round)
    for (point in points.dropLast(1)) drawCircle(color, 2.15f, point)
    drawCircle(accent, 3.35f, points.last())
}
