package com.airgesture.control.ui

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object AergisColors {
    val Background = Color(0xFFF1EFEB)
    val Surface = Color(0xFFFCFAF6)
    val Raised = Color(0xFFE8E0F4)
    val Deep = Color(0xFF211733)
    val Outline = Color(0xFFD6CDDF)
    val Text = Color(0xFF251C31)
    val Muted = Color(0xFF62576E)
    val Purple = Color(0xFF5733A2)
    val Stage = Color(0xFF4B259F)
    val StageShadow = Color(0xFF301A64)
    val OnStage = Color(0xFFFFFCF5)
    val StageHint = Color(0xFFE9DEFF)
    val Yellow = Color(0xFFF5EC70)
    val Cyan = Purple
    val Success = Color(0xFF12634D)
    val Caution = Color(0xFF795015)
    val Error = Color(0xFFA32C48)
}

internal object AergisSpace {
    val Tiny = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Content = 16.dp
    val Section = 24.dp
    val Large = 32.dp
}

internal object AergisShapes {
    val Card = RoundedCornerShape(18.dp)
    val Stage = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 8.dp)
    val Control = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(50)
    val Segment = RoundedCornerShape(12.dp)
    /** Aergis brand detail: used sparingly, for the primary command and brand marks only. */
    val Brand = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)
}

internal object AergisType {
    /** Wide-tracked wordmark. Minimal use. */
    val Brand = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
    /** Tabular numerals so changing telemetry does not jitter horizontally. */
    val Telemetry = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
}

private val colors = lightColorScheme(
    primary = AergisColors.Purple, onPrimary = AergisColors.OnStage,
    primaryContainer = AergisColors.Raised, onPrimaryContainer = AergisColors.Text,
    secondary = AergisColors.Purple, onSecondary = AergisColors.OnStage,
    secondaryContainer = AergisColors.Raised, onSecondaryContainer = AergisColors.Text,
    background = AergisColors.Background, onBackground = AergisColors.Text,
    surface = AergisColors.Surface, onSurface = AergisColors.Text,
    surfaceVariant = AergisColors.Raised, onSurfaceVariant = AergisColors.Muted,
    surfaceContainer = AergisColors.Surface, surfaceContainerHigh = AergisColors.Raised,
    outline = AergisColors.Muted, outlineVariant = AergisColors.Outline,
    error = AergisColors.Error, onError = AergisColors.OnStage,
    errorContainer = Color(0xFFFBE4E9), onErrorContainer = AergisColors.Error
)

private fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight
)

private val typography = Typography(
    displaySmall = style(42, 44, FontWeight.Black).copy(letterSpacing = (-1.6).sp),
    headlineLarge = style(36, 40, FontWeight.Black).copy(letterSpacing = (-1.3).sp),
    headlineMedium = style(28, 33, FontWeight.Bold).copy(letterSpacing = (-.7).sp),
    headlineSmall = style(22, 28, FontWeight.Bold).copy(letterSpacing = (-.4).sp),
    titleLarge = style(20, 26, FontWeight.Medium),
    titleMedium = style(18, 24, FontWeight.Medium),
    titleSmall = style(16, 22, FontWeight.Medium),
    bodyLarge = style(16, 24), bodyMedium = style(14, 20), bodySmall = style(12, 18),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium), labelSmall = style(11, 16, FontWeight.Bold).copy(letterSpacing = .85.sp)
)

@Composable
internal fun AergisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors, typography = typography,
        shapes = Shapes(small = AergisShapes.Control, medium = AergisShapes.Control, large = AergisShapes.Card),
        content = content
    )
}
