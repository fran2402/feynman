package com.example.feynman.draw

import com.example.feynman.physics.Diagram
import com.example.feynman.physics.Line
import com.example.feynman.physics.LineStyle
import com.example.feynman.physics.Particle
import com.example.feynman.physics.SM
import com.example.feynman.physics.Topology
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * How each kind of line is drawn, as in Romão & Silva's figures: fermions solid with an arrow
 * on the line, γ/W/Z as waves, gluons as a tighter wave (or coils), scalars dashed, ghosts
 * dotted with an arrow, and momenta as short arrows beside the line. Plain geometry, drawn by
 * the app's Canvas, the previews and the SVG and TikZ exports alike.
 */

data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
    val length get() = hypot(x, y)
    fun normalized() = if (length == 0f) Pt(1f, 0f) else this * (1f / length)
    /** Rotated a quarter turn counterclockwise (on screen, where y points down, clockwise). */
    val perp get() = Pt(-y, x)
}

/** Sizes in the canvas's units (dp in the app). */
data class Style(
    val stroke: Float = 1.6f,
    val amplitude: Float = 4.2f,
    val wavelength: Float = 11f,
    val gluonAmplitude: Float = 3.4f,
    val gluonWavelength: Float = 6.2f,
    val gluonCoils: Boolean = false,
    val dash: Float = 6f,
    val gap: Float = 4f,
    val dotSpacing: Float = 4.2f,
    val dotRadius: Float = 1.25f,
    val arrow: Float = 7f,
    val momentumLength: Float = 18f,
    val momentumOffset: Float = 11f,
    val labelOffset: Float = 15f,
    val selfLoopRadius: Float = 26f,
)

/** A drawn line: strokes, dots, filled arrowheads, and where its labels go. */
class LineShape(
    val strokes: List<List<Pt>>,
    val dots: List<Pt>,
    val arrows: List<List<Pt>>,
    /** The particle label's position and the side it's on (a unit vector away from the line). */
    val labelAt: Pt,
    val labelDir: Pt,
    /** The momentum arrow (a stroke and its head) and its label position, if shown. */
    val momentumStroke: List<Pt>?,
    val momentumHead: List<Pt>?,
    val momentumLabelAt: Pt?,
    /** Points along the middle of the line, for hit-testing. */
    val spine: List<Pt>,
)

object Geometry {
    /** The line's centre curve from start to end: straight, a circular arc when bent, a circle for a self-loop. */
    fun spine(a: Pt, b: Pt, bend: Float, selfLoop: Boolean, style: Style): List<Pt> {
        if (selfLoop) {
            // A circle touching the vertex, on the side the bend points to (up by default).
            val r = style.selfLoopRadius * (if (abs(bend) > 0.01f) abs(bend) / 0.5f else 1f).coerceIn(0.6f, 2.5f)
            val dir = if (bend >= 0) -1f else 1f
            val c = Pt(a.x, a.y + dir * r)
            val n = 72
            val start = atan2(a.y - c.y, a.x - c.x)
            return (0..n).map { i ->
                val t = start + 2 * PI.toFloat() * i / n
                Pt(c.x + r * cos(t), c.y + r * sin(t))
            }
        }
        val d = b - a
        val len = d.length
        if (abs(bend) < 1e-3f || len < 1f) return listOf(a, b)
        // Sagitta h = bend·len; the arc's radius and centre follow.
        val h = bend * len
        val r = (len * len / 4 + h * h) / (2 * abs(h))
        val mid = (a + b) * 0.5f
        val n = d.normalized().perp
        val centre = mid + n * ((r - abs(h)) * (if (h > 0) -1f else 1f))
        val a0 = atan2(a.y - centre.y, a.x - centre.x)
        var a1 = atan2(b.y - centre.y, b.x - centre.x)
        // Go the short way round for small bends, the long way (through the bulge) otherwise.
        val bulge = mid + n * (-h)
        val ab = atan2(bulge.y - centre.y, bulge.x - centre.x)
        var sweep = a1 - a0
        fun norm(x: Float): Float { var y = x; while (y > PI) y -= (2 * PI).toFloat(); while (y < -PI) y += (2 * PI).toFloat(); return y }
        sweep = norm(sweep)
        val toBulge = norm(ab - a0)
        if (toBulge * sweep < 0 || abs(toBulge) > abs(sweep)) sweep = if (sweep > 0) sweep - 2 * PI.toFloat() else sweep + 2 * PI.toFloat()
        val steps = max(12, (abs(sweep) * r / 3f).roundToInt())
        a1 = a0 + sweep
        return (0..steps).map { i -> val t = a0 + (a1 - a0) * i / steps; Pt(centre.x + r * cos(t), centre.y + r * sin(t)) }
    }

