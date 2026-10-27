package ai.driverhelper.ui

import ai.driverhelper.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Pleos Connect UI design guide, as far as it is written down: Asta Sans, ≥12sp, line height ≥1.5×,
 * contrast ≥4.5:1, day/night themes, key text ≥4 mm tall, ≤3 lines per paragraph, icons always labelled.
 * Exact colour hex values and the type scale live only in the Figma assets, so the numbers below are
 * estimates kept in this one file. Swap them when the design tokens are available.
 */

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val astaSans = FontFamily(
    Font(R.font.asta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.asta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.asta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = astaSans, fontSize = size.sp, lineHeight = (size * 1.5).sp, fontWeight = weight)

/** Headline / Title / Body / Label, the four levels the guide names. Body is ≥4 mm on the 160 dpi car displays. */
val pleosTypography = Typography(
    headlineLarge = style(44, FontWeight.SemiBold),
    titleLarge = style(32, FontWeight.Medium),
    bodyLarge = style(26),
    labelLarge = style(22, FontWeight.Medium),
)

private val night = darkColorScheme(
    background = Color(0xFF0B0D10),
    surface = Color(0xFF171A1F),
    surfaceVariant = Color(0xFF22262D),
    primary = Color(0xFF5B9DFF),
    onPrimary = Color(0xFF071A33),
    onBackground = Color(0xFFF2F4F7),
    onSurface = Color(0xFFF2F4F7),
    onSurfaceVariant = Color(0xFFB4BAC4),
    outline = Color(0xFF3A404A),
    error = Color(0xFFFF6B6B),
)

private val day = lightColorScheme(
    background = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E9EE),
    primary = Color(0xFF1F5EFF),
    onPrimary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111418),
    onSurface = Color(0xFF111418),
    onSurfaceVariant = Color(0xFF4B5260),
    outline = Color(0xFFB8BEC8),
    error = Color(0xFFC62828),
)

/** Amber for regulation warnings (school zone, cameras); same in both themes, like the guide's "Static" colours. */
val regulationAmber = Color(0xFFFFB020)

val pleosShapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))

/** Follows the car's day/night setting, which Android exposes as the system dark mode. */
@Composable
fun DriverHelperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) night else day,
        typography = pleosTypography,
        shapes = pleosShapes,
        content = content,
    )
}

/** Minimum touch target: comfortably above the 48 dp Android floor, for gloved hands on a moving car. */
val touchTarget = 96.dp
