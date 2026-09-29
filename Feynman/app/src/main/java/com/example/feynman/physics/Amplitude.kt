package com.example.feynman.physics

/*
 * Builds iℳ from a diagram with the rules of Rules.kt: a vertex factor at every vertex, a
 * propagator on every internal line, spinors and polarization vectors on the external lines,
 * each fermion line read against its arrow, (−1) and a trace for each closed fermion or ghost
 * loop, ∫d⁴k/(2π)⁴ for each loop and the symmetry factor.
 */

/** An open fermion line: its spinors (left ū or v̄, right u or v) and the lines it runs along. */
class Chain(val left: Spinor, val right: Spinor, val leftLeg: External, val rightLeg: External)

/** One color structure of the amplitude with its Lorentz/Dirac part (a product, not yet contracted). */
class AmpTerm(val color: List<ColorFactor>, val expr: Expr)

class Amplitude(
    val topology: Topology,
    val ctx: RuleContext,
    val terms: List<AmpTerm>,
    val chains: List<Chain>,
    /** Every rule used: vertices, then propagators, as (what it is, the rule). */
    val rules: List<Pair<String, RuleUse>>,
    /** iℳ as LaTeX, factor by factor. */
    val tex: String,
    /** 1/S. */
    val symmetry: Rational,
    /** −1 per closed fermion or ghost loop. */
    val loopSign: Int,
    /** The sign from the order of the external fermions (for adding diagrams). */
    val fermionSign: Int,
    val issues: List<Issue>,
    /** Loop lines and their momenta (for the loop integral). */
    val loopLines: List<Pair<Line, Mom>>,
    val colorIndices: Map<Int, CIdx>,
    val lorentz: Map<Pair<Int, Boolean>, Idx>,
    /** The rule at each vertex (by point id). */
    val vertexRules: Map<Int, RuleUse> = emptyMap(),
) {
    val externals get() = topology.externals
    val ok get() = issues.isEmpty()

    /** The Lorentz part with the color factors multiplied out as symbols (for showing only). */
    fun colorTexOf(t: AmpTerm) = t.color.joinToString(" ") { it.tex() }

    companion object {
        private val greek = listOf("\\mu", "\\nu", "\\rho", "\\sigma", "\\alpha", "\\beta", "\\lambda", "\\kappa", "\\tau", "\\eta", "\\zeta", "\\xi")
        private val quarkHints = listOf("i", "j", "k", "l", "m", "n", "r", "s")
        private val gluonHints = listOf("a", "b", "c", "d", "f", "h", "p", "q")

        fun build(d: Diagram, ctx: RuleContext): Amplitude {
            val topo = Topology.of(d)
            val issues = ArrayList(topo.issues)
            val names = ctx.names
            val pool = IndexPool()
            // Lorentz indices at each end of each vector line (line id, at its start?).
            val lorentz = HashMap<Pair<Int, Boolean>, Idx>()
            var nextGreek = 0
            fun freshIdx(): Idx {
                val name = if (nextGreek < greek.size) greek[nextGreek] else "\\mu_{${nextGreek - greek.size + 1}}"
                nextGreek++
                val i = pool.fresh(name)
                names.reserve(i, name)
                return i
            }
            val particle = d.lines.associate { it.id to SM.byId(it.particle) }
            // Indices in the order the vertices are read (left to right), external ones first.
            for (x in topo.externals) if (x.particle.isVector) {
                val atStart = x.line.from != x.point
                lorentz[x.line.id to atStart] = freshIdx()
            }
            for (v in topo.vertices) for (l in d.linesAt(v)) {
                if (particle[l.id]?.isVector != true) continue
                if (l.from == v && (l.id to true) !in lorentz) lorentz[l.id to true] = freshIdx()
                if (l.to == v && (l.id to false) !in lorentz) lorentz[l.id to false] = freshIdx()
            }
            fun idx(l: Line, atStart: Boolean) = lorentz.getOrPut(l.id to atStart) { pool.fresh() }
            // Color: one index per colored line.
            val colors = HashMap<Int, CIdx>()
            var nq = 0; var ng = 0
            for (l in (topo.externals.map { it.line } + d.lines).distinct()) {
                val p = particle[l.id] ?: continue
                when (p.color) {
                    ColorRep.Triplet -> colors[l.id] = CIdx(l.id, false, quarkHints.getOrElse(nq++) { "i_{$nq}" })
                    ColorRep.Octet -> colors[l.id] = CIdx(l.id, true, gluonHints.getOrElse(ng++) { "a_{$ng}" })
                    ColorRep.None -> {}
                }
            }

            // Vertex factors.
            val vertexRule = HashMap<Int, RuleUse>()
            val fermionVertex = HashSet<Int>()
            val rules = ArrayList<Pair<String, RuleUse>>()
            for (v in topo.vertices) {
                val legs = ArrayList<Leg>()
                for (l in d.linesAt(v)) {
                    val p = particle[l.id] ?: continue
                    val q = topo.momenta[l.id] ?: emptyMap()
                    if (l.to == v) legs.add(Leg(p, false, idx(l, false), q, colors[l.id]))
                    if (l.from == v) legs.add(Leg(p, true, idx(l, true), -q, colors[l.id]))
                }
                if (legs.size < 3) continue
                val r = Rules.vertex(legs, ctx)
                if (r == null) {
                    val what = legs.joinToString(" ") { if (it.particle.oriented && it.anti) it.particle.antiTex else it.particle.tex }
                    issues.add(Issue("No Standard Model vertex joins \\(${what}\\)", point = v))
                    continue
                }
                vertexRule[v] = r
                if (legs.any { it.particle.isFermion }) fermionVertex.add(v)
                rules.add(vertexName(legs) to r)
            }
            // Propagators.
            val propRule = HashMap<Int, RuleUse>()
            for (l in topo.internal) {
                val p = particle[l.id] ?: continue
                val r = Rules.propagator(p, topo.momenta[l.id] ?: emptyMap(), idx(l, true), idx(l, false), ctx)
                propRule[l.id] = r
                rules.add("${if (p.oriented) p.tex else p.tex}\\text{ propagator}" to r)
            }

            // Fermion lines, read against the arrows.
            val chains = ArrayList<Chain>()
            val chainFactors = ArrayList<List<AmpTerm>>()
            val chainTex = ArrayList<String>()
            val onChain = HashSet<Int>()
            val fermionLines = d.lines.filter { particle[it.id]?.isFermion == true }
            val extByPoint = topo.externals.associateBy { it.point }
            fun massExpr(p: Particle) = ctx.massOf(p)?.let { sym(it) } ?: Expr.ZERO
            for (x in topo.externals.filter { it.particle.isFermion }) {
                // Start where the flow leaves the diagram: an outgoing particle or incoming antiparticle.
                if (x.line.to != x.point) continue
                val left = Spinor(if (x.incoming) Spinor.Kind.VBar else Spinor.Kind.UBar, x.momentum, massExpr(x.particle))
                var terms = listOf(AmpTerm(emptyList(), diracOne))
                val tex = StringBuilder()
                var line = x.line
                var guard = 0
                var end: External? = null
                while (guard++ < 100) {
                    onChain.add(line.id)
                    val v = line.from
                    val ext = extByPoint[v]
                    if (ext != null && d.degree(v) == 1) { end = ext; break }
                    val r = vertexRule[v]
                    if (r != null) { terms = timesChain(terms, r.terms); tex.append(Tex.paren(r.tex).let { "\\left(${r.tex}\\right)" }).append(" ") }
                    val next = fermionLines.firstOrNull { it.to == v && it.id != line.id && it.id !in onChain } ?: break
                    val pr = propRule[next.id]
                    if (pr != null) { terms = timesChain(terms, pr.terms); tex.append(pr.tex).append(" ") }
                    line = next
                }
                if (end == null) { issues.add(Issue("A fermion line doesn't reach an external end", line = x.line.id)); continue }
                val right = Spinor(if (end.incoming) Spinor.Kind.U else Spinor.Kind.V, end.momentum, massExpr(end.particle))
                chains.add(Chain(left, right, x, end))
                chainFactors.add(terms)
                chainTex.add("${spinorTex(left)}\\, ${tex.toString().trim()}\\, ${spinorTex(right)}")
            }
            // Closed loops of fermions and of ghosts.
            var loopSign = 1
            val traceFactors = ArrayList<List<AmpTerm>>()
            val traceTex = ArrayList<String>()
            val scalarLoopTex = ArrayList<String>()
            val ghostLines = d.lines.filter { particle[it.id]?.isGhost == true && it.from in topo.vertices && it.to in topo.vertices }
            for (start in fermionLines + ghostLines) {
                if (start.id in onChain) continue
                if (start.from !in topo.vertices || start.to !in topo.vertices) continue
                val isGhost = particle[start.id]!!.isGhost
                val flowLines = if (isGhost) ghostLines else fermionLines
                var terms = listOf(AmpTerm(emptyList(), diracOne))
                val tex = StringBuilder()
                var line = start
                var guard = 0
                do {
                    onChain.add(line.id)
                    val pr = propRule[line.id]
                    if (pr != null && !isGhost) { terms = timesChain(terms, pr.terms); tex.append(pr.tex).append(" ") }
                    val v = line.from
                    val r = vertexRule[v]
                    if (r != null && !isGhost) { terms = timesChain(terms, r.terms); tex.append("\\left(${r.tex}\\right) ") }
                    val next = flowLines.firstOrNull { it.to == v && (it.id !in onChain || it.id == start.id) } ?: break
                    line = next
                } while (line.id != start.id && guard++ < 100)
                loopSign = -loopSign
                if (isGhost) scalarLoopTex.add("(-1)")
                else {
                    traceFactors.add(terms.map { t -> AmpTerm(t.color, t.expr.mapTerms { k, c -> (trace(k.chains.firstOrNull() ?: emptyList()) * Expr(mapOf(TermKey(k.mono, emptyList()) to c))) }) })
                    traceTex.add("(-1)\\,\\mathrm{Tr}\\left[${tex.toString().trim()}\\right]")
                }
            }

            // Everything else: vertices off fermion lines, boson (and ghost) propagators, polarizations.
            var scalar = listOf(AmpTerm(emptyList(), Expr.ONE))
            val scalarTex = ArrayList<String>()
            for (v in topo.vertices) {
                val r = vertexRule[v] ?: continue
                if (v in fermionVertex) continue
                scalar = times(scalar, r.terms)
                scalarTex.add(if (Tex.needsParen(r.tex) || r.tex.startsWith("-")) "\\left(${r.tex}\\right)" else r.tex)
            }
            for (l in topo.internal) {
                val p = particle[l.id] ?: continue
                if (p.isFermion) continue
                val r = propRule[l.id] ?: continue
                scalar = times(scalar, r.terms)
                scalarTex.add(r.tex)
            }
            val polTex = ArrayList<String>()
            for (x in topo.externals) if (x.particle.isVector) {
                val i = idx(x.line, x.line.from != x.point)
                val eps = if (x.incoming) "eps${x.number}" else "eps${x.number}*"
                scalar = times(scalar, listOf(VertexTerm(emptyList(), atom(Vec(eps, i)))))
                polTex.add("${if (x.incoming) "\\varepsilon" else "\\varepsilon^{*}"}_{${names.name(i)}}(${MomNames.tex(x.momentum)})")
            }

            var all = scalar
            for (f in chainFactors) all = timesSeparate(all, f)
            for (f in traceFactors) all = times(all, f.map { VertexTerm(it.color, it.expr) })

            // Symmetry factor and fermion signs.
            val s = symmetryFactor(d, topo)
            val symmetry = Rational.of(1, s.toLong())
            val prefactor = Expr.const(symmetry) * CQ.of(loopSign.toLong())
            all = all.map { AmpTerm(it.color, Rules.simplifyRoots(it.expr * prefactor)) }
            val order = chains.flatMap { listOf(it.leftLeg.number, it.rightLeg.number) }
            val fermionSign = permutationSign(order)

            val loopLines = topo.internal.filter { l -> topo.momenta[l.id]?.keys?.any { it.startsWith("k") } == true }.map { it to topo.momenta[it.id]!! }

            val texParts = ArrayList<String>()
            if (s != 1) texParts.add("\\frac{1}{$s}")
            topo.loopMomenta.forEach { texParts.add("\\int\\frac{d^{4}${MomNames.tex(it)}}{(2\\pi)^{4}}") }
            texParts.addAll(scalarLoopTex)
            chainTex.forEach { texParts.add("\\left[$it\\right]") }
            texParts.addAll(traceTex)
            texParts.addAll(scalarTex)
            texParts.addAll(polTex)
            val tex = if (texParts.isEmpty()) "1" else texParts.joinToString("\\, ")

            return Amplitude(topo, ctx, all, chains, rules, tex, symmetry, loopSign, fermionSign, issues, loopLines, colors, lorentz, vertexRule)
        }

        private fun vertexName(legs: List<Leg>) =
            legs.joinToString("") { if (it.particle.oriented && it.anti) it.particle.antiTex else it.particle.tex }.let { "\\text{vertex } $it" }

        fun spinorTex(s: Spinor): String {
            val p = MomNames.tex(s.p)
            return when (s.kind) {
                Spinor.Kind.U -> "u($p)"
                Spinor.Kind.V -> "v($p)"
                Spinor.Kind.UBar -> "\\bar{u}($p)"
                Spinor.Kind.VBar -> "\\bar{v}($p)"
            }
        }

        private fun timesChain(a: List<AmpTerm>, b: List<VertexTerm>): List<AmpTerm> =
            a.flatMap { x -> b.map { y -> AmpTerm(x.color + y.color, x.expr.dirac(y.expr)) } }

        private fun times(a: List<AmpTerm>, b: List<VertexTerm>): List<AmpTerm> =
            a.flatMap { x -> b.map { y -> AmpTerm(x.color + y.color, x.expr * y.expr) } }

        private fun timesSeparate(a: List<AmpTerm>, b: List<AmpTerm>): List<AmpTerm> =
            a.flatMap { x -> b.map { y -> AmpTerm(x.color + y.color, x.expr * y.expr) } }

        fun permutationSign(seq: List<Int>): Int {
            val a = seq.toMutableList()
            var sign = 1
            for (i in a.indices) for (j in 0 until a.size - 1 - i) if (a[j] > a[j + 1]) { val t = a[j]; a[j] = a[j + 1]; a[j + 1] = t; sign = -sign }
            return sign
        }

        /**
         * S: the number of ways of relabelling the vertices that keep the diagram (external ends
         * fixed), times k! for k identical lines between the same two vertices, times 2 for each
         * self-conjugate line from a vertex to itself.
         */
        fun symmetryFactor(d: Diagram, topo: Topology): Int {
            val verts = topo.vertices
            if (verts.isEmpty()) return 1
            if (verts.size > 8) return 1
            fun edgeKey(l: Line, a: Int, b: Int): String {
                val p = SM.byId(l.particle)
                val dir = if (p?.oriented == true) (if (l.from == a) ">" else "<") else "-"
                return l.particle + dir
            }
            // Edges between two points (ordered), as a sorted list of keys.
            fun bundle(a: Int, b: Int): List<String> = d.lines.filter { (it.from == a && it.to == b) || (it.from == b && it.to == a) }
                .map { edgeKey(it, a, b) }.sorted()
            val allPoints = d.points.filter { d.degree(it.id) > 0 }.map { it.id }
            var valid = 0
            val perm = IntArray(verts.size)
            val usedV = BooleanArray(verts.size)
            fun map(p: Int): Int { val i = verts.indexOf(p); return if (i < 0) p else verts[perm[i]] }
            fun check(): Boolean {
                for (a in allPoints) for (b in allPoints) if (a <= b) if (bundle(a, b) != bundle(map(a), map(b))) return false
                return true
            }
            fun rec(i: Int) {
                if (i == verts.size) { if (check()) valid++; return }
                for (j in verts.indices) if (!usedV[j] && d.degree(verts[j]) == d.degree(verts[i])) {
                    usedV[j] = true; perm[i] = j; rec(i + 1); usedV[j] = false
                }
            }
            rec(0)
            var edgeFactor = 1
            val groups = d.lines.filter { it.from in verts && it.to in verts }.groupBy { l ->
                val (a, b) = if (l.from <= l.to) l.from to l.to else l.to to l.from
                "$a:$b:" + edgeKey(l, a, b)
            }
            for ((_, g) in groups) {
                for (k in 2..g.size) edgeFactor *= k
                val self = g.firstOrNull()?.isSelfLoop == true && SM.byId(g.first().particle)?.oriented == false
                if (self) repeat(g.size) { edgeFactor *= 2 }
            }
            return maxOf(1, valid) * edgeFactor
        }
    }
}