    /** Arc-length positions along a polyline. */
    private class Walker(val pts: List<Pt>) {
        val cum = FloatArray(pts.size)
        init { for (i in 1 until pts.size) cum[i] = cum[i - 1] + (pts[i] - pts[i - 1]).length }
        val length get() = cum.last()
        fun at(s: Float): Pair<Pt, Pt> {
            val sc = s.coerceIn(0f, length)
            var i = 1
            while (i < pts.size - 1 && cum[i] < sc) i++
            val seg = pts[i] - pts[i - 1]
            val segLen = seg.length
            val t = if (segLen == 0f) 0f else (sc - cum[i - 1]) / segLen
            return (pts[i - 1] + seg * t) to seg.normalized()
        }
    }

    fun shape(
        p: Particle?,
        a: Pt,
        b: Pt,
        bend: Float,
        selfLoop: Boolean,
        style: Style,
        momentum: Boolean,
        /** The momentum arrow's direction along the line: +1 start → end, −1 back. */
        momentumSign: Int = 1,
        labelSide: Float = 1f,
    ): LineShape {
        val sp = spine(a, b, bend, selfLoop, style)
        val w = Walker(sp)
        val len = w.length
        val strokes = ArrayList<List<Pt>>()
        val dots = ArrayList<Pt>()
        val arrows = ArrayList<List<Pt>>()
        val lineStyle = p?.style ?: LineStyle.Fermion
        when (lineStyle) {
            LineStyle.Fermion -> strokes.add(sp)
            LineStyle.Boson -> strokes.add(wave(w, style.amplitude, style.wavelength))
            LineStyle.Gluon -> strokes.add(if (style.gluonCoils) coils(w, style) else wave(w, style.gluonAmplitude, style.gluonWavelength))
            LineStyle.Scalar -> {
                // Dashes centred so the ends look alike.
                val period = style.dash + style.gap
                val n = max(1, ((len + style.gap) / period).roundToInt())
                val dash = (len + style.gap) / n - style.gap
                for (i in 0 until n) {
                    val s0 = i * (dash + style.gap)
                    val s1 = s0 + dash
                    val seg = (0..6).map { k -> w.at(s0 + (s1 - s0) * k / 6).first }
                    strokes.add(seg)
                }
            }
            LineStyle.Ghost -> {
                val n = max(2, (len / style.dotSpacing).roundToInt())
                for (i in 0..n) dots.add(w.at(len * i / n).first)
            }
        }
        // Arrow on the line for fermions, ghosts and charged lines (W±, φ±): the direction of flow.
        val oriented = p?.oriented ?: true
        if (oriented) {
            val (mid, dir) = w.at(len / 2)
            val s = if (lineStyle == LineStyle.Boson) style.arrow * 0.85f else style.arrow
            val tip = mid + dir * (s * 0.55f)
            val back = mid - dir * (s * 0.45f)
            val n = dir.perp
            val onWave = lineStyle == LineStyle.Boson || lineStyle == LineStyle.Gluon
            // On a wave the arrow sits beside the line so it doesn't clash with it.
            val off = if (onWave) n * (-(style.amplitude + s * 0.7f) * labelSide) else Pt(0f, 0f)
            arrows.add(listOf(tip + off, back + n * (s * 0.42f) + off, back - n * (s * 0.42f) + off))
        }
        // The label sits beside the middle, on the side away from the momentum arrow.
        val (mid, dir) = w.at(len / 2)
        val side = if (selfLoop) (mid - a).normalized() else dir.perp * labelSide
        val waveExtra = if (lineStyle == LineStyle.Boson || lineStyle == LineStyle.Gluon) style.amplitude else 0f
        val labelAt = mid + side * (style.labelOffset + waveExtra)
        var mStroke: List<Pt>? = null
        var mHead: List<Pt>? = null
        var mLabel: Pt? = null
        if (momentum && len > style.momentumLength * 1.6f) {
            val other = side * -1f
            val centre = mid + other * (style.momentumOffset + waveExtra)
            val d = dir * (if (momentumSign >= 0) 1f else -1f)
            val half = style.momentumLength / 2
            val s0 = centre - d * half
            val s1 = centre + d * half
            mStroke = listOf(s0, s1)
            val n = d.perp
            mHead = listOf(s1 + d * 1.5f, s1 - d * 4.5f + n * 2.6f, s1 - d * 4.5f - n * 2.6f)
            mLabel = centre + other * 9f
        }
        return LineShape(strokes, dots, arrows, labelAt, side, mStroke, mHead, mLabel, sp)
    }

