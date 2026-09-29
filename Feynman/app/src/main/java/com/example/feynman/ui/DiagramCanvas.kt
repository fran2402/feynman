package com.example.feynman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.example.feynman.draw.DiagramShapes
import com.example.feynman.draw.Geometry
import com.example.feynman.draw.LineShape
import com.example.feynman.draw.Pt
import com.example.feynman.draw.Style
import com.example.feynman.latex.MathLayout
import com.example.feynman.latex.MathParser
import com.example.feynman.physics.Diagram
import com.example.feynman.physics.Editing
import com.example.feynman.physics.Line
import com.example.feynman.physics.SM
import kotlin.math.hypot

/** Colors for drawing a diagram. */
class DiagramColors(
    val line: Color,
    val label: Color,
    val momentum: Color,
    val vertex: Color,
    val selected: Color,
    val error: Color,
    val grid: Color,
)

@Composable
fun diagramColors(): DiagramColors {
    val c = MaterialTheme.colorScheme
    return DiagramColors(c.onSurface, c.primary, c.onSurfaceVariant.copy(alpha = 0.8f), c.onSurface, c.tertiary, c.error, c.outlineVariant.copy(alpha = 0.5f))
}

fun drawingStyle() = Style(gluonCoils = AppSettings.gluonCoils)

/**
 * Draws [shapes]: every line in its paper style, the particle names and momenta in Computer
 * Modern, vertices as dots. [toScreen] maps diagram units (dp) to pixels; [unit] is px per dp.
 */
fun DrawScope.drawDiagram(
    shapes: DiagramShapes,
    fonts: AndroidMathFonts,
    colors: DiagramColors,
    toScreen: (Pt) -> Offset,
    unit: Float,
    selectedLine: Int? = null,
    selectedPoint: Int? = null,
    badPoints: Set<Int> = emptySet(),
    labels: Boolean = true,
) {
    val d = shapes.diagram
    val labelLayout = MathLayout(fonts, 14f * unit)
    val momLayout = MathLayout(fonts, 11f * unit)
    for (l in d.lines) {
        val sh = shapes.shapes[l.id] ?: continue
        val color = if (l.id == selectedLine) colors.selected else colors.line
        drawLineShape(sh, color, toScreen, unit, shapes.style)
        if (!labels) continue
        sh.momentumStroke?.let { ms ->
            val a = toScreen(ms[0]); val b = toScreen(ms[1])
            drawLine(colors.momentum, a, b, strokeWidth = 1.1f * unit, cap = StrokeCap.Round)
            fillPolygon(sh.momentumHead!!.map(toScreen), colors.momentum)
            shapes.momentumTex(l)?.let { tex ->
                val box = runCatching { momLayout.layout(MathParser.parse(tex)) }.getOrNull() ?: return@let
                val at = toScreen(sh.momentumLabelAt!!)
                val dir = sh.labelDir * -1f
                // Push the label away from the line by half its size.
                val off = Offset(dir.x * box.width / 2, dir.y * box.height / 2)
                drawMath(fonts, box, at.x + off.x - box.width / 2, at.y + off.y + (box.ascent - box.descent) / 2, colors.momentum)
            }
        }
        val box = runCatching { labelLayout.layout(MathParser.parse(shapes.labelTex(l))) }.getOrNull() ?: continue
        val at = toScreen(sh.labelAt)
        val off = Offset(sh.labelDir.x * box.width / 2, sh.labelDir.y * box.height / 2)
        drawMath(fonts, box, at.x + off.x - box.width / 2, at.y + off.y + (box.ascent - box.descent) / 2, colors.label)
    }
    for (p in d.points) {
        val deg = d.degree(p.id)
        val at = toScreen(Pt(p.x, p.y))
        when {
            p.id in badPoints -> drawCircle(colors.error, 6f * unit, at)
            p.id == selectedPoint -> drawCircle(colors.selected, 5f * unit, at)
            deg >= 2 -> drawCircle(colors.vertex, 3f * unit, at)
            deg == 1 && labels -> drawCircle(colors.vertex.copy(alpha = 0.35f), 2.2f * unit, at)
        }
    }
}

