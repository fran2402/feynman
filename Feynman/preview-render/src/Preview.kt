import com.example.feynman.draw.DiagramShapes
import com.example.feynman.draw.Style
import com.example.feynman.latex.Box
import com.example.feynman.latex.Draw
import com.example.feynman.latex.MathFont
import com.example.feynman.latex.MathFonts
import com.example.feynman.latex.MathLayout
import com.example.feynman.latex.MathParser
import com.example.feynman.physics.Block
import com.example.feynman.physics.SolveOptions
import com.example.feynman.physics.Solver
import com.example.feynman.physics.Templates
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/*
 * Renders every template diagram and its worked solution to PNG with Java2D, using the same
 * layout code as the app (latex/, draw/), to check them by eye without an Android device.
 * Run from Feynman/jvm: gradle run --args="out-dir".
 */

private val fontDir = File("../app/src/main/res/font")

class AwtFonts : MathFonts {
    private val base = mapOf(
        MathFont.Roman to Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "cm_main.otf")),
        MathFont.Italic to Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "cm_italic.otf")),
        MathFont.Cal to Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "cm_cal.otf")),
        MathFont.Big to Font.createFont(Font.TRUETYPE_FONT, File(fontDir, "cm_size2.otf")),
    )
    private val frc = FontRenderContext(null, true, true)
    fun font(f: MathFont, size: Float): Font = base[f]!!.deriveFont(size)
    override fun width(text: String, font: MathFont, size: Float) = font(font, size).getStringBounds(text, frc).width.toFloat()
    override fun ascent(text: String, font: MathFont, size: Float): Float {
        val gv = font(font, size).createGlyphVector(frc, text)
        return (-gv.visualBounds.minY).toFloat().coerceAtLeast(0f)
    }
    override fun descent(text: String, font: MathFont, size: Float): Float {
        val gv = font(font, size).createGlyphVector(frc, text)
        return gv.visualBounds.maxY.toFloat().coerceAtLeast(0f)
    }
    override fun has(font: MathFont, codePoint: Int) = base[font]!!.canDisplay(codePoint)
}