    /** A sine wave along the spine with a whole number of half-waves, starting and ending on it. */
    private fun wave(w: Walker, amp: Float, lambda: Float): List<Pt> {
        val len = w.length
        val halfWaves = max(2, (2 * len / lambda).roundToInt())
        val k = PI.toFloat() * halfWaves / len
        val n = max(40, (len / 1.2f).roundToInt())
        return (0..n).map { i ->
            val s = len * i / n
            val (p, d) = w.at(s)
            p + d.perp * (amp * sin(k * s))
        }
    }

    /** Gluon coils: a curtate cycloid along the spine. */
    private fun coils(w: Walker, style: Style): List<Pt> {
        val len = w.length
        val r = style.gluonAmplitude * 1.3f
        val turns = max(2, (len / (style.gluonWavelength * 1.2f)).roundToInt())
        val n = turns * 28
        val out = ArrayList<Pt>()
        for (i in 0..n) {
            val th = 2 * PI.toFloat() * turns * i / n
            // Advance along the line, looping back a little on each turn.
            val s = len * i / n - r * 0.9f * sin(th)
            val (p, d) = w.at(s.coerceIn(0f, len))
            out.add(p + d.perp * (r * (1 - cos(th))) * 0.9f)
        }
        return out
    }

    /** Where a tap at [q] is closest on the line's spine. */
    fun distance(shape: LineShape, q: Pt): Float {
        var best = Float.MAX_VALUE
        val s = shape.spine
        for (i in 1 until s.size) {
            val a = s[i - 1]; val b = s[i]
            val d = b - a
            val l2 = d.x * d.x + d.y * d.y
            val t = if (l2 == 0f) 0f else (((q.x - a.x) * d.x + (q.y - a.y) * d.y) / l2).coerceIn(0f, 1f)
            best = minOf(best, (a + d * t - q).length)
        }
        return best
    }
}

/** A diagram's lines laid out, with labels chosen from its topology. */
class DiagramShapes(val diagram: Diagram, val style: Style, val showMomenta: Boolean = true) {
    val topology = Topology.of(diagram)
    val shapes: Map<Int, LineShape> = diagram.lines.associate { l -> l.id to shapeOf(l) }

    private fun shapeOf(l: Line): LineShape {
        val a = diagram.pointOrNull(l.from) ?: return Geometry.shape(null, Pt(0f, 0f), Pt(0f, 0f), 0f, false, style, false)
        val b = diagram.pointOrNull(l.to) ?: return Geometry.shape(null, Pt(0f, 0f), Pt(0f, 0f), 0f, false, style, false)
        val p = SM.byId(l.particle)
        val pa = Pt(a.x, a.y)
        val pb = Pt(b.x, b.y)
        // Labels above horizontal lines, and on the outside of bent ones.
        val d = pb - pa
        var side = if (abs(d.x) >= abs(d.y)) (if (d.x >= 0) -1f else 1f) else (if (d.y >= 0) 1f else -1f)
        if (abs(l.bend) > 0.01f) side = if (l.bend > 0) -1f else 1f
        // A momentum arrow points the way the momentum flows (incoming externals inward).
        val q = topology.momenta[l.id]
        val sign = if (q != null && q.values.firstOrNull()?.signum ?: 1 < 0) -1 else 1
        return Geometry.shape(p, pa, pb, l.bend, l.isSelfLoop, style, showMomenta && l.showMomentum, sign, side)
    }

    /** The particle's name on the line: e^-, W^+ (by the direction it's drawn), γ, … */
    fun labelTex(l: Line): String {
        val p = SM.byId(l.particle) ?: return l.particle
        val ext = topology.external(l.id)
        if (ext != null) return ext.tex
        return p.tex
    }

    /** The momentum on a line, as LaTeX: p_1, k, p_1 + p_2 − k, … written the way it flows. */
    fun momentumTex(l: Line): String? {
        val q = topology.momenta[l.id] ?: return null
        if (q.isEmpty()) return null
        val flip = q.values.first().signum < 0
        return com.example.feynman.physics.MomNames.tex(if (flip) q.mapValues { -it.value } else q)
    }
}
