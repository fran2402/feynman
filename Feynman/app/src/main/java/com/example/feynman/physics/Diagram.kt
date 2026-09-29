package com.example.feynman.physics

/*
 * A Feynman diagram as drawn: points on the canvas joined by lines. A point with one line
 * is an external end; a point with three or four is a vertex. Oriented lines run from
 * [Line.from] to [Line.to] in the direction of the particle's flow.
 */

/** Whether an external end is incoming or outgoing; Auto decides from where it's drawn. */
enum class Io { Auto, In, Out }

data class Point(val id: Int, val x: Float, val y: Float, val io: Io = Io.Auto)

data class Line(
    val id: Int,
    val from: Int,
    val to: Int,
    val particle: String,
    /** How far the middle of the line is pushed sideways, as a fraction of its length. */
    val bend: Float = 0f,
    /** Show the momentum next to the line. */
    val showMomentum: Boolean = true,
) {
    fun other(p: Int) = if (from == p) to else from
    val isSelfLoop get() = from == to
}

data class Diagram(val points: List<Point> = emptyList(), val lines: List<Line> = emptyList(), val name: String = "") {
    fun point(id: Int) = points.first { it.id == id }
    fun pointOrNull(id: Int) = points.firstOrNull { it.id == id }
    fun linesAt(p: Int) = lines.filter { it.from == p || it.to == p }
    /** Lines at a point, counting a self-loop twice. */
    fun degree(p: Int) = lines.sumOf { (if (it.from == p) 1 else 0) + (if (it.to == p) 1 else 0) }
    val nextId get() = maxOf(points.maxOfOrNull { it.id } ?: 0, lines.maxOfOrNull { it.id } ?: 0) + 1

    /** A compact text form for saving: points then lines. */
    fun encode(): String = buildString {
        append("v1|").append(name.replace("|", "/").replace("\n", " ")).append('\n')
        points.forEach { append("P ${it.id} ${it.x} ${it.y} ${it.io.name}\n") }
        lines.forEach { append("L ${it.id} ${it.from} ${it.to} ${it.particle} ${it.bend} ${it.showMomentum}\n") }
    }

    companion object {
        fun decode(s: String): Diagram? = runCatching {
            val rows = s.lines().filter { it.isNotBlank() }
            val name = rows.firstOrNull()?.takeIf { it.startsWith("v1|") }?.removePrefix("v1|") ?: ""
            val pts = ArrayList<Point>()
            val lns = ArrayList<Line>()
            for (r in rows) {
                val t = r.split(" ")
                when (t[0]) {
                    "P" -> pts.add(Point(t[1].toInt(), t[2].toFloat(), t[3].toFloat(), Io.valueOf(t[4])))
                    "L" -> lns.add(Line(t[1].toInt(), t[2].toInt(), t[3].toInt(), t[4], t[5].toFloat(), t.getOrNull(6)?.toBoolean() ?: true))
                }
            }
            Diagram(pts, lns, name)
        }.getOrNull()
    }
}

/** An external particle: which end, which line, in or out, particle or antiparticle, its momentum. */
class External(
    val number: Int,
    val point: Int,
    val line: Line,
    val particle: Particle,
    val incoming: Boolean,
    /** The antiparticle (e⁺ rather than e⁻, W⁻ rather than W⁺). */
    val anti: Boolean,
    val momentum: String,
) {
    val tex: String get() = if (anti) particle.antiTex else particle.tex
}

/** A problem with the drawing, shown on the canvas and in the solution. */
class Issue(val message: String, val point: Int? = null, val line: Int? = null)

