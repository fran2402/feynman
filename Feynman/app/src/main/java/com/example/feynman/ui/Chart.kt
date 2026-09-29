package com.example.feynman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feynman.latex.MathLayout
import com.example.feynman.latex.MathParser
import com.example.feynman.physics.Numbers
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A line chart of one curve, with ticks and Computer Modern labels, in CAS Calculator's colors. */
@Composable
fun LineChart(xs: DoubleArray, ys: DoubleArray, xLabel: String, yLabel: String, modifier: Modifier = Modifier, logY: Boolean = false) {
    val fonts = LocalMathFonts.current
    val colors = MaterialTheme.colorScheme
    val line = colors.primary
    val axis = inkVariant()
    val grid = colors.outlineVariant.copy(alpha = 0.5f)
    val density = LocalDensity.current.density
    Column(modifier) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { MathTex(yLabel, fontSize = 13.sp, color = axis, wrap = false) }
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val left = 52f * density; val bottom = size.height - 22f * density; val top = 8f * density; val right = size.width - 8f * density
            val ysPlot = if (logY) ys.map { if (it > 0) log10(it) else Double.NaN }.toDoubleArray() else ys
            val finite = ysPlot.filter { it.isFinite() }
            if (finite.isEmpty()) return@Canvas
            var lo = finite.min(); var hi = finite.max()
            if (hi - lo < 1e-300) { hi += 1.0; lo -= 1.0 }
            if (!logY && lo > 0 && lo < hi * 0.3) lo = 0.0
            val x0 = xs.first(); val x1 = xs.last()
            fun sx(x: Double) = (left + (x - x0) / (x1 - x0) * (right - left)).toFloat()
            fun sy(y: Double) = (bottom - (y - lo) / (hi - lo) * (bottom - top)).toFloat()
            val small = MathLayout(fonts, 10f * density)
            // Ticks at 1, 2, 5 × 10ⁿ.
            fun ticks(a: Double, b: Double): List<Double> {
                val span = b - a
                val raw = span / 4
                val mag = 10.0.pow(floor(log10(raw)))
                val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * mag }.first { it >= raw }
                val out = ArrayList<Double>()
                var t = kotlin.math.ceil(a / step) * step
                while (t <= b + step * 1e-9) { out.add(t); t += step }
                return out
            }
            for (t in ticks(lo, hi)) {
                val y = sy(t)
                drawLine(grid, Offset(left, y), Offset(right, y), 1f * density)
                val label = if (logY) "10^{${Numbers.format(t).replace("−", "-")}}" else Numbers.texOf(t)
                val box = small.layout(MathParser.parse(label))
                drawMath(fonts, box, left - box.width - 4 * density, y + (box.ascent - box.descent) / 2, axis)
            }
            for (t in ticks(x0, x1)) {
                val x = sx(t)
                drawLine(grid, Offset(x, top), Offset(x, bottom), 1f * density)
                val box = small.layout(MathParser.parse(Numbers.texOf(if (abs(t) < 1e-12) 0.0 else t)))
                drawMath(fonts, box, x - box.width / 2, bottom + box.ascent + 4 * density, axis)
            }
            drawLine(axis, Offset(left, bottom), Offset(right, bottom), 1.2f * density)
            drawLine(axis, Offset(left, top), Offset(left, bottom), 1.2f * density)
            val path = Path()
            var started = false
            for (i in xs.indices) {
                val y = ysPlot[i]
                if (!y.isFinite()) { started = false; continue }
                if (!started) { path.moveTo(sx(xs[i]), sy(y)); started = true } else path.lineTo(sx(xs[i]), sy(y))
            }
            drawPath(path, line, style = Stroke(width = 2.2f * density))
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MathTex(xLabel, fontSize = 13.sp, color = axis, wrap = false) }
    }
}
