package com.example.feynman.physics

import kotlin.math.cos
import kotlin.math.sin

/*
 * Every diagram of a process, from the theory's vertices.
 *
 * Trees are built from "currents": a set of external legs joined by vertices into one line
 * leaving towards the rest of the diagram. A current for a set is made by splitting the set
 * into two or three parts, taking a current for each part and a field X for the new line such
 * that the parts' fields and X̄ meet at a vertex the theory has. The diagram is the currents of
 * a split of all legs but the last joined with the last leg at one vertex, so each tree is made
 * once. One-loop diagrams are trees with two extra legs, a field and its antiparticle, joined
 * into one line; the copies this makes are removed by comparing the diagrams as graphs.
 */
object Generate {
    /** A field entering a vertex: a particle, or (for oriented lines) its antiparticle. */
    data class Kind(val p: Particle, val anti: Boolean) {
        fun conj() = if (p.oriented) Kind(p, !anti) else this
        val tex get() = if (p.oriented && anti) p.antiTex else p.tex
    }

    /** An external particle as asked for. */
    data class Leg(val p: Particle, val incoming: Boolean, val anti: Boolean) {
        /** The field it brings into the diagram: incoming e⁻ is e⁻, outgoing e⁻ is an incoming e⁺. */
        val kind get() = Kind(p, p.oriented && (anti == incoming))
        val tex get() = if (p.oriented && anti) p.antiTex else p.tex
    }

    class Options(
        val loops: Int = 0,
        val goldstones: Boolean = true,
        /** Leave out loops on external legs and tadpoles (loops hanging off one line). */
        val onlyAmputated: Boolean = true,
        val limit: Int = 60,
    )

    class Result(val diagrams: List<Diagram>, val truncated: Boolean, val note: String?)

    private sealed class Node {
        /** External leg number i (extra loop legs are numbered after the externals). */
        data class Ext(val i: Int) : Node()
        /** A vertex joining these currents, whose line out carries [out]. */
        data class Vtx(val parts: List<Cur>, val out: Kind) : Node()
    }

    /** A current: the field it brings into the next vertex, and how it's made. */
    private data class Cur(val kind: Kind, val node: Node)

    private fun legsOf(kinds: List<Kind>, ctx: RuleContext): Boolean {
        val pool = IndexPool()
        var colorId = 0
        // Each leg gets its own momentum, so momentum-dependent vertices (φ⁺φ⁻γ, f̃f̃Z …) don't vanish.
        val legs = kinds.mapIndexed { n, k ->
            val color = when (k.p.color) {
                ColorRep.Triplet -> CIdx(100 + colorId++, false, "i")
                ColorRep.Octet -> CIdx(100 + colorId++, true, "a")
                ColorRep.None -> null
            }
            com.example.feynman.physics.Leg(k.p, k.anti, pool.fresh(), mom("q$n"), color)
        }
        return Rules.vertex(legs, ctx)?.terms?.any { !it.expr.isZero } == true
    }

    private val vertexCache = HashMap<String, Boolean>()

    private fun valid(kinds: List<Kind>, ctx: RuleContext): Boolean {
        val key = kinds.map { it.p.id + (if (it.anti) "~" else "") }.sorted().joinToString(",") + "|" + ctx.theory + ctx.gauge
        return synchronized(vertexCache) { vertexCache[key] } ?: legsOf(kinds, ctx).also { synchronized(vertexCache) { vertexCache[key] = it } }
    }

    /** Set partitions of the bits of [mask] into 2 or 3 non-empty blocks (each partition once). */
    private fun partitions(mask: Int): List<List<Int>> {
        val bits = (0 until 31).filter { mask and (1 shl it) != 0 }
        val out = ArrayList<List<Int>>()
        val n = bits.size
        // Assign each element a block label (restricted growth strings), first element in block 0.
        val labels = IntArray(n)
        fun rec(i: Int, blocks: Int) {
            if (i == n) {
                if (blocks in 2..3) out.add((0 until blocks).map { b -> bits.filterIndexed { j, _ -> labels[j] == b }.fold(0) { acc, x -> acc or (1 shl x) } })
                return
            }
            for (b in 0..minOf(blocks, 2)) { labels[i] = b; rec(i + 1, maxOf(blocks, b + 1)) }
        }
        labels[0] = 0
        if (n > 0) rec(1, 1)
        return out
    }

