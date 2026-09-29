package com.example.feynman.ui

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colour conversions for the colour picker: hex, RGB (0–255), HSV (hue in
 * degrees, saturation and value 0–100) and OKLab (L 0–1, a and b about ±0.4,
 * Björn Ottosson's perceptual space). Plain Kotlin, so it's tested directly.
 */
object ColorMath {
    data class Rgb(val r: Int, val g: Int, val b: Int) {
        init { require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB values go from 0 to 255" } }
        val argb: Int get() = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun fromArgb(argb: Int) = Rgb((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    // ---- Hex
    fun hex(c: Rgb) = "#%02X%02X%02X".format(c.r, c.g, c.b)

    /** #RRGGBB, RRGGBB or the short #RGB. */
    fun parseHex(text: String): Rgb? {
        val h = text.trim().removePrefix("#")
        val full = when (h.length) { 3 -> h.map { "$it$it" }.joinToString(""); 6 -> h; else -> return null }
        val v = full.toIntOrNull(16) ?: return null
        return Rgb((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
    }

    // ---- HSV
    fun toHsv(c: Rgb): Triple<Double, Double, Double> {
        val r = c.r / 255.0; val g = c.g / 255.0; val b = c.b / 255.0
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        val h = when {
            d == 0.0 -> 0.0
            max == r -> 60 * (((g - b) / d).mod(6.0))
            max == g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        return Triple(h, if (max == 0.0) 0.0 else d / max * 100, max * 100)
    }

    fun fromHsv(h: Double, s: Double, v: Double): Rgb {
        val hh = h.mod(360.0); val ss = (s / 100).coerceIn(0.0, 1.0); val vv = (v / 100).coerceIn(0.0, 1.0)
        val c = vv * ss
        val x = c * (1 - abs((hh / 60).mod(2.0) - 1))
        val m = vv - c
        val (r, g, b) = when {
            hh < 60 -> Triple(c, x, 0.0); hh < 120 -> Triple(x, c, 0.0); hh < 180 -> Triple(0.0, c, x)
            hh < 240 -> Triple(0.0, x, c); hh < 300 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x)
        }
        return Rgb(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    // ---- OKLab (via linear sRGB)
    private fun toLinear(u: Double) = if (u <= 0.04045) u / 12.92 else ((u + 0.055) / 1.055).pow(2.4)
    private fun toGamma(u: Double) = if (u <= 0.0031308) 12.92 * u else 1.055 * u.pow(1 / 2.4) - 0.055

    fun toOklab(c: Rgb): Triple<Double, Double, Double> {
        val r = toLinear(c.r / 255.0); val g = toLinear(c.g / 255.0); val b = toLinear(c.b / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Triple(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    /** OKLab to the nearest sRGB colour (out-of-gamut values are clipped). */
    fun fromOklab(lightness: Double, a: Double, b: Double): Rgb {
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        fun channel(u: Double) = (toGamma(u.coerceIn(0.0, 1.0)) * 255).roundToInt().coerceIn(0, 255)
        return Rgb(channel(r), channel(g), channel(bl))
    }
}
