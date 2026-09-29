package com.example.feynman.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.example.feynman.R
import com.example.feynman.latex.Box
import com.example.feynman.latex.Draw
import com.example.feynman.latex.MathFont
import com.example.feynman.latex.MathFonts
import com.example.feynman.latex.MathLayout
import com.example.feynman.latex.MathParser

/** The Computer Modern fonts, measured with Android's Paint. */
class AndroidMathFonts(context: Context) : MathFonts {
    private val faces: Map<MathFont, Typeface?> = mapOf(
        MathFont.Roman to ResourcesCompat.getFont(context, R.font.cm_main),
        MathFont.Italic to ResourcesCompat.getFont(context, R.font.cm_italic),
        MathFont.Cal to ResourcesCompat.getFont(context, R.font.cm_cal),
        MathFont.Big to ResourcesCompat.getFont(context, R.font.cm_size2),
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = Rect()
    private val widths = HashMap<String, Float>()
    private val bounds = HashMap<String, Pair<Float, Float>>()

    fun paint(font: MathFont, size: Float, color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = faces[font]; textSize = size; this.color = color
    }

    @Synchronized
    override fun width(text: String, font: MathFont, size: Float): Float = widths.getOrPut("$font|$text") {
        paint.typeface = faces[font]; paint.textSize = 100f
        paint.measureText(text) / 100f
    } * size

    @Synchronized
    private fun inkOf(text: String, font: MathFont): Pair<Float, Float> = bounds.getOrPut("$font|$text") {
        paint.typeface = faces[font]; paint.textSize = 100f
        paint.getTextBounds(text, 0, text.length, rect)
        (-rect.top / 100f).coerceAtLeast(0f) to (rect.bottom / 100f).coerceAtLeast(0f)
    }

    override fun ascent(text: String, font: MathFont, size: Float) = inkOf(text, font).first * size
    override fun descent(text: String, font: MathFont, size: Float) = inkOf(text, font).second * size
    // The fonts' own character tables (Paint.hasGlyph also counts system fallback fonts).
    override fun has(font: MathFont, codePoint: Int): Boolean = when (font) {
        MathFont.Italic -> codePoint in 'a'.code..'z'.code || codePoint in 'A'.code..'Z'.code || codePoint in 0x3B1..0x3C9 ||
            codePoint in listOf(0x3D1, 0x3D5, 0x3D6, 0x3F1, 0x3F5) || codePoint in listOf(0x393, 0x394, 0x398, 0x39B, 0x39E, 0x3A0, 0x3A3, 0x3A5, 0x3A6, 0x3A8, 0x3A9)
        MathFont.Cal -> codePoint in 'A'.code..'Z'.code
        else -> true
    }
}

/** Text and maths color: white in dark mode, onSurface in light mode. */
@Composable
fun ink(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.surface.luminance() < 0.5f) Color.White else c.onSurface
}

/** Secondary text and icons: white at 80% in dark mode, onSurfaceVariant in light mode. */
@Composable
fun inkVariant(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.surface.luminance() < 0.5f) Color.White.copy(alpha = 0.8f) else c.onSurfaceVariant
}

/** Whether the app is shown dark (the setting can differ from the system's). */
@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

val LocalMathFonts = staticCompositionLocalOf<AndroidMathFonts> { error("Wrap the UI in FeynmanTheme") }

/** Draws a laid-out formula with its baseline at y = [y]. */
fun DrawScope.drawMath(fonts: AndroidMathFonts, box: Box, x: Float, y: Float, color: Color) {
    val argb = color.toArgb()
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb; style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for (d in box.items) when (d) {
            is Draw.Text -> nc.drawText(d.text, x + d.x, y + d.y, fonts.paint(d.font, d.size, argb))
            is Draw.Rule -> nc.drawRect(x + d.x, y + d.y, x + d.x + d.w, y + d.y + d.h, fill)
            is Draw.Stroke -> {
                stroke.strokeWidth = d.width
                val path = android.graphics.Path()
                d.points.forEachIndexed { i, (a, b) -> if (i == 0) path.moveTo(x + a, y + b) else path.lineTo(x + a, y + b) }
                nc.drawPath(path, stroke)
            }
        }
    }
}

/**
 * A formula in Computer Modern. Long ones break into lines at +, − and = to fit the width;
 * anything that still doesn't fit scrolls sideways.
 */
@Composable
fun MathTex(tex: String, modifier: Modifier = Modifier, fontSize: TextUnit = 18.sp, color: Color = LocalContentColor.current.takeOrElse { ink() }, wrap: Boolean = true) {
    val fonts = LocalMathFonts.current
    val density = LocalDensity.current
    val px = with(density) { (fontSize * AppSettings.mathScale).toPx() }
    BoxWithConstraints(modifier) {
        val maxW = if (wrap && constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.MAX_VALUE
        val box = remember(tex, px, maxW) {
            runCatching {
                val layout = MathLayout(fonts, px)
                if (maxW < Float.MAX_VALUE) layout.lines(MathParser.parse(tex), maxW) else layout.layout(MathParser.parse(tex))
            }.getOrElse { MathLayout(fonts, px).layout(MathParser.parse("\\text{(can't show this)}")) }
        }
        val pad = px * 0.1f
        val w = with(density) { (box.width + 2 * pad).toDp() }
        val h = with(density) { (box.height + 2 * pad).toDp() }
        val canvas = Modifier.size(w, h)
        val scroll = if (box.width > maxW) Modifier.horizontalScroll(rememberScrollState()) else Modifier
        androidx.compose.foundation.layout.Box(scroll) {
            Canvas(canvas) { drawMath(fonts, box, pad, pad + box.ascent, color) }
        }
    }
}

/** Words with \( … \) maths in them, wrapped like a paragraph. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = LocalContentColor.current.takeOrElse { ink() }) {
    val parts = remember(text) { splitMath(text) }
    if (parts.none { it.second }) { Text(text, modifier, style = style, color = color); return }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(0.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        for ((s, math) in parts) {
            if (math) MathTex(s, fontSize = style.fontSize * 1.05f, color = color, wrap = false)
            else s.split(" ").forEachIndexed { i, word ->
                if (word.isNotEmpty() || i > 0) Text(if (i < s.split(" ").size - 1) "$word " else word, style = style, color = color)
            }
        }
    }
}

/** Splits "a \(x\) b" into text and maths pieces. */
fun splitMath(text: String): List<Pair<String, Boolean>> {
    val out = ArrayList<Pair<String, Boolean>>()
    var i = 0
    while (i < text.length) {
        val open = text.indexOf("\\(", i)
        if (open < 0) { out.add(text.substring(i) to false); break }
        if (open > i) out.add(text.substring(i, open) to false)
        val close = text.indexOf("\\)", open + 2)
        if (close < 0) { out.add(text.substring(open) to false); break }
        out.add(text.substring(open + 2, close) to true)
        i = close + 2
    }
    return out
}
