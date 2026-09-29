package com.example.feynman.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import com.example.feynman.R

// Olive fallback palettes, close to Google Calculator's; used before Android 12
// (on 12+ the colors come from the wallpaper, like every Pixel app).
private val DarkFallback = darkColorScheme(
    primary = Color(0xFFC5CB86), onPrimary = Color(0xFF2F3300),
    primaryContainer = Color(0xFF525727), onPrimaryContainer = Color(0xFFE6E9A8),
    secondary = Color(0xFFC7C9A8), onSecondary = Color(0xFF2F3219),
    secondaryContainer = Color(0xFF3D3F28), onSecondaryContainer = Color(0xFFE0E1C4),
    tertiary = Color(0xFFFFE3C0), onTertiary = Color(0xFF4A3610),
    tertiaryContainer = Color(0xFF5F4A22), onTertiaryContainer = Color(0xFFFFDDB6),
    background = Color(0xFF12130C), onBackground = Color(0xFFE5E3D6),
    surface = Color(0xFF12130C), onSurface = Color(0xFFE5E3D6),
    surfaceVariant = Color(0xFF47473B), onSurfaceVariant = Color(0xFFC8C7B5),
    surfaceContainerLowest = Color(0xFF0D0E08), surfaceContainerLow = Color(0xFF1B1C14),
    surfaceContainer = Color(0xFF1F2018), surfaceContainerHigh = Color(0xFF292A22),
    surfaceContainerHighest = Color(0xFF34352C),
    outline = Color(0xFF929181), outlineVariant = Color(0xFF47473B),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

private val LightFallback = lightColorScheme(
    primary = Color(0xFF5B6133), onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE5A0), onPrimaryContainer = Color(0xFF1A1E00),
    secondary = Color(0xFF5E6044), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E4C4), onSecondaryContainer = Color(0xFF1B1D0A),
    tertiary = Color(0xFF7A5A2C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB6), onTertiaryContainer = Color(0xFF2A1800),
    background = Color(0xFFFCF9EE), onBackground = Color(0xFF1C1C16),
    surface = Color(0xFFFCF9EE), onSurface = Color(0xFF1C1C16),
    surfaceVariant = Color(0xFFE5E3D1), onSurfaceVariant = Color(0xFF47473B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F4E8),
    surfaceContainer = Color(0xFFF1EEE2), surfaceContainerHigh = Color(0xFFEBE9DC),
    surfaceContainerHighest = Color(0xFFE5E3D6),
    outline = Color(0xFF787767), outlineVariant = Color(0xFFC8C7B5),
    error = Color(0xFFBA1A1A), onError = Color.White,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeynmanTheme(content: @Composable () -> Unit) {
    val dark = when (com.example.feynman.ui.AppSettings.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val context = LocalContext.current
    val colors: ColorScheme = remember(context, dark, com.example.feynman.ui.AppSettings.dynamicColor, com.example.feynman.ui.AppSettings.themeColor) {
        appColorScheme(context, dark)
    }
    val mathFonts = remember(context) { com.example.feynman.ui.AndroidMathFonts(context.applicationContext) }
    CompositionLocalProvider(com.example.feynman.ui.LocalMathFonts provides mathFonts) {
        // M3 Expressive: springier motion for every component that animates.
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = if (com.example.feynman.ui.AppSettings.expressiveMotion) androidx.compose.material3.MotionScheme.expressive()
            else androidx.compose.material3.MotionScheme.standard(),
            typography = RoundedTypography,
            content = content,
        )
    }
}

/**
 * The app's colors, light or dark: the wallpaper's (Material You, Android 12+) when that's on,
 * otherwise grown from the chosen app color, or the built-in olive. Also used to export a graph
 * in the other theme.
 */
fun appColorScheme(context: Context, dark: Boolean): ColorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && com.example.feynman.ui.AppSettings.dynamicColor ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    // Your own color, or olive by default.
    com.example.feynman.ui.AppSettings.themeColor != 0 -> schemeOf(com.example.feynman.ui.TonalScheme.from(com.example.feynman.ui.AppSettings.themeColor, dark), dark)
    dark -> DarkFallback
    else -> LightFallback
}