fun DrawScope.drawLineShape(sh: LineShape, color: Color, toScreen: (Pt) -> Offset, unit: Float, style: Style) {
    val stroke = Stroke(width = style.stroke * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
    for (s in sh.strokes) {
        if (s.size < 2) continue
        val path = Path()
        s.forEachIndexed { i, p -> val o = toScreen(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, color, style = stroke)
    }
    for (dot in sh.dots) drawCircle(color, style.dotRadius * unit, toScreen(dot))
    for (a in sh.arrows) fillPolygon(a.map(toScreen), color)
}

private fun DrawScope.fillPolygon(pts: List<Offset>, color: Color) {
    if (pts.size < 3) return
    val path = Path()
    pts.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    path.close()
    drawPath(path, color)
}

/** A diagram drawn to fit its box (for lists and cards), without interaction. */
@Composable
fun DiagramThumbnail(diagram: Diagram, modifier: Modifier = Modifier, labels: Boolean = true) {
    val fonts = LocalMathFonts.current
    val colors = diagramColors()
    val shapes = remember(diagram, AppSettings.gluonCoils) { DiagramShapes(diagram, drawingStyle(), showMomenta = false) }
    Canvas(modifier) {
        if (diagram.points.isEmpty()) return@Canvas
        val pad = 28f
        val minX = diagram.points.minOf { it.x } - pad; val maxX = diagram.points.maxOf { it.x } + pad
        val minY = diagram.points.minOf { it.y } - pad; val maxY = diagram.points.maxOf { it.y } + pad
        val unit = minOf(size.width / (maxX - minX), size.height / (maxY - minY))
        val ox = (size.width - (maxX - minX) * unit) / 2 - minX * unit
        val oy = (size.height - (maxY - minY) * unit) / 2 - minY * unit
        drawDiagram(shapes, fonts, colors, { p -> Offset(ox + p.x * unit, oy + p.y * unit) }, unit, labels = labels)
    }
}

/**
 * The drawing surface: draw lines by dragging from point to point, move points, bend lines,
 * erase, tap a line to select it. Dragging empty space with the move tool pans.
 */
@Composable
fun DiagramEditor(vm: FeynmanViewModel, modifier: Modifier = Modifier, badPoints: Set<Int>) {
    val fonts = LocalMathFonts.current
    val colors = diagramColors()
    val density = LocalDensity.current.density
    val diagram = vm.diagram
    val shapes = remember(diagram, AppSettings.gluonCoils, AppSettings.showMomenta) { DiagramShapes(diagram, drawingStyle(), AppSettings.showMomenta) }
    var pan by remember { mutableStateOf(Offset(40f, 60f)) }
    var dragFrom by remember { mutableStateOf<Offset?>(null) }
    var dragTo by remember { mutableStateOf<Offset?>(null) }
    var moving by remember { mutableStateOf<Int?>(null) }
    var bending by remember { mutableStateOf<Line?>(null) }
    var preview by remember { mutableStateOf<Diagram?>(null) }
    val gridColor = colors.grid

    fun toDiagram(o: Offset) = Pt(o.x / density - pan.x, o.y / density - pan.y)
    fun lineAt(pt: Pt): Line? = diagram.lines.minByOrNull { l -> shapes.shapes[l.id]?.let { Geometry.distance(it, pt) } ?: Float.MAX_VALUE }
        ?.takeIf { l -> (shapes.shapes[l.id]?.let { Geometry.distance(it, pt) } ?: Float.MAX_VALUE) < FeynmanViewModel.HIT_RADIUS }

    Canvas(
        modifier
            .pointerInput(vm.tool, diagram) {
                detectTapGestures { o ->
                    val pt = toDiagram(o)
                    val point = Editing.pointAt(diagram, pt.x, pt.y, FeynmanViewModel.HIT_RADIUS)
                    val line = if (point == null) lineAt(pt) else null
                    when (vm.tool) {
                        Tool.Erase -> when {
                            point != null -> vm.edit { Editing.deletePoint(it, point.id) }
                            line != null -> vm.edit { Editing.deleteLine(it, line.id) }
                        }
                        else -> {
                            vm.selectedLine = line?.id
                            vm.selectedPoint = if (line == null) point?.id else null
                        }
                    }
                }
            }
            .pointerInput(vm.tool, diagram) {
                detectDragGestures(
                    onDragStart = { o ->
                        val pt = toDiagram(o)
                        when (vm.tool) {
                            Tool.Draw -> { dragFrom = o; dragTo = o }
                            Tool.Move -> moving = Editing.pointAt(diagram, pt.x, pt.y, FeynmanViewModel.HIT_RADIUS * 1.3f)?.id
                            Tool.Bend -> bending = lineAt(pt)
                            Tool.Erase -> {}
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        when (vm.tool) {
                            Tool.Draw -> dragTo = change.position
                            Tool.Move -> {
                                val id = moving
                                if (id == null) pan += Offset(amount.x / density, amount.y / density)
                                else {
                                    val pt = toDiagram(change.position)
                                    preview = Editing.movePoint(preview ?: diagram, id, pt.x, pt.y, false)
                                }
                            }
                            Tool.Bend -> bending?.let { l ->
                                val pt = toDiagram(change.position)
                                val base = preview ?: diagram
                                preview = Editing.setBend(base, l.id, Editing.bendToward(base, l, pt.x, pt.y))
                            }
                            Tool.Erase -> {}
                        }
                    },
                    onDragEnd = {
                        when (vm.tool) {
                            Tool.Draw -> {
                                val a = dragFrom; val b = dragTo
                                if (a != null && b != null) {
                                    val pa = toDiagram(a); val pb = toDiagram(b)
                                    val self = Editing.pointAt(diagram, pa.x, pa.y, FeynmanViewModel.HIT_RADIUS)?.let { p ->
                                        hypot(pb.x - p.x, pb.y - p.y) < FeynmanViewModel.HIT_RADIUS
                                    } ?: false
                                    if (self || hypot(pb.x - pa.x, pb.y - pa.y) > 12f) {
                                        var newLine: Int? = null
                                        vm.edit { d ->
                                            val (nd, id) = Editing.addLine(d, pa.x, pa.y, pb.x, pb.y, vm.particle, FeynmanViewModel.HIT_RADIUS, AppSettings.snapToGrid)
                                            newLine = id
                                            nd
                                        }
                                        vm.selectedLine = newLine
                                    }
                                }
                            }
                            Tool.Move -> {
                                val id = moving
                                val p = preview?.pointOrNull(id ?: -1)
                                if (id != null && p != null) vm.edit { Editing.movePoint(it, id, p.x, p.y, AppSettings.snapToGrid) }
                            }
                            Tool.Bend -> preview?.let { pd -> vm.edit { pd } }
                            Tool.Erase -> {}
                        }
                        dragFrom = null; dragTo = null; moving = null; bending = null; preview = null
                    },
                    onDragCancel = { dragFrom = null; dragTo = null; moving = null; bending = null; preview = null },
                )
            },
    ) {
        val unit = density
        // Dot grid.
        if (AppSettings.showGrid) {
            val step = Editing.GRID * unit
            val sx = (pan.x * unit) % step
            val sy = (pan.y * unit) % step
            var x = sx
            while (x < size.width) {
                var y = sy
                while (y < size.height) { drawCircle(gridColor, 1.1f * unit, Offset(x, y)); y += step }
                x += step
            }
        }
        val shown = preview?.let { DiagramShapes(it, drawingStyle(), AppSettings.showMomenta) } ?: shapes
        drawDiagram(shown, fonts, colors, { p -> Offset((p.x + pan.x) * unit, (p.y + pan.y) * unit) }, unit, vm.selectedLine, vm.selectedPoint, badPoints)
        // The line being drawn, in the chosen particle's style.
        val a = dragFrom; val b = dragTo
        if (a != null && b != null) {
            val pa = toDiagram(a); val pb = toDiagram(b)
            val sh = Geometry.shape(SM.byId(vm.particle), pa, pb, 0f, false, drawingStyle(), false)
            drawLineShape(sh, colors.selected, { p -> Offset((p.x + pan.x) * unit, (p.y + pan.y) * unit) }, unit, drawingStyle())
        }
    }
}
