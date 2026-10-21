package com.airgesture.control.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.airgesture.control.RegisteredGesture
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Static, decorative product artwork. Never subscribes to camera frames or runtime coordinates. */
@Composable
internal fun SculptedHand(gesture: RegisteredGesture = RegisteredGesture.OPEN_PALM, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val unit = min(size.width / 190f, size.height / 230f)
        translate((size.width - 180f * unit) / 2f, (size.height - 214f * unit) / 2f) {
            scale(unit, unit, Offset.Zero) { metalHand(gesture) }
        }
    }
}

@Composable
internal fun ControlInstrument(active: Boolean, stopped: Boolean, modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val radius = min(size.width, size.height) * .46f
        val c = center
        // Layered bevels and a restrained glass reflection reproduce the MagicPath instrument.
        drawCircle(Color.Black.copy(alpha = .38f), radius, c + Offset(0f, radius * .08f))
        drawCircle(Brush.sweepGradient(listOf(Color(0xFF3A3B44), Color(0xFFADADB8),
            Color(0xFF1E1F27), Color(0xFF777981), Color(0xFF292A32), Color(0xFF3A3B44)), c), radius, c)
        drawCircle(Color(0xFF111116), radius * .974f, c)
        drawCircle(Brush.linearGradient(listOf(Color(0xFF71717D), Color(0xFF17171F), Color(0xFF42424F)),
            c - Offset(radius, radius), c + Offset(radius, radius)), radius * .945f, c)
        drawCircle(Color(0xFF16161E), radius * .922f, c)
        for (i in 0 until 72) {
            val angle = i * Math.PI / 36.0
            val direction = Offset(sin(angle).toFloat(), cos(angle).toFloat())
            drawLine(if (i % 6 == 0) Color(0xFFB0ABB9) else Color(0xFF56515F),
                c + direction * radius * (if (i % 6 == 0) .84f else .87f),
                c + direction * radius * .9f, if (i % 6 == 0) 1.2f else .7f)
        }
        drawCircle(Brush.radialGradient(listOf(Color(if (active) 0xFF514758 else 0xFF363240),
            Color(0xFF18171F), Color(0xFF101016)), c - Offset(radius * .3f, radius * .4f), radius * 1.5f), radius * .79f, c)
        drawCircle(Color(0xFF77717F).copy(alpha = .35f), radius * .79f, c, style = Stroke(1f))
        drawArc(Color.White.copy(alpha = .12f), 205f, 90f, false,
            c - Offset(radius * .75f, radius * .75f), Size(radius * 1.5f, radius * 1.5f), style = Stroke(1f))
        val unit = radius * .006f
        translate(c.x - 88f * unit, c.y - 112f * unit) {
            scale(unit, unit, Offset.Zero) { metalHand(RegisteredGesture.OPEN_PALM) }
        }
        val signal = if (active) AergisColors.Yellow else if (stopped) AergisColors.Muted else AergisColors.Purple
        drawCircle(signal.copy(alpha = .14f), radius * .055f, c + Offset(0f, radius * .66f))
        drawCircle(signal, radius * .018f, c + Offset(0f, radius * .66f))
    }
}

@Composable
internal fun DirectionInstrument(modifier: Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        val r = min(size.width, size.height) * .44f
        val c = center
        drawCircle(Color.Black.copy(alpha = .35f), r, c + Offset(0f, r * .1f))
        drawCircle(Brush.sweepGradient(listOf(Color(0xFF94919E), Color(0xFF2D2C36), Color(0xFF777480),
            Color(0xFF18171F), Color(0xFF94919E)), c), r, c)
        drawCircle(Color(0xFF13131A), r * .97f, c)
        drawCircle(Brush.linearGradient(listOf(Color(0xFF45424E), Color(0xFF202029), Color(0xFF101016)),
            c - Offset(r, r), c + Offset(r, r)), r * .94f, c)
        drawCircle(Color(0xFF66616F), r * .89f, c, style = Stroke(1f))
        for (i in 0..3) rotate(i * 90f, c) {
            val arrow = Path().apply {
                moveTo(c.x, c.y - r * .68f)
                lineTo(c.x + r * .15f, c.y - r * .41f)
                lineTo(c.x - r * .15f, c.y - r * .41f)
                close()
            }
            drawPath(arrow, if (i == 0) Brush.linearGradient(listOf(AergisColors.Yellow, AergisColors.YellowDeep))
                else Brush.linearGradient(listOf(AergisColors.SilverBright, Color(0xFF55535F))))
            drawLine(Color.White.copy(alpha = .22f), c + Offset(0f, -r * .28f), c + Offset(0f, -r * .39f), 1f)
        }
        drawCircle(Color(0xFF111117), r * .24f, c)
        drawCircle(Color(0xFF797080), r * .24f, c, style = Stroke(1f))
        drawCircle(Brush.radialGradient(listOf(AergisColors.SilverBright, AergisColors.Purple, AergisColors.PurpleDeep), c, r * .1f), r * .08f, c)
    }
}