    fun generate(legs: List<Leg>, ctx: RuleContext, options: Options): Result {
        val fields = ctx.theory.particles.filter { p ->
            (options.goldstones || (p !== SM.phiZ && p !== SM.phi)) &&
                !(ctx.gauge == Gauge.Unitary && (p === SM.phiZ || p === SM.phi || (p.isGhost && p.mass != null)))
        }
        val treeFields = fields.filter { !it.isGhost }
        val kindsOf = { list: List<Particle> -> list.flatMap { p -> if (p.oriented) listOf(Kind(p, false), Kind(p, true)) else listOf(Kind(p, false)) } }
        val out = ArrayList<Diagram>()
        var truncated = false
        if (options.loops == 0) {
            val trees = trees(legs.map { it.kind }, kindsOf(treeFields), ctx, options.limit * 4)
            truncated = trees.second
            for (t in trees.first) out.add(toDiagram(legs, t, emptyList()))
        } else {
            // Glue: two extra legs X and X̄ joined into one line.
            val loopKinds = kindsOf(fields)
            for (x in loopKinds) {
                val extra = listOf(x, x.conj())
                val (ts, cut) = trees(legs.map { it.kind } + extra, kindsOf(treeFields + fields.filter { it.isGhost }), ctx, 4000)
                if (cut) truncated = true
                for (t in ts) out.add(toDiagram(legs, t, listOf(legs.size, legs.size + 1)))
                if (out.size > 4000) { truncated = true; break }
            }
        }
        var list = dedupe(out.filter { Topology.of(it).loops == options.loops })
        if (options.loops > 0 && options.onlyAmputated) list = list.filter { amputated(it) }
        if (list.size > options.limit) { truncated = true; list = list.take(options.limit) }
        val named = list.mapIndexed { i, d -> Editing.tidy(d).copy(name = "Diagram ${i + 1}") }
        val note = when {
            named.isEmpty() -> "No diagrams: the theory has no vertices that make this process (check charges, colors and fermion number)."
            truncated -> "Stopped at ${named.size} diagrams."
            else -> null
        }
        return Result(named, truncated, note)
    }

    /** Every tree with these external fields (as currents joined at the last leg). */
    private fun trees(ext: List<Kind>, internal: List<Kind>, ctx: RuleContext, cap: Int): Pair<List<Cur>, Boolean> {
        val n = ext.size
        if (n < 2) return emptyList<Cur>() to false
        val memo = HashMap<Int, List<Cur>>()
        var cut = false
        fun currents(mask: Int): List<Cur> = memo.getOrPut(mask) {
            val bits = (0 until n).filter { mask and (1 shl it) != 0 }
            if (bits.size == 1) return@getOrPut listOf(Cur(ext[bits[0]], Node.Ext(bits[0])))
            val res = ArrayList<Cur>()
            for (part in partitions(mask)) {
                val choices = part.map { currents(it) }
                if (choices.any { it.isEmpty() }) continue
                for (combo in cartesian(choices)) {
                    for (x in internal) {
                        // The new line leaves this vertex: here it's an incoming X̄.
                        if (valid(combo.map { it.kind } + x.conj(), ctx)) res.add(Cur(x, Node.Vtx(combo, x)))
                    }
                    if (res.size > cap) { cut = true; return@getOrPut res }
                }
            }
            res
        }
        val last = n - 1
        val rest = (1 shl last) - 1
        val out = ArrayList<Cur>()
        if (n == 2) {
            // Just a propagating particle: not a diagram with vertices.
            return emptyList<Cur>() to false
        }
        for (part in partitions(rest)) {
            val choices = part.map { currents(it) }
            if (choices.any { it.isEmpty() }) continue
            for (combo in cartesian(choices)) {
                if (valid(combo.map { it.kind } + ext[last], ctx)) out.add(Cur(ext[last], Node.Vtx(combo, ext[last])))
                if (out.size > cap) return out to true
            }
        }
        return out to cut
    }

    private fun <T> cartesian(lists: List<List<T>>): List<List<T>> =
        lists.fold(listOf(emptyList())) { acc, l -> acc.flatMap { a -> l.map { a + it } } }

