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

    /**
     * Tidies the drawing without changing the diagram: incoming ends in a column on the left and
     * outgoing ones on the right (in their order, evenly spaced), vertices placed by a spring
     * layout started from how far each is from the incoming side, everything on the grid, single
     * lines straight, parallel lines spread evenly and loops at a vertex pointing outwards.
     */
    fun tidy(d: Diagram): Diagram {
        val topo = Topology.of(d)
        val verts = topo.vertices
        if (verts.isEmpty() || topo.externals.isEmpty()) return straighten(d)
        val used = d.points.filter { d.degree(it.id) > 0 }
        val originX = used.minOf { it.x }
        val originY = used.minOf { it.y }
        // Neighbours between points (each pair once, self-loops left out).
        val adj = HashMap<Int, MutableSet<Int>>()
        for (l in d.lines) if (!l.isSelfLoop) {
            adj.getOrPut(l.from) { HashSet() }.add(l.to)
            adj.getOrPut(l.to) { HashSet() }.add(l.from)
        }
        fun distances(sources: List<Int>): Map<Int, Int> {
            val dist = HashMap<Int, Int>()
            val queue = ArrayDeque<Int>()
            sources.forEach { dist[it] = 0; queue.add(it) }
            while (queue.isNotEmpty()) {
                val v = queue.removeFirst()
                for (w in adj[v].orEmpty()) if (w !in dist) { dist[w] = dist[v]!! + 1; queue.add(w) }
            }
            return dist
        }
        val ins = topo.externals.filter { it.incoming }
        val outs = topo.externals.filter { !it.incoming }
        val fromIn = distances(ins.map { it.point })
        val fromOut = distances(outs.map { it.point })
        // Left-to-right position of each vertex, 0 … 1.
        val rank = verts.associateWith { v ->
            val a = fromIn[v]?.toFloat(); val b = fromOut[v]?.toFloat()
            when {
                a != null && b != null -> a / (a + b)
                a != null -> 0.7f
                b != null -> 0.3f
                else -> 0.5f
            }
        }
        val layers = verts.map { ((rank[it] ?: 0.5f) * 8).roundToInt() }.distinct().size
        val width = maxOf(240f, 110f * (layers + 1))
        val height = maxOf(160f, 70f * (maxOf(ins.size, outs.size) - 1).coerceAtLeast(1) + 60f)
        val pos = HashMap<Int, Pair<Float, Float>>()
        fun column(list: List<External>, x: Float) {
            val sorted = list.sortedBy { d.point(it.point).y }
            sorted.forEachIndexed { i, e ->
                val y = if (sorted.size == 1) height / 2 else height * i / (sorted.size - 1)
                pos[e.point] = x to y
            }
        }
        column(ins, 0f)
        column(outs, width)
        // Vertices start at their rank, at the height of the ends they're joined to.
        val oldY = used.associate { it.id to it.y }
        val yMin = oldY.values.min(); val yMax = oldY.values.max()
        for (v in verts) {
            val y = if (yMax > yMin) (oldY[v]!! - yMin) / (yMax - yMin) * height else height / 2
            pos[v] = (rank[v]!! * (width - 120f) + 60f) to y
        }
        val free = verts.toSet()
        val ideal = 100f
        repeat(400) { step ->
            val t = 1f - step / 400f
            val force = HashMap<Int, Pair<Float, Float>>()
            fun push(v: Int, fx: Float, fy: Float) { val (a, b) = force[v] ?: (0f to 0f); force[v] = (a + fx) to (b + fy) }
            // Springs along lines.
            for ((a, ns) in adj) for (b in ns) if (a < b) {
                val (ax, ay) = pos[a] ?: continue; val (bx, by) = pos[b] ?: continue
                val dx = bx - ax; val dy = by - ay
                val len = hypot(dx, dy).coerceAtLeast(1f)
                val f = (len - ideal) * 0.08f
                push(a, f * dx / len, f * dy / len); push(b, -f * dx / len, -f * dy / len)
            }
            // Vertices keep apart from each other and from the ends.
            for (v in free) for (w in pos.keys) if (v != w) {
                val (ax, ay) = pos[v]!!; val (bx, by) = pos[w]!!
                val dx = ax - bx; val dy = ay - by
                val d2 = (dx * dx + dy * dy).coerceAtLeast(25f)
                val f = 4000f / d2
                val len = kotlin.math.sqrt(d2)
                push(v, f * dx / len, f * dy / len)
            }
            // A gentle pull toward each vertex's place from left to right.
            for (v in free) { val (x, _) = pos[v]!!; push(v, (rank[v]!! * (width - 120f) + 60f - x) * 0.05f, 0f) }
            for (v in free) {
                val (fx, fy) = force[v] ?: continue
                val (x, y) = pos[v]!!
                val cap = 12f * t + 1f
                val m = hypot(fx, fy)
                val k = if (m > cap) cap / m else 1f
                pos[v] = (x + fx * k).coerceIn(30f, width - 30f) to (y + fy * k).coerceIn(-height * 0.5f, height * 1.5f)
            }
        }
        var out = d.copy(points = d.points.map { p ->
            val q = pos[p.id] ?: return@map p
            p.copy(x = snap(q.first + originX, true), y = snap(q.second + originY, true))
        })
        // Two points can't share a place: nudge any that landed together.
        val seen = HashSet<Pair<Float, Float>>()
        out = out.copy(points = out.points.map { p ->
            var q = p
            while (!seen.add(q.x to q.y)) q = q.copy(y = q.y + GRID)
            q
        })
        return straighten(out)
    }

    /** Straight single lines, evenly spread parallel ones, loops at a vertex pointing away from the rest. */
    fun straighten(d: Diagram): Diagram {
        var lines = d.lines.map { if (it.isSelfLoop) it else it.copy(bend = 0f) }
        val pairs = lines.filter { !it.isSelfLoop }.map { if (it.from < it.to) it.from to it.to else it.to to it.from }.distinct()
        for ((a, b) in pairs) lines = spread(lines, a, b)
        val cy = if (d.points.isEmpty()) 0f else d.points.map { it.y }.average().toFloat()
        lines = lines.map { l ->
            if (!l.isSelfLoop) l else {
                val v = d.pointOrNull(l.from) ?: return@map l
                // Up (positive bend) when the vertex is in the upper half, down otherwise.
                l.copy(bend = if (v.y <= cy) 0.5f else -0.5f)
            }
        }
        return d.copy(lines = lines)
    }
}
