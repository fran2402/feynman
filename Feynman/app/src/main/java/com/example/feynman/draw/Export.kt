package com.example.feynman.draw

import com.example.feynman.latex.Box
import com.example.feynman.latex.Draw
import com.example.feynman.latex.MathFont
import com.example.feynman.latex.MathFonts
import com.example.feynman.latex.MathLayout
import com.example.feynman.latex.MathParser
import com.example.feynman.physics.Block
import com.example.feynman.physics.Diagram
import com.example.feynman.physics.Solution
import java.util.Base64

/** Exports: the diagram as SVG (fonts embedded) and the whole solution as a LaTeX document. */
object Export {
    private fun f(x: Float) = "%.2f".format(java.util.Locale.ROOT, x)

    /** Label and line positions shared by every renderer: the box and where its baseline starts. */
    class Label(val box: Box, val x: Float, val y: Float, val color: String)

    fun labels(shapes: DiagramShapes, fonts: MathFonts, labelSize: Float = 14f, momentumSize: Float = 11f): List<Label> {
        val out = ArrayList<Label>()
        val lab = MathLayout(fonts, labelSize)
        val mom = MathLayout(fonts, momentumSize)
        for (l in shapes.diagram.lines) {
            val sh = shapes.shapes[l.id] ?: continue
            sh.momentumLabelAt?.let { at ->
                shapes.momentumTex(l)?.let { tex ->
                    val box = runCatching { mom.layout(MathParser.parse(tex)) }.getOrNull() ?: return@let
                    val dir = sh.labelDir * -1f
                    out.add(Label(box, at.x + dir.x * box.width / 2 - box.width / 2, at.y + dir.y * box.height / 2 + (box.ascent - box.descent) / 2, "#787767"))
                }
            }
            val box = runCatching { lab.layout(MathParser.parse(shapes.labelTex(l))) }.getOrNull() ?: continue
            val at = sh.labelAt
            out.add(Label(box, at.x + sh.labelDir.x * box.width / 2 - box.width / 2, at.y + sh.labelDir.y * box.height / 2 + (box.ascent - box.descent) / 2, "#5B6133"))
        }
        return out
    }

    private fun fontName(m: MathFont) = when (m) { MathFont.Roman -> "cmr"; MathFont.Italic -> "cmi"; MathFont.Cal -> "cmcal"; MathFont.Big -> "cmsize2" }