/** A generated scheme as Material's; the roles it doesn't set keep Material's defaults. */
private fun schemeOf(r: com.example.feynman.ui.TonalScheme.Roles, dark: Boolean): ColorScheme {
    val base = if (dark) DarkFallback else LightFallback
    return base.copy(
        primary = Color(r.primary), onPrimary = Color(r.onPrimary),
        primaryContainer = Color(r.primaryContainer), onPrimaryContainer = Color(r.onPrimaryContainer),
        secondary = Color(r.secondary), onSecondary = Color(r.onSecondary),
        secondaryContainer = Color(r.secondaryContainer), onSecondaryContainer = Color(r.onSecondaryContainer),
        tertiary = Color(r.tertiary), onTertiary = Color(r.onTertiary),
        tertiaryContainer = Color(r.tertiaryContainer), onTertiaryContainer = Color(r.onTertiaryContainer),
        background = Color(r.background), onBackground = Color(r.onBackground),
        surface = Color(r.surface), onSurface = Color(r.onSurface),
        surfaceVariant = Color(r.surfaceVariant), onSurfaceVariant = Color(r.onSurfaceVariant),
        surfaceContainerLowest = Color(r.surfaceContainerLowest), surfaceContainerLow = Color(r.surfaceContainerLow),
        surfaceContainer = Color(r.surfaceContainer), surfaceContainerHigh = Color(r.surfaceContainerHigh),
        surfaceContainerHighest = Color(r.surfaceContainerHighest),
        surfaceBright = Color(if (dark) r.surfaceContainerHighest else r.surface), surfaceDim = Color(if (dark) r.surface else r.surfaceContainerHighest),
        surfaceTint = Color(r.primary),
        inverseSurface = Color(r.inverseSurface), inverseOnSurface = Color(r.inverseOnSurface), inversePrimary = Color(r.inversePrimary),
        outline = Color(r.outline), outlineVariant = Color(r.outlineVariant),
    )
}

/** The "=" key: bright peach in dark mode, soft peach in light mode, like Google Calculator. */
val ColorScheme.equalsKey: Pair<Color, Color>
    // Dark or light as the app is shown (the theme setting can differ from the system's).
    @Composable get() = if (surface.luminance() < 0.5f) tertiary to onTertiary else tertiaryContainer to onTertiaryContainer

@OptIn(ExperimentalTextApi::class)
/** Google Sans Flex, always fully rounded (ROND 100) — the only look used outside the maths. */
fun googleSansFlex(weight: Int, width: Float = 100f, slant: Float = 0f): FontFamily =
    FontFamily(
        Font(
            resId = R.font.google_sans_flex,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.width(width),
                FontVariation.slant(slant),
                FontVariation.Setting("ROND", 100f),
            ),
        ),
    )

@OptIn(ExperimentalTextApi::class)
fun roboto(weight: Int): FontFamily = FontFamily(
    Font(
        resId = R.font.roboto,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    ),
)

/**
 * Material's type scale with every style in Google Sans Flex, fully rounded,
 * so dialogs, menus and buttons match the rest of the app. (Only maths uses
 * Computer Modern.)
 */
val RoundedTypography: Typography by lazy {
    val base = Typography()
    fun TextStyle.rounded() = copy(fontFamily = googleSansFlex(weight = (fontWeight ?: FontWeight.Normal).weight))
    Typography(
        displayLarge = base.displayLarge.rounded(), displayMedium = base.displayMedium.rounded(), displaySmall = base.displaySmall.rounded(),
        headlineLarge = base.headlineLarge.rounded(), headlineMedium = base.headlineMedium.rounded(), headlineSmall = base.headlineSmall.rounded(),
        titleLarge = base.titleLarge.rounded(), titleMedium = base.titleMedium.rounded(), titleSmall = base.titleSmall.rounded(),
        bodyLarge = base.bodyLarge.rounded(), bodyMedium = base.bodyMedium.rounded(), bodySmall = base.bodySmall.rounded(),
        labelLarge = base.labelLarge.rounded(), labelMedium = base.labelMedium.rounded(), labelSmall = base.labelSmall.rounded(),
    )
}

object AppFonts {
    /** Computer Modern, as in LaTeX (MathJax's TeX fonts), for particle names on keys. */
    val CmRoman = FontFamily(Font(R.font.cm_main))
    val CmItalic = FontFamily(Font(R.font.cm_italic))
    val Key = googleSansFlex(weight = 400)
    val KeyMedium = googleSansFlex(weight = 500)
    val Ui = googleSansFlex(weight = 500)
}

