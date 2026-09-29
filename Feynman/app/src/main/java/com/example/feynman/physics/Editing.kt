package com.example.feynman.physics

import kotlin.math.hypot
import kotlin.math.roundToInt

/** Changes to a diagram, each returning a new one (so undo is just the old value). */
object Editing {
    const val GRID = 20f

    fun snap(v: Float, on: Boolean) = if (on) (v / GRID).roundToInt() * GRID else v

    /** The point within [radius] of (x, y), nearest first. */
    fun pointAt(d: Diagram, x: Float, y: Float, radius: Float): Point? =
        d.points.filter { hypot(it.x - x, it.y - y) <= radius }.minByOrNull { hypot(it.x - x, it.y - y) }

    /**
     * A line from (x1, y1) to (x2, y2): ends near existing points join them, others make new
     * points. Lines between points already joined are bent apart so all of them show.
     */
    fun addLine(d: Diagram, x1: Float, y1: Float, x2: Float, y2: Float, particle: String, radius: Float, snapOn: Boolean): Pair<Diagram, Int?> {
        var pts = d.points.toMutableList()
        var next = d.nextId
        fun pointFor(x: Float, y: Float): Int {
            pointAt(Diagram(pts, d.lines), x, y, radius)?.let { return it.id }
            val p = Point(next++, snap(x, snapOn), snap(y, snapOn))
            pts.add(p)
            return p.id
        }
        val a = pointFor(x1, y1)
        val b = pointFor(x2, y2)
        val lineId = next++
        if (a == b) {
            // A loop from a point back to itself: only on a vertex.
            if (d.lines.none { it.from == a || it.to == a }) return d to null
            return d.copy(points = pts, lines = d.lines + Line(lineId, a, a, particle, 0.5f)) to lineId
        }
        var lines = d.lines + Line(lineId, a, b, particle)
        lines = spread(lines, a, b)
        return d.copy(points = pts, lines = lines) to lineId
    }

    /** Bends the lines between a and b symmetrically: 0, ±0.4, ±0.8… */
    fun spread(lines: List<Line>, a: Int, b: Int): List<Line> {
        val between = lines.filter { (it.from == a && it.to == b) || (it.from == b && it.to == a) }
        if (between.size < 2) return lines
        val bends = when (between.size) {
            2 -> listOf(0.45f, -0.45f)
            3 -> listOf(0.5f, 0f, -0.5f)
            else -> between.indices.map { (it - (between.size - 1) / 2f) * 0.35f }
        }
        val ids = between.map { it.id }
        return lines.map { l ->
            val i = ids.indexOf(l.id)
            if (i < 0) l else {
                // The bend is relative to the drawn direction, so flip it for lines drawn the other way.
                val s = if (l.from == a) 1f else -1f
                l.copy(bend = bends[i] * s)
            }
        }
    }

    fun movePoint(d: Diagram, id: Int, x: Float, y: Float, snapOn: Boolean) =
        d.copy(points = d.points.map { if (it.id == id) it.copy(x = snap(x, snapOn), y = snap(y, snapOn)) else it })

    /** Removes a line and any end left with no lines. */
    fun deleteLine(d: Diagram, id: Int): Diagram {
        val lines = d.lines.filter { it.id != id }
        val used = lines.flatMap { listOf(it.from, it.to) }.toSet()
        return d.copy(lines = lines, points = d.points.filter { it.id in used })
    }

    fun deletePoint(d: Diagram, id: Int): Diagram {
        val lines = d.lines.filter { it.from != id && it.to != id }
        val used = lines.flatMap { listOf(it.from, it.to) }.toSet()
        return d.copy(lines = lines, points = d.points.filter { it.id in used })
    }

    fun setParticle(d: Diagram, id: Int, particle: String) = d.copy(lines = d.lines.map { if (it.id == id) it.copy(particle = particle) else it })

    /** Reverses a line: the other way for the arrow (particle ↔ antiparticle, W⁺ ↔ W⁻). */
    fun flip(d: Diagram, id: Int) = d.copy(lines = d.lines.map { if (it.id == id) it.copy(from = it.to, to = it.from, bend = -it.bend) else it })

    fun setBend(d: Diagram, id: Int, bend: Float) = d.copy(lines = d.lines.map { if (it.id == id) it.copy(bend = bend.coerceIn(-1.5f, 1.5f)) else it })

    fun setIo(d: Diagram, point: Int, io: Io) = d.copy(points = d.points.map { if (it.id == point) it.copy(io = io) else it })

    fun toggleMomentum(d: Diagram, id: Int) = d.copy(lines = d.lines.map { if (it.id == id) it.copy(showMomentum = !it.showMomentum) else it })

    /** Moves the whole diagram so its top-left corner is at (x, y). */
    fun normalized(d: Diagram, x: Float = 0f, y: Float = 0f): Diagram {
        if (d.points.isEmpty()) return d
        val minX = d.points.minOf { it.x }
        val minY = d.points.minOf { it.y }
        return d.copy(points = d.points.map { it.copy(x = it.x - minX + x, y = it.y - minY + y) })
    }

    /** The bend that puts the middle of line [l] nearest (x, y). */
    fun bendToward(d: Diagram, l: Line, x: Float, y: Float): Float {
        val a = d.point(l.from); val b = d.point(l.to)
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = hypot(dx, dy)
        if (len < 1f) return l.bend
        val mx = (a.x + b.x) / 2; val my = (a.y + b.y) / 2
        // Signed distance of the touch from the chord, along the normal (−dy, dx).
        val nx = -dy / len; val ny = dx / len
        val dist = (x - mx) * nx + (y - my) * ny
        return (-dist / len).coerceIn(-1.5f, 1.5f)
    }
}
