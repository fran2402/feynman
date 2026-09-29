package com.example.feynman.draw

import com.example.feynman.physics.Diagram
import com.example.feynman.physics.LineStyle
import com.example.feynman.physics.SM
import com.example.feynman.physics.Topology
import kotlin.math.atan
import kotlin.math.roundToInt

/** The diagram as TikZ-Feynman code (\usepackage{tikz-feynman}), with the points where they're drawn. */
object TikZ {
    fun of(d: Diagram): String {
        val topo = Topology.of(d)
        val used = d.points.filter { d.degree(it.id) > 0 }
        if (used.isEmpty()) return "% empty diagram"
        val minX = used.minOf { it.x }; val minY = used.minOf { it.y }
        fun cm(v: Float) = "%.2f".format(java.util.Locale.ROOT, v / 60f)
        val sb = StringBuilder()
        sb.append("\\begin{tikzpicture}\n\\begin{feynman}\n")
        for (p in used) {
            val ext = topo.externals.firstOrNull { it.point == p.id }
            val label = if (ext != null) " {\\(${ext.tex}\\)}" else ""
            val kind = if (d.degree(p.id) >= 2) "\\vertex[dot]" else "\\vertex"
            sb.append("  $kind (v${p.id}) at (${cm(p.x - minX)}, ${cm(-(p.y - minY))})$label;\n")
        }
        sb.append("  \\diagram* {\n")
        for (l in d.lines) {
            val p = SM.byId(l.particle) ?: continue
            val style = when (p.style) {
                LineStyle.Fermion -> "fermion"
                LineStyle.Boson -> if (p.oriented) "charged boson" else "boson"
                LineStyle.Gluon -> "gluon"
                LineStyle.Scalar -> if (p.oriented) "charged scalar" else "scalar"
                LineStyle.Ghost -> "ghost"
            }
            val opts = ArrayList<String>()
            opts.add(style)
            if (l.isSelfLoop) opts.add("out=135, in=45, loop, min distance=1.2cm")
            else if (l.bend != 0f) {
                val deg = (Math.toDegrees(2 * atan(2 * l.bend.toDouble()))).roundToInt()
                // A positive bend bulges to the left of the drawing direction.
                opts.add(if (deg > 0) "bend left=$deg" else "bend right=${-deg}")
            }
            if (topo.external(l.id) == null) opts.add("edge label=\\(${p.tex}\\)")
            sb.append("    (v${l.from}) -- [${opts.joinToString(", ")}] (v${l.to}),\n")
        }
        sb.append("  };\n\\end{feynman}\n\\end{tikzpicture}\n")
        return sb.toString()
    }
}