    /**
     * A tree as a diagram: externals on the left (in) and right (out); the root's vertex joins the
     * last leg. Leg numbers in [glue] (two of them) are joined into one internal line.
     */
    private fun toDiagram(legs: List<Leg>, root: Cur, glue: List<Int>): Diagram {
        val points = ArrayList<Point>()
        val lines = ArrayList<Line>()
        var id = 1
        val extPoint = HashMap<Int, Int>()
        val nIn = legs.count { it.incoming }
        val nOut = legs.size - nIn
        legs.forEachIndexed { i, l ->
            val col = legs.subList(0, i).count { it.incoming == l.incoming }
            val total = if (l.incoming) nIn else nOut
            val y = if (total == 1) 100f else 200f * col / (total - 1)
            val pid = id++
            points.add(Point(pid, if (l.incoming) 0f else 320f, y, if (l.incoming) Io.In else Io.Out))
            extPoint[i] = pid
        }
        val glueEnds = HashMap<Int, Pair<Int, Kind>>()
        var angle = 0.0
        fun vertexPoint(): Int {
            val pid = id++
            angle += 2.3
            points.add(Point(pid, 160f + 60f * cos(angle).toFloat(), 100f + 60f * sin(angle).toFloat()))
            return pid
        }
        /** Adds the line for a current ending at vertex [at]. */
        fun addLine(c: Cur, at: Int) {
            val kind = c.kind
            when (val node = c.node) {
                is Node.Ext -> {
                    if (node.i in glue) { glueEnds[node.i] = at to kind; return }
                    val e = extPoint[node.i]!!
                    // Flow into the vertex unless it's an incoming antiparticle field.
                    lines.add(if (!kind.p.oriented || !kind.anti) Line(id++, e, at, kind.p.id) else Line(id++, at, e, kind.p.id))
                }
                is Node.Vtx -> {
                    val v = vertexPoint()
                    node.parts.forEach { addLine(it, v) }
                    lines.add(if (!kind.p.oriented || !kind.anti) Line(id++, v, at, kind.p.id) else Line(id++, at, v, kind.p.id))
                }
            }
        }
        val rootNode = root.node as Node.Vtx
        val rv = vertexPoint()
        rootNode.parts.forEach { addLine(it, rv) }
        // The last leg (an external one, or the second glue leg) joins the root vertex.
        addLine(Cur(root.kind, Node.Ext(legs.size + glue.size - 1)), rv)
        if (glue.size == 2) {
            val (a, ka) = glueEnds[glue[0]] ?: return Diagram(points, lines)
            val (b, _) = glueEnds[glue[1]] ?: return Diagram(points, lines)
            // Leg glue[0] brings field ka into vertex a, so its particle flows from b to a.
            lines.add(if (!ka.p.oriented || !ka.anti) Line(id++, b, a, ka.p.id) else Line(id++, a, b, ka.p.id))
        }
        return Editing.straighten(Diagram(points, lines))
    }

    /** No loop on an external leg and no tadpole: every internal line, cut, leaves ≥ 2 externals on each side. */
    fun amputated(d: Diagram): Boolean {
        val topo = Topology.of(d)
        val ext = topo.externals.map { it.point }.toSet()
        for (l in topo.internal) {
            if (l.isSelfLoop) continue
            val others = d.lines.filter { it.id != l.id }
            val seen = HashSet<Int>()
            val stack = ArrayDeque(listOf(l.from))
            while (stack.isNotEmpty()) {
                val p = stack.removeLast()
                if (!seen.add(p)) continue
                others.filter { it.from == p || it.to == p }.forEach { stack.add(if (it.from == p) it.to else it.from) }
            }
            if (l.to in seen) continue // still connected: the line is in the loop
            val side = seen.count { it in ext }
            val otherSide = ext.size - side
            if (side < 2 || otherSide < 2) return false
        }
        return true
    }

    /** Removes diagrams that are the same graph (externals fixed, vertices relabelled). */
    fun dedupe(list: List<Diagram>): List<Diagram> {
        val out = ArrayList<Diagram>()
        val buckets = HashMap<String, MutableList<Diagram>>()
        for (d in list) {
            val sig = signature(d)
            val bucket = buckets.getOrPut(sig) { ArrayList() }
            if (bucket.none { isomorphic(it, d) }) { bucket.add(d); out.add(d) }
        }
        return out
    }

    private fun externalsKey(d: Diagram): Map<Int, Int> {
        // External points by leg number (they're numbered the same in every generated diagram).
        val topo = Topology.of(d)
        return topo.externals.associate { it.number to it.point }
    }

    private fun signature(d: Diagram): String {
        val deg = d.points.map { d.degree(it.id) }.sorted()
        val lines = d.lines.map { it.particle }.sorted()
        return "$deg|$lines"
    }

    private fun isomorphic(a: Diagram, b: Diagram): Boolean {
        val ea = externalsKey(a); val eb = externalsKey(b)
        if (ea.keys != eb.keys) return false
        val va = Topology.of(a).vertices; val vb = Topology.of(b).vertices
        if (va.size != vb.size || va.size > 8) return false
        fun edges(d: Diagram, map: (Int) -> Int): List<String> = d.lines.map { l ->
            val p = SM.byId(l.particle)
            val x = map(l.from); val y = map(l.to)
            if (p?.oriented == true) "${l.particle}:$x>$y" else "${l.particle}:${minOf(x, y)}-${maxOf(x, y)}"
        }.sorted()
        // Name points: externals by leg number (negative), vertices by permutation index.
        val extNameA = ea.entries.associate { (n, p) -> p to -n }
        val extNameB = eb.entries.associate { (n, p) -> p to -n }
        val target = edges(b) { p -> extNameB[p] ?: vb.indexOf(p) }
        val perm = IntArray(va.size); val used = BooleanArray(va.size)
        fun rec(i: Int): Boolean {
            if (i == va.size) return edges(a) { p -> extNameA[p] ?: perm[va.indexOf(p)] } == target
            for (j in va.indices) if (!used[j] && a.degree(va[i]) == b.degree(vb[j])) {
                used[j] = true; perm[i] = j
                if (rec(i + 1)) return true
                used[j] = false
            }
            return false
        }
        return rec(0)
    }
}
