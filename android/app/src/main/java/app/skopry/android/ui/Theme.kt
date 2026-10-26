package app.skopry.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ProvideTextStyle
import app.skopry.android.R

/**
 * Skopry's look, from android/DESIGN.md: flat and dark, surfaces stacked on an abyss canvas and
 * outlined with one solid line, violet for what you press, coral kept for what is live or ends the
 * session, Plus Jakarta Sans. No shadows.
 */
object SkopryColors {
    /** Filled controls that are on or primary: buttons, a lit tile. White text on it. */
    val primary = Color(0xFF4F46E5)
    val primaryPressed = Color(0xFF6366F1)
    /** The violet that reads on the dark surfaces: text, icons and outlines of what is selected. */
    val accent = Color(0xFFC3C0FF)
    /** Live streams and the session's end, nothing else. */
    val live = Color(0xFFFF5733)
    val online = Color(0xFF10B981)
    val warning = Color(0xFFF59E0B)

    val background = Color(0xFF0B0F19)
    val surface = Color(0xFF141A2A)
    val surfaceHigh = Color(0xFF1C243A)
    /** The one line around every surface. */
    val outline = Color(0xFF232C45)
    /** Translucent surfaces over the video: the rails, the deck, a toast. */
    val bar = Color(0xF0141A2A)
    val toast = Color(0xE61C243A)
    val secondaryText = Color(0xFF94A3B8)
    val tertiaryText = Color(0x66FFFFFF)
    val danger = live

    // macOS's own window buttons, in the window menu: their colours mean close, minimise, zoom.
    val close = Color(0xFFFF5F57)
    val minimize = Color(0xFFFEBC2E)
    val zoom = Color(0xFF28C840)
}

/** Corner radii of DESIGN.md: cards and windows 14, buttons and fields 12, tags full. */
object SkopryShapes {
    val card = RoundedCornerShape(14.dp)
    val control = RoundedCornerShape(12.dp)
    val sheetTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Plus Jakarta Sans, one variable file, at the weights the app uses. */
val SkopryFont = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700))

@Composable
fun SkopryTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = SkopryColors.primary,
            onPrimary = Color.White,
            primaryContainer = SkopryColors.primary,
            onPrimaryContainer = Color.White,
            secondary = SkopryColors.accent,
            secondaryContainer = SkopryColors.surfaceHigh,
            onSecondaryContainer = Color.White,
            background = SkopryColors.background,
            onBackground = Color.White,
            surface = SkopryColors.surface,
            onSurface = Color.White,
            surfaceVariant = SkopryColors.surfaceHigh,
            onSurfaceVariant = SkopryColors.secondaryText,
            surfaceContainer = SkopryColors.surface,
            surfaceContainerHigh = SkopryColors.surfaceHigh,
            surfaceContainerHighest = SkopryColors.surfaceHigh,
            outline = SkopryColors.outline,
            outlineVariant = SkopryColors.outline,
            error = SkopryColors.danger,
        ),
        shapes = Shapes(extraSmall = SkopryShapes.control, small = RoundedCornerShape(8.dp), medium = SkopryShapes.control, large = SkopryShapes.card),
        typography = base.copy(
            displayLarge = base.displayLarge.withFont(), displayMedium = base.displayMedium.withFont(), displaySmall = base.displaySmall.withFont(),
            headlineLarge = base.headlineLarge.withFont(), headlineMedium = base.headlineMedium.withFont(), headlineSmall = base.headlineSmall.withFont(),
            titleLarge = base.titleLarge.withFont(), titleMedium = base.titleMedium.withFont(), titleSmall = base.titleSmall.withFont(),
            bodyLarge = base.bodyLarge.withFont(), bodyMedium = base.bodyMedium.withFont(), bodySmall = base.bodySmall.withFont(),
            labelLarge = base.labelLarge.withFont(), labelMedium = base.labelMedium.withFont(), labelSmall = base.labelSmall.withFont(),
        ),
    ) {
        // Text outside a Surface (most of the app) takes its style from here, so it gets the font too.
        ProvideTextStyle(TextStyle(fontFamily = SkopryFont), content)
    }
}

private fun TextStyle.withFont() = copy(fontFamily = SkopryFont)