fun drawBox(g: Graphics2D, fonts: AwtFonts, box: Box, x: Float, y: Float, color: Color) {
    g.color = color
    for (d in box.items) when (d) {
        is Draw.Text -> { g.font = fonts.font(d.font, d.size); g.drawString(d.text, x + d.x, y + d.y) }
        is Draw.Rule -> g.fill(java.awt.geom.Rectangle2D.Float(x + d.x, y + d.y, d.w, d.h))
        is Draw.Stroke -> {
            g.stroke = BasicStroke(d.width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            val p = GeneralPath()
            d.points.forEachIndexed { i, (a, b) -> if (i == 0) p.moveTo(x + a, y + b) else p.lineTo(x + a, y + b) }
            g.draw(p)
        }
    }
}

fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "preview-out").apply { mkdirs() }
    val fonts = AwtFonts()
    val ink = Color(0x1C, 0x1C, 0x16)
    val accent = Color(0x5B, 0x61, 0x33)
    val scale = 2f
    // With "tidy" as the second argument: each template scrambled, then tidied.
    val list = if (args.getOrNull(1) != "tidy") Templates.all else Templates.all.flatMap { t ->
        val r = java.util.Random(3)
        val ext = com.example.feynman.physics.Topology.of(t.diagram).externals.associate { it.point to it.incoming }
        val messy = t.diagram.copy(points = t.diagram.points.map { p ->
            p.copy(x = p.x + r.nextInt(160) - 80, y = p.y + r.nextInt(160) - 80,
                io = ext[p.id]?.let { if (it) com.example.feynman.physics.Io.In else com.example.feynman.physics.Io.Out } ?: p.io)
        })
        listOf(
            Templates.Template(t.name + " (messy)", t.group, com.example.feynman.physics.Editing.normalized(messy), t.about),
            Templates.Template(t.name + " (tidied)", t.group, com.example.feynman.physics.Editing.normalized(com.example.feynman.physics.Editing.tidy(messy)), t.about),
        )
    }
    for ((ti, t) in list.withIndex()) {
        val sol = Solver.solve(t.diagram, SolveOptions())
        val width = 1100
        val layout = MathLayout(fonts, 17f * scale)
        val small = MathLayout(fonts, 13f * scale)
        // Lay everything out first to know the height.
        class Item(val box: Box?, val text: String?, val color: Color, val gap: Float)
        val items = ArrayList<Item>()
        for (s in sol.steps) {
            items.add(Item(null, s.title, accent, 18f * scale))
            for (b in s.blocks) when (b) {
                is Block.Math -> items.add(Item(runCatching { layout.lines(MathParser.parse(b.tex), (width - 80) * 1f) }.getOrElse { small.lines(MathParser.parse("\\text{LAYOUT ERROR}"), 800f) }, null, ink, 8f * scale))
                is Block.Rule -> {
                    items.add(Item(null, "${b.what.replace(Regex("\\\\[a-z]+|[{}^_]"), "")}  (eq. ${b.eq})", Color.GRAY, 6f * scale))
                    items.add(Item(layout.lines(MathParser.parse(b.tex), (width - 80) * 1f), null, ink, 4f * scale))
                }
                is Block.Text -> items.add(Item(null, b.text, Color.DARK_GRAY, 6f * scale))
                is Block.Note -> items.add(Item(null, "Note: " + b.text, Color(0x9A, 0x40, 0x20), 6f * scale))
            }
        }
        val diagramH = 300 * scale
        var height = diagramH + 60
        for (it in items) height += it.gap + (it.box?.height ?: (18f * scale)) + 6
        val img = BufferedImage(width, height.toInt() + 40, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.color = Color(0xFC, 0xF9, 0xEE)
        g.fillRect(0, 0, img.width, img.height)
        // The diagram.
        val shapes = DiagramShapes(t.diagram, Style())
        val ox = 60f; val oy = 60f
        g.scale(scale.toDouble(), scale.toDouble())
        g.color = ink
        val labelLayout = MathLayout(fonts, 14f)
        for (l in t.diagram.lines) {
            val sh = shapes.shapes[l.id] ?: continue
            g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.color = ink
            for (s in sh.strokes) {
                val p = GeneralPath()
                s.forEachIndexed { i, pt -> if (i == 0) p.moveTo(ox + pt.x, oy + pt.y) else p.lineTo(ox + pt.x, oy + pt.y) }
                g.draw(p)
            }
            for (d in sh.dots) g.fill(java.awt.geom.Ellipse2D.Float(ox + d.x - 1.25f, oy + d.y - 1.25f, 2.5f, 2.5f))
            for (a in sh.arrows) {
                val p = GeneralPath(); a.forEachIndexed { i, pt -> if (i == 0) p.moveTo(ox + pt.x, oy + pt.y) else p.lineTo(ox + pt.x, oy + pt.y) }; p.closePath(); g.fill(p)
            }
            sh.momentumStroke?.let { ms ->
                g.color = Color(0x78, 0x77, 0x67)
                g.stroke = BasicStroke(1f)
                g.draw(java.awt.geom.Line2D.Float(ox + ms[0].x, oy + ms[0].y, ox + ms[1].x, oy + ms[1].y))
                val p = GeneralPath(); sh.momentumHead!!.forEachIndexed { i, pt -> if (i == 0) p.moveTo(ox + pt.x, oy + pt.y) else p.lineTo(ox + pt.x, oy + pt.y) }; p.closePath(); g.fill(p)
                shapes.momentumTex(l)?.let { mt ->
                    val b = MathLayout(fonts, 11f).layout(MathParser.parse(mt))
                    val at = sh.momentumLabelAt!!
                    drawBox(g, fonts, b, ox + at.x - b.width / 2, oy + at.y + (b.ascent - b.descent) / 2, Color(0x78, 0x77, 0x67))
                }
            }
            val lb = labelLayout.layout(MathParser.parse(shapes.labelTex(l)))
            drawBox(g, fonts, lb, ox + sh.labelAt.x - lb.width / 2, oy + sh.labelAt.y + (lb.ascent - lb.descent) / 2, accent)
        }
        for (v in shapes.topology.vertices) {
            val p = t.diagram.point(v)
            g.color = ink
            g.fill(java.awt.geom.Ellipse2D.Float(ox + p.x - 2.5f, oy + p.y - 2.5f, 5f, 5f))
        }
        g.scale(1 / scale.toDouble(), 1 / scale.toDouble())
        var y = diagramH + 40
        g.font = Font("SansSerif", Font.PLAIN, (14 * scale).toInt())
        for (it in items) {
            y += it.gap
            if (it.box != null) {
                drawBox(g, fonts, it.box, 40f, y + it.box.ascent, it.color)
                y += it.box.height + 6
            } else {
                g.color = it.color
                g.font = Font("SansSerif", if (it.color == accent) Font.BOLD else Font.PLAIN, ((if (it.color == accent) 16 else 13) * scale).toInt())
                g.drawString(it.text!!.take(160), 40f, y + 16 * scale)
                y += 18 * scale + 6
            }
        }
        g.dispose()
        ImageIO.write(img, "png", File(out, "%02d_%s.png".format(ti, t.name.replace(Regex("[^A-Za-z0-9]+"), "_"))))
        println("${t.name}: ${sol.steps.size} steps, issues: ${sol.issues.map { it.message }}")
    }
}