    /**
     * The diagram as an SVG file, the Computer Modern fonts embedded (as base64) so the labels
     * look the same anywhere. [fontFiles] holds each font's bytes (OTF).
     */
    fun svg(d: Diagram, style: Style, fonts: MathFonts, fontFiles: Map<MathFont, ByteArray>, showMomenta: Boolean = true): String {
        val shapes = DiagramShapes(d, style, showMomenta)
        val labels = labels(shapes, fonts)
        val pts = shapes.shapes.values.flatMap { it.spine + it.strokes.flatten() } + labels.flatMap { listOf(Pt(it.x, it.y - it.box.ascent), Pt(it.x + it.box.width, it.y + it.box.descent)) }
        if (pts.isEmpty()) return "<svg xmlns=\"http://www.w3.org/2000/svg\"/>"
        val pad = 12f
        val minX = pts.minOf { it.x } - pad; val minY = pts.minOf { it.y } - pad
        val w = pts.maxOf { it.x } + pad - minX; val h = pts.maxOf { it.y } + pad - minY
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"${f(minX)} ${f(minY)} ${f(w)} ${f(h)}\" width=\"${f(w * 2)}\" height=\"${f(h * 2)}\">\n")
        sb.append("<style>\n")
        for ((m, bytes) in fontFiles) sb.append("@font-face { font-family: ${fontName(m)}; src: url(data:font/otf;base64,${Base64.getEncoder().encodeToString(bytes)}); }\n")
        sb.append("</style>\n<rect x=\"${f(minX)}\" y=\"${f(minY)}\" width=\"${f(w)}\" height=\"${f(h)}\" fill=\"white\"/>\n")
        sb.append("<g fill=\"none\" stroke=\"#1C1C16\" stroke-width=\"${f(style.stroke)}\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n")
        for (sh in shapes.shapes.values) for (s in sh.strokes) if (s.size >= 2) sb.append("<polyline points=\"${s.joinToString(" ") { "${f(it.x)},${f(it.y)}" }}\"/>\n")
        sb.append("</g>\n<g fill=\"#1C1C16\">\n")
        for (sh in shapes.shapes.values) {
            for (dot in sh.dots) sb.append("<circle cx=\"${f(dot.x)}\" cy=\"${f(dot.y)}\" r=\"${f(style.dotRadius)}\"/>\n")
            for (a in sh.arrows) sb.append("<polygon points=\"${a.joinToString(" ") { "${f(it.x)},${f(it.y)}" }}\"/>\n")
        }
        for (v in shapes.topology.vertices) d.pointOrNull(v)?.let { sb.append("<circle cx=\"${f(it.x)}\" cy=\"${f(it.y)}\" r=\"2.5\"/>\n") }
        sb.append("</g>\n<g fill=\"#787767\" stroke=\"#787767\" stroke-width=\"1\">\n")
        for (sh in shapes.shapes.values) {
            val ms = sh.momentumStroke ?: continue
            sb.append("<line x1=\"${f(ms[0].x)}\" y1=\"${f(ms[0].y)}\" x2=\"${f(ms[1].x)}\" y2=\"${f(ms[1].y)}\"/>\n")
            sb.append("<polygon stroke=\"none\" points=\"${sh.momentumHead!!.joinToString(" ") { "${f(it.x)},${f(it.y)}" }}\"/>\n")
        }
        sb.append("</g>\n")
        for (l in labels) sb.append(boxSvg(l.box, l.x, l.y, l.color))
        sb.append("</svg>\n")
        return sb.toString()
    }

    /** A laid-out formula as SVG text, rules and strokes. */
    fun boxSvg(box: Box, x: Float, y: Float, color: String): String {
        val sb = StringBuilder("<g fill=\"$color\">")
        for (d in box.items) when (d) {
            is Draw.Text -> sb.append("<text x=\"${f(x + d.x)}\" y=\"${f(y + d.y)}\" font-family=\"${fontName(d.font)}\" font-size=\"${f(d.size)}\">${escape(d.text)}</text>")
            is Draw.Rule -> sb.append("<rect x=\"${f(x + d.x)}\" y=\"${f(y + d.y)}\" width=\"${f(d.w)}\" height=\"${f(d.h)}\"/>")
            is Draw.Stroke -> sb.append("<polyline fill=\"none\" stroke=\"$color\" stroke-width=\"${f(d.width)}\" stroke-linecap=\"round\" points=\"${d.points.joinToString(" ") { (a, b) -> "${f(x + a)},${f(y + b)}" }}\"/>")
        }
        return sb.append("</g>\n").toString()
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * The solution as a LaTeX document: the diagram in TikZ-Feynman, then every step, with long
     * equations broken by breqn. Compile with LuaLaTeX (the text has Unicode in it).
     */
    fun latex(d: Diagram, solution: Solution): String {
        val sb = StringBuilder()
        sb.append("% Made by Feynman. Compile with lualatex.\n")
        sb.append("\\documentclass[11pt]{article}\n\\usepackage[margin=2cm]{geometry}\n\\usepackage{amsmath,amssymb,slashed,breqn}\n")
        sb.append("\\usepackage{fontspec}\n\\usepackage[compat=1.1.0]{tikz-feynman}\n\\allowdisplaybreaks\n")
        sb.append("\\title{${texText(d.name.ifEmpty { "Feynman diagram" })}}\n\\date{}\n\\begin{document}\n\\maketitle\n")
        sb.append("\\begin{center}\n").append(TikZ.of(d)).append("\\end{center}\n\n")
        for (step in solution.steps) {
            sb.append("\\section*{${texText(step.title)}}\n")
            for (b in step.blocks) when (b) {
                is Block.Text -> sb.append(texText(b.text)).append("\n\n")
                is Block.Note -> sb.append("\\emph{").append(texText(b.text)).append("}\n\n")
                is Block.Math -> sb.append("\\begin{dmath*}\n").append(b.tex).append("\n\\end{dmath*}\n")
                is Block.Rule -> sb.append("\\noindent ${texText(b.tag)}: \\(${b.what}\\)\n\\begin{dmath*}\n${b.tex}\n\\end{dmath*}\n")
            }
        }
        sb.append("\\end{document}\n")
        return sb.toString()
    }

    /** Text with its \( … \) maths kept, and LaTeX's special characters escaped outside it. */
    private fun texText(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val open = s.indexOf("\\(", i)
            val chunk = if (open < 0) s.substring(i) else s.substring(i, open)
            out.append(chunk.replace("&", "\\&").replace("%", "\\%").replace("#", "\\#").replace("_", "\\_"))
            if (open < 0) break
            val close = s.indexOf("\\)", open + 2).let { if (it < 0) s.length else it }
            out.append(s, open, minOf(s.length, close + 2))
            i = close + 2
        }
        return out.toString()
    }
}
