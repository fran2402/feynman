package com.example.feynman.ui

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A Material-style color scheme grown from one chosen color, for when Material You is off
 * (or before Android 12). Like Material's "tonal spot" scheme: five tonal palettes (primary,
 * secondary, tertiary, neutral, neutral variant) share the chosen hue (tertiary is turned
 * 60° round), each with its own chroma, and every role takes a fixed tone from one of them.
 *
 * Tones are CIELAB lightness L* (0 black to 100 white), as in Material's HCT; they're built in
 * OKLab at the matching lightness (for a grey, OKLab L = ∛Y = (L* + 16)/116), with the chroma
 * reduced until the color fits in sRGB. Plain Kotlin (ARGB ints), so it's tested directly.
 */
object TonalScheme {
    /** The roles the app uses, as ARGB. */
    data class Roles(
        val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
        val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
        val tertiary: Int, val onTertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
        val background: Int, val onBackground: Int, val surface: Int, val onSurface: Int,
        val surfaceVariant: Int, val onSurfaceVariant: Int,
        val surfaceContainerLowest: Int, val surfaceContainerLow: Int, val surfaceContainer: Int,
        val surfaceContainerHigh: Int, val surfaceContainerHighest: Int,
        val inverseSurface: Int, val inverseOnSurface: Int, val inversePrimary: Int,
        val outline: Int, val outlineVariant: Int,
    )

    /** One hue at one chroma (OKLab a–b distance), at any tone. */
    class Palette(private val hue: Double, private val chroma: Double) {
        fun tone(t: Double): Int = TonalScheme.tone(t, hue, chroma)
    }

    /** OKLab lightness for a CIELAB lightness L* (exact for greys). */
    private fun oklabLightness(tone: Double): Double {
        val t = tone.coerceIn(0.0, 100.0)
        val y = if (t > 8) ((t + 16) / 116).let { it * it * it } else t / 903.2963
        return Math.cbrt(y)
    }

    /** The color at [tone] with [hue] (radians) and as much of [chroma] as sRGB allows. */
    fun tone(tone: Double, hue: Double, chroma: Double): Int {
        val l = oklabLightness(tone)
        var lo = 0.0
        var hi = chroma
        // The greatest chroma up to the one asked for that stays inside sRGB.
        if (!inGamut(l, hi * cos(hue), hi * sin(hue))) {
            repeat(24) {
                val mid = (lo + hi) / 2
                if (inGamut(l, mid * cos(hue), mid * sin(hue))) lo = mid else hi = mid
            }
            hi = lo
        }
        return ColorMath.fromOklab(l, hi * cos(hue), hi * sin(hue)).argb
    }

    private fun inGamut(l: Double, a: Double, b: Double): Boolean {
        val lp = (l + 0.3963377774 * a + 0.2158037573 * b).let { it * it * it }
        val mp = (l - 0.1055613458 * a - 0.0638541728 * b).let { it * it * it }
        val sp = (l - 0.0894841775 * a - 1.2914855480 * b).let { it * it * it }
        val r = 4.0767416621 * lp - 3.3077115913 * mp + 0.2309699292 * sp
        val g = -1.2684380046 * lp + 2.6097574011 * mp - 0.3413193965 * sp
        val bl = -0.0041960863 * lp - 0.7034186147 * mp + 1.7076147010 * sp
        val e = 1e-4
        return r in -e..1 + e && g in -e..1 + e && bl in -e..1 + e
    }

    /** The scheme for [seed] (ARGB), light or dark. */
    fun from(seed: Int, dark: Boolean): Roles {
        val (_, a, b) = ColorMath.toOklab(ColorMath.fromArgb(seed))
        val hue = atan2(b, a)
        // A grey seed gives grey palettes (its hue means nothing), with a hint of it in the accents.
        val vivid = (hypot(a, b) / 0.04).coerceIn(0.0, 1.0)
        val p = Palette(hue, 0.13 * vivid)
        val s = Palette(hue, 0.045 * vivid)
        val t = Palette(hue + Math.PI / 3, 0.09 * vivid)
        val n = Palette(hue, 0.012 * vivid)
        val nv = Palette(hue, 0.022 * vivid)
        return if (!dark) Roles(
            primary = p.tone(40.0), onPrimary = p.tone(100.0), primaryContainer = p.tone(90.0), onPrimaryContainer = p.tone(10.0),
            secondary = s.tone(40.0), onSecondary = s.tone(100.0), secondaryContainer = s.tone(90.0), onSecondaryContainer = s.tone(10.0),
            tertiary = t.tone(40.0), onTertiary = t.tone(100.0), tertiaryContainer = t.tone(90.0), onTertiaryContainer = t.tone(10.0),
            background = n.tone(98.0), onBackground = n.tone(10.0), surface = n.tone(98.0), onSurface = n.tone(10.0),
            surfaceVariant = nv.tone(90.0), onSurfaceVariant = nv.tone(30.0),
            surfaceContainerLowest = n.tone(100.0), surfaceContainerLow = n.tone(96.0), surfaceContainer = n.tone(94.0),
            surfaceContainerHigh = n.tone(92.0), surfaceContainerHighest = n.tone(90.0),
            inverseSurface = n.tone(20.0), inverseOnSurface = n.tone(95.0), inversePrimary = p.tone(80.0),
            outline = nv.tone(50.0), outlineVariant = nv.tone(80.0),
        ) else Roles(
            primary = p.tone(80.0), onPrimary = p.tone(20.0), primaryContainer = p.tone(30.0), onPrimaryContainer = p.tone(90.0),
            secondary = s.tone(80.0), onSecondary = s.tone(20.0), secondaryContainer = s.tone(30.0), onSecondaryContainer = s.tone(90.0),
            tertiary = t.tone(80.0), onTertiary = t.tone(20.0), tertiaryContainer = t.tone(30.0), onTertiaryContainer = t.tone(90.0),
            background = n.tone(6.0), onBackground = n.tone(90.0), surface = n.tone(6.0), onSurface = n.tone(90.0),
            surfaceVariant = nv.tone(30.0), onSurfaceVariant = nv.tone(80.0),
            surfaceContainerLowest = n.tone(4.0), surfaceContainerLow = n.tone(10.0), surfaceContainer = n.tone(12.0),
            surfaceContainerHigh = n.tone(17.0), surfaceContainerHighest = n.tone(22.0),
            inverseSurface = n.tone(90.0), inverseOnSurface = n.tone(20.0), inversePrimary = p.tone(40.0),
            outline = nv.tone(60.0), outlineVariant = nv.tone(30.0),
        )
    }

    /** Seeds offered as swatches in settings (0 is the built-in olive palette). */
    val PRESETS: List<Pair<String, Int>> = listOf(
        "Olive" to 0,
        "Blue" to 0xFF3F6FD8.toInt(),
        "Teal" to 0xFF1E8A83.toInt(),
        "Green" to 0xFF3C8A3F.toInt(),
        "Amber" to 0xFFC88A12.toInt(),
        "Red" to 0xFFC23B32.toInt(),
        "Rose" to 0xFFC0476F.toInt(),
        "Purple" to 0xFF7A4FC4.toInt(),
        "Grey" to 0xFF777777.toInt(),
    )
}
