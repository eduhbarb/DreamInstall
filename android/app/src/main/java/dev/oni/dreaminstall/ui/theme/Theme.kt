package dev.oni.dreaminstall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.oni.dreaminstall.R

val LocalPalette = staticCompositionLocalOf { DREAM }

private val Fredoka = FontFamily(
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)

private val Hanken = FontFamily(
    Font(R.font.hanken_regular, FontWeight.Normal),
    Font(R.font.hanken_semibold, FontWeight.SemiBold),
)

private val DreamTypography = Typography(
    displaySmall = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
)

private val DreamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

fun Palette.color(which: (Palette) -> Int): Color = Color(which(this))

@Composable
fun DreamTheme(accent: Int, content: @Composable () -> Unit) {
    val palette = remember(accent) { paletteFor(accent) }
    val colors = remember(palette) {
        darkColorScheme(
            primary = Color(palette.accent),
            onPrimary = Color(palette.onAccent),
            primaryContainer = Color(palette.selected),
            onPrimaryContainer = Color(palette.text),
            secondary = Color(palette.highlight),
            onSecondary = Color(palette.onAccent),
            secondaryContainer = Color(palette.track),
            onSecondaryContainer = Color(palette.text),
            tertiary = Color(palette.soft),
            onTertiary = Color(palette.onAccent),
            background = Color(palette.bg),
            onBackground = Color(palette.text),
            surface = Color(palette.card),
            onSurface = Color(palette.text),
            surfaceVariant = Color(palette.track),
            onSurfaceVariant = Color(palette.dim),
            surfaceContainer = Color(palette.card),
            surfaceContainerHigh = Color(palette.blob),
            surfaceContainerHighest = Color(palette.border),
            surfaceContainerLow = Color(palette.bg),
            surfaceContainerLowest = Color(palette.bg),
            outline = Color(palette.border),
            outlineVariant = Color(palette.track),
            error = Color(0xFFE44C3E),
            onError = Color(0xFF2B0A08),
            scrim = Color(0xCC000000),
            inverseSurface = Color(palette.text),
            inverseOnSurface = Color(palette.bg),
            inversePrimary = Color(palette.bg),
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colors, typography = DreamTypography, shapes = DreamShapes, content = content)
    }
}
