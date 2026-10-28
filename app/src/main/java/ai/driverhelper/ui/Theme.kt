package ai.driverhelper.ui

import ai.driverhelper.R
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Toss Design System (TDS) tokens on the Pleos car-display scale.
 *
 * Colours, radii, weights and tracking follow the Toss design.md (published @toss/tds-colors values for
 * light and dark). Sizes sit well above the Pleos minimums (body 32sp, 1.5× line height, 96dp touch targets) for a 2 m viewing distance.
 * The font stays Asta Sans because Pleos requires it; Toss Product Sans cannot be redistributed and
 * Toss itself suggests a neutral grotesque substitute. One blue primary action per screen, flat
 * surfaces, 1dp hairlines, no gradients, motion within 120/200/320 ms.
 */

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val astaSans = FontFamily(
    Font(R.font.asta_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.asta_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.asta_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.asta_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Double, tracking: Double) =
    TextStyle(fontFamily = astaSans, fontSize = size.sp, lineHeight = (size * lineHeight).sp, fontWeight = weight, letterSpacing = tracking.em)

/** TDS ramp: display/h → headline/title, title-1 → titleMedium, body-1/2 → body, label-l/m → label, caption → labelSmall. */
val tdsTypography = Typography(
    headlineLarge = style(60, FontWeight.Bold, 1.2, -0.02),
    titleLarge = style(44, FontWeight.Bold, 1.3, -0.02),
    titleMedium = style(34, FontWeight.SemiBold, 1.4, -0.01),
    bodyLarge = style(32, FontWeight.Normal, 1.5, -0.005),
    bodyMedium = style(28, FontWeight.Normal, 1.5, -0.005),
    labelLarge = style(30, FontWeight.Bold, 1.25, -0.005),
    labelMedium = style(26, FontWeight.SemiBold, 1.25, -0.005),
    labelSmall = style(22, FontWeight.Medium, 1.4, 0.0),
)

/** Light: TDS grey-100 canvas, white cards, grey-900 text, blue-500 brand, red-500 danger, orange-500 warning. */
private val light = lightColorScheme(
    background = Color(0xFFF2F4F6),
    onBackground = Color(0xFF191F28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191F28),
    surfaceVariant = Color(0xFFF2F4F6),
    onSurfaceVariant = Color(0xFF4E5968),
    primary = Color(0xFF3182F6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F3FF),
    onPrimaryContainer = Color(0xFF3182F6),
    secondary = Color(0xFF4E5968),
    tertiary = Color(0xFFFF8800),
    error = Color(0xFFF04452),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDECEE),
    onErrorContainer = Color(0xFFF04452),
    outline = Color(0xFFE5E8EB),
    outlineVariant = Color(0xFFD1D6DB),
    inverseSurface = Color(0xFF191F28),
    inverseOnSurface = Color(0xFFFFFFFF),
)

/** Dark: TDS adaptive palette (grey names keep their role, lightness flips). */
private val dark = darkColorScheme(
    background = Color(0xFF202027),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF2C2C35),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF3C3C47),
    onSurfaceVariant = Color(0xFFC3C3C6),
    primary = Color(0xFF3485FA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF202C4D),
    onPrimaryContainer = Color(0xFF8DB9FF),
    secondary = Color(0xFFC3C3C6),
    tertiary = Color(0xFFFFB134),
    error = Color(0xFFF04251),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3B2328),
    onErrorContainer = Color(0xFFFF7A86),
    outline = Color(0xFF3C3C47),
    outlineVariant = Color(0xFF4D4D59),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF191F28),
)

/** TDS radius ladder scaled for 96dp controls: small = inputs, medium = buttons, large = cards, extraLarge = sheets. */
val tdsShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Chips, pills and the primary CTA. */
val pill = RoundedCornerShape(999.dp)

/** 1dp card hairline on the grey canvas (TDS line-subtle, 8 % black; 8 % white at night). */
@Composable
fun hairline() = if (isSystemInDarkTheme()) Color(0x14FFFFFF) else Color(0x14000000)

/** Minimum touch target: Pleos' 96dp, well above TDS' 44pt phone minimum. */
val touchTarget = 96.dp

/** TDS motion tokens: durations in ms, no bounce or overshoot. */
const val DUR_FAST = 120
const val DUR_BASE = 200
const val DUR_SLOW = 320
/** TDS `ease`: toggles, expand/collapse. */
val tdsEase = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)
/** TDS `ease-out`: snappier, for screen and sheet entry. */
val tdsEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Follows the car's day/night setting (system dark mode). Pressed state is the TDS 26 % black overlay, not a shadow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverHelperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) dark else light,
        typography = tdsTypography,
        shapes = tdsShapes,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides RippleConfiguration(color = Color.Black, rippleAlpha = RippleAlpha(0.26f, 0.26f, 0.26f, 0.26f)),
            content = content,
        )
    }
}