private fun DrawScope.metalHand(gesture: RegisteredGesture) {
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
        else -> setOf(0, 1, 2, 3)
    }
    val rotation = when (gesture) {
        RegisteredGesture.DOUBLE_GUN_LEFT -> -90f
        RegisteredGesture.DOUBLE_GUN_RIGHT -> 90f
        RegisteredGesture.DOUBLE_GUN_DOWN -> 180f
        else -> 0f
    }
    val metal = Brush.linearGradient(0f to Color(0xFF565962), .15f to Color(0xFFC4C7CE),
        .27f to Color(0xFFE5E6EB), .44f to Color(0xFF7B7E89), .72f to Color(0xFF292C34),
        .87f to Color(0xFFB9BCC8), 1f to Color(0xFF747782), start = Offset(30f, 0f), end = Offset(155f, 65f))
    rotate(rotation, Offset(90f, 111f)) {
        val palm = Path().apply {
            moveTo(51f, 99f); cubicTo(56f, 92f, 67f, 89f, 77f, 90f)
            lineTo(130f, 95f); cubicTo(142f, 97f, 148f, 103f, 147f, 117f)
            lineTo(145f, 147f); cubicTo(144f, 162f, 132f, 168f, 129f, 182f)
            lineTo(127f, 202f); lineTo(74f, 202f); lineTo(71f, 187f)
            cubicTo(70f, 179f, 57f, 174f, 49f, 161f)
            lineTo(33f, 137f); cubicTo(24f, 122f, 32f, 112f, 43f, 121f)
            lineTo(55f, 135f); close()
        }
        drawPath(palm, metal)
        drawPath(palm, Color(0xFFD9DBE6).copy(alpha = .28f), style = Stroke(.7f))
        for (finger in 0..3) {
            val x = 53f + finger * 24f
            val y = if (finger in extended) listOf(37f, 17f, 29f, 57f)[finger] else 89f
            drawRoundRect(Color.Black.copy(alpha = .22f), Offset(x + 3f, y + 5f), Size(22f, 121f - y), CornerRadius(11f))
            drawRoundRect(metal, Offset(x, y), Size(20f, 119f - y), CornerRadius(10f))
            drawRoundRect(Color(0xFFD9DBE6).copy(alpha = .28f), Offset(x, y), Size(20f, 119f - y), CornerRadius(10f), style = Stroke(.7f))
            drawLine(Color.White.copy(alpha = .26f), Offset(x + 5f, y + 10f), Offset(x + 5f, y + 48f), .9f, StrokeCap.Round)
        }
        val thumbOut = gesture in setOf(RegisteredGesture.OPEN_PALM, RegisteredGesture.I_LOVE_YOU,
            RegisteredGesture.SHAKA, RegisteredGesture.L_SHAPE, RegisteredGesture.THUMB_INDEX_MIDDLE,
            RegisteredGesture.FOUR_LITTLE_FOLDED, RegisteredGesture.FOUR_INDEX_FOLDED,
            RegisteredGesture.DOUBLE_GUN_LEFT, RegisteredGesture.DOUBLE_GUN_RIGHT,
            RegisteredGesture.DOUBLE_GUN_UP, RegisteredGesture.DOUBLE_GUN_DOWN)
        val thumbEnd = when (gesture) {
            RegisteredGesture.THUMB_UP -> Offset(39f, 44f)
            RegisteredGesture.THUMB_DOWN -> Offset(32f, 202f)
            else -> if (thumbOut) Offset(17f, 105f) else Offset(92f, 121f)
        }
        drawLine(metal, Offset(59f, 164f), thumbEnd, 21f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = .22f), Offset(54f, 158f), thumbEnd - Offset(3f, 2f), 1f, StrokeCap.Round)
        val crease = Path().apply {
            moveTo(68f, 129f); cubicTo(79f, 130f, 81f, 149f, 85f, 163f)
            moveTo(121f, 123f); cubicTo(106f, 134f, 102f, 144f, 101f, 162f)
            moveTo(75f, 180f); lineTo(119f, 179f)
        }
        drawPath(crease, Color.White.copy(alpha = .18f), style = Stroke(.7f))
        drawRoundRect(Color(0xFF42454F), Offset(79f, 196f), Size(45f, 11f), CornerRadius(2f))
        drawCircle(Color(0xFF17171E), 7f, Offset(96f, 143f))
        drawCircle(AergisColors.Purple, 7f, Offset(96f, 143f), style = Stroke(1f))
        drawCircle(AergisColors.Yellow, 2.5f, Offset(96f, 143f))
    }
}
