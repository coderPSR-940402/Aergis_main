package com.airgesture.control.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object AergisColors {
    val Background = Color(0xFF060C1B)
    val Surface = Color(0xFF101C32)
    val Raised = Color(0xFF17253E)
    val Outline = Color(0xFF304663)
    val Text = Color(0xFFF2F6FF)
    val Muted = Color(0xFFB4C3D9)
    val Cyan = Color(0xFF67E7FF)
    val Blue = Color(0xFF98BDFF)
    val Violet = Color(0xFFB7A0FF)
    val Success = Color(0xFF73E6BB)
    val Caution = Color(0xFFFFD18A)
    val Error = Color(0xFFFFB4BD)
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
    val Card = RoundedCornerShape(24.dp)
    val Control = RoundedCornerShape(16.dp)
    val Chip = RoundedCornerShape(50)
    val Segment = RoundedCornerShape(12.dp)
    /** Aergis brand detail: used sparingly, for the primary command and brand marks only. */
    val Brand = CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)
}

internal object AergisType {
    /** Wide-tracked wordmark. Minimal use. */
    val Brand = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
    /** Tabular numerals so changing telemetry does not jitter horizontally. */
    val Telemetry = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
}

internal object AergisMotion {
    const val StateMs = 160
    const val NavigationMs = 180
    val Easing = FastOutSlowInEasing
}

private val colors = darkColorScheme(
    primary = AergisColors.Cyan, onPrimary = AergisColors.Background,
    primaryContainer = Color(0xFF133C50), onPrimaryContainer = AergisColors.Cyan,
    secondary = AergisColors.Violet, onSecondary = AergisColors.Background,
    secondaryContainer = Color(0xFF302947), onSecondaryContainer = AergisColors.Text,
    tertiary = AergisColors.Success, onTertiary = AergisColors.Background,
    background = AergisColors.Background, onBackground = AergisColors.Text,
    surface = AergisColors.Surface, onSurface = AergisColors.Text,
    surfaceVariant = AergisColors.Raised, onSurfaceVariant = AergisColors.Muted,
    surfaceContainer = AergisColors.Surface, surfaceContainerHigh = AergisColors.Raised,
    outline = AergisColors.Outline, outlineVariant = AergisColors.Outline,
    error = AergisColors.Error, onError = AergisColors.Background,
    errorContainer = Color(0xFF422330), onErrorContainer = AergisColors.Text
)

private fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight
)

private val typography = Typography(
    headlineLarge = style(32, 38, FontWeight.SemiBold),
    headlineMedium = style(28, 34, FontWeight.SemiBold),
    headlineSmall = style(24, 30, FontWeight.SemiBold),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(18, 24, FontWeight.SemiBold),
    titleSmall = style(16, 22, FontWeight.Medium),
    bodyLarge = style(16, 24), bodyMedium = style(14, 20), bodySmall = style(12, 18),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium), labelSmall = style(11, 16, FontWeight.Medium)
)

@Composable
internal fun AergisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors, typography = typography,
        shapes = Shapes(small = AergisShapes.Control, medium = AergisShapes.Control, large = AergisShapes.Card),
        content = content
    )
}