/** What the drawing is, before any rules are applied. */
class Topology(
    val diagram: Diagram,
    val externals: List<External>,
    /** Points with two or more lines. */
    val vertices: List<Int>,
    /** Lines between two vertices (or from a vertex to itself). */
    val internal: List<Line>,
    val loops: Int,
    /** Momentum of each line, from its start to its end. */
    val momenta: Map<Int, Mom>,
    val loopMomenta: List<String>,
    val issues: List<Issue>,
) {
    val isTree get() = loops == 0
    fun external(line: Int) = externals.firstOrNull { it.line.id == line }

    companion object {
        fun of(d: Diagram): Topology {
            val issues = ArrayList<Issue>()
            val used = d.points.filter { d.degree(it.id) > 0 }
            val particles = d.lines.associate { it.id to SM.byId(it.particle) }
            d.lines.filter { particles[it.id] == null }.forEach { issues.add(Issue("Unknown particle ${it.particle}", line = it.id)) }
            val ends = used.filter { d.degree(it.id) == 1 }
            val vertices = used.filter { d.degree(it.id) >= 2 }.sortedWith(compareBy({ d.point(it.id).x }, { d.point(it.id).y })).map { it.id }
            vertices.filter { d.degree(it) == 2 }.forEach { issues.add(Issue("A vertex needs three or four lines", point = it)) }
            vertices.filter { d.degree(it) > 4 }.forEach { issues.add(Issue("The Standard Model has no vertex with more than four lines", point = it)) }

            // Connected?
            if (used.isNotEmpty()) {
                val seen = HashSet<Int>()
                val stack = ArrayDeque(listOf(used.first().id))
                while (stack.isNotEmpty()) {
                    val p = stack.removeLast()
                    if (!seen.add(p)) continue
                    d.linesAt(p).forEach { stack.add(it.other(p)) }
                }
                if (seen.size < used.size) issues.add(Issue("The diagram is in more than one piece"))
            }

            // External ends: in or out, numbered incoming first (top to bottom), then outgoing.
            val extLines = ends.mapNotNull { pt ->
                val line = d.linesAt(pt.id).first()
                val other = d.point(line.other(pt.id))
                val incoming = when (pt.io) {
                    Io.In -> true
                    Io.Out -> false
                    Io.Auto -> if (d.degree(other.id) == 1) pt.x <= other.x else pt.x < other.x
                }
                Triple(pt, line, incoming)
            }
            val ordered = extLines.filter { it.third }.sortedBy { it.first.y } + extLines.filter { !it.third }.sortedBy { it.first.y }
            val externals = ordered.mapIndexedNotNull { n, (pt, line, incoming) ->
                val p = particles[line.id] ?: return@mapIndexedNotNull null
                // Flow out of the end point into the diagram: incoming particle or outgoing antiparticle.
                val flowsIn = line.from == pt.id
                val anti = p.oriented && (flowsIn != incoming)
                if (p.isGhost) issues.add(Issue("Ghosts only run inside loops", line = line.id))
                External(n + 1, pt.id, line, p, incoming, anti, "p${n + 1}")
            }

            val internal = d.lines.filter { it.from in vertices && it.to in vertices }
            // Loops: E − V + 1 for a connected diagram.
            val loops = if (vertices.isEmpty()) 0 else maxOf(0, internal.size - vertices.size + 1)

            // Momentum routing: a spanning tree of the vertices; the other internal lines carry loop momenta.
            val momenta = HashMap<Int, Mom>()
            for (x in externals) {
                val p = mom(x.momentum)
                // Incoming momenta flow from the end into the diagram, outgoing ones out to the end.
                val alongFromTo = if (x.incoming) x.line.from == x.point else x.line.to == x.point
                momenta[x.line.id] = if (alongFromTo) p else -p
            }
            val tree = HashSet<Int>()
            val reached = HashSet<Int>()
            for (root in vertices) {
                if (root in reached) continue
                val queue = ArrayDeque(listOf(root))
                reached.add(root)
                while (queue.isNotEmpty()) {
                    val v = queue.removeFirst()
                    for (l in internal.filter { (it.from == v || it.to == v) && !it.isSelfLoop }.sortedBy { it.id }) {
                        val w = l.other(v)
                        if (w !in reached) { reached.add(w); tree.add(l.id); queue.add(w) }
                    }
                }
            }
            val chords = internal.filter { it.id !in tree }
            // Prefer to route the loop momentum along a fermion line, and in the flow's direction.
            val loopNames = if (chords.size == 1) listOf("k") else chords.indices.map { "k${it + 1}" }
            chords.forEachIndexed { n, l -> momenta[l.id] = mom(loopNames[n]) }
            // Peel: a vertex with one unknown line fixes it by conservation.
            var progress = true
            while (progress) {
                progress = false
                for (v in vertices) {
                    val at = d.linesAt(v)
                    val unknown = at.filter { it.id !in momenta }
                    if (unknown.size != 1) continue
                    val u = unknown[0]
                    var known: Mom = emptyMap()
                    for (l in at) {
                        if (l.id == u.id || l.isSelfLoop) continue
                        val q = momenta[l.id]!!
                        known = if (l.to == v) known + q else known - q
                    }
                    // sign_u·q_u + known = 0, sign +1 when u ends here.
                    momenta[u.id] = if (u.to == v) -known else known
                    progress = true
                }
            }
            d.lines.filter { it.id !in momenta }.forEach { momenta[it.id] = emptyMap() }
            if (externals.isEmpty() && used.isNotEmpty()) issues.add(Issue("Add external lines: a vacuum diagram has no amplitude"))
            return Topology(d, externals, vertices, internal, loops, momenta, loopNames, issues)
        }
    }
}
