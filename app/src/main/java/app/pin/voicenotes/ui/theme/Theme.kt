package app.pin.voicenotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * PIN's visual system: near-black graphite surfaces, warm off-white type,
 * one amber signal accent. Dark is the product's single, deliberate look —
 * a half-hearted light theme would dilute it, so there isn't one.
 */
object PinColors {
    val Background = Color(0xFF0B0C0F)
    val Surface = Color(0xFF121418)
    val SurfaceRaised = Color(0xFF171A20)
    val SurfaceHigh = Color(0xFF1E222A)
    val Outline = Color(0xFF262B33)
    val Ink = Color(0xFFF2EEE4)
    val InkFaint = Color(0xFFA8A69C)
    val InkGhost = Color(0xFF6F6E67)
    val Accent = Color(0xFFE8A33D)
    val AccentDeep = Color(0xFF6B4A15)
    val OnAccent = Color(0xFF1D1404)
    val Danger = Color(0xFFE0684F)
    val Success = Color(0xFF8FBF7F)
}

private val DarkScheme = darkColorScheme(
    primary = PinColors.Accent,
    onPrimary = PinColors.OnAccent,
    primaryContainer = PinColors.AccentDeep,
    onPrimaryContainer = Color(0xFFF6DCB2),
    secondary = PinColors.InkFaint,
    onSecondary = PinColors.Background,
    secondaryContainer = PinColors.SurfaceHigh,
    onSecondaryContainer = PinColors.Ink,
    background = PinColors.Background,
    onBackground = PinColors.Ink,
    surface = PinColors.Background,
    onSurface = PinColors.Ink,
    surfaceVariant = PinColors.SurfaceRaised,
    onSurfaceVariant = PinColors.InkFaint,
    surfaceContainer = PinColors.Surface,
    surfaceContainerHigh = PinColors.SurfaceRaised,
    surfaceContainerHighest = PinColors.SurfaceHigh,
    surfaceContainerLow = PinColors.Surface,
    outline = PinColors.Outline,
    outlineVariant = PinColors.Outline,
    error = PinColors.Danger,
    onError = Color(0xFF230A05),
    inverseSurface = PinColors.Ink,
    inverseOnSurface = PinColors.Background,
    inversePrimary = PinColors.AccentDeep,
)

private val PinTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.8.sp,
    ),
)

@Composable
fun PinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = PinTypography,
        content = content,
    )
}
