package com.example.feynman.physics

/*
 * The spin- and color-summed square of a tree amplitude (or of several diagrams with the same
 * external particles, interference included): spinor sums Σuū = p̸ + m and Σvv̄ = p̸ − m join
 * the fermion lines of ℳ and ℳ* into traces, polarization sums give −g_{μν} (+ k_μk_ν/M² for
 * massive vectors), colors are summed exactly, and the incoming spins and colors averaged.
 */

class SquaredResult(
    /** The average over incoming spins and colors. */
    val average: Rational,
    val averageTex: String,
    /** Σ|ℳ|² written with traces, when there's one diagram with one term. */
    val traceTex: String?,
    /** The averaged result in dot products. */
    val dots: Expr,
    /** And with the kinematics substituted (Mandelstam variables for 2 → 2). */
    val result: Expr,
    val notes: List<String>,
)

object Squared {
    private const val CONJ_OFFSET = 200_000

    private fun renameIdx(i: Idx) = Idx(i.id + CONJ_OFFSET, i.hint?.let { "$it'" })

    /** The conjugate amplitude's expression with its indices renamed (ε ↔ ε*). */
    private fun conjugate(e: Expr): Expr {
        fun p(name: String) = if (name.startsWith("eps")) (if (name.endsWith("*")) name.dropLast(1) else "$name*") else name
        val out = HashMap<TermKey, CQ>()
        for ((k, c) in e.terms) {
            val factors = k.mono.factors.map { (a, n) ->
                val b: Atom = when (a) {
                    is Vec -> Vec(p(a.p), renameIdx(a.i))
                    is Met -> Met.of(renameIdx(a.i), renameIdx(a.j))
                    is Sym -> if (a.complex) a.conj() else a
                    is Dot -> Dot.of(p(a.a), p(a.b))
                    else -> a
                }
                b to n
            }
            // [ū Γ u]* = ū Γ̄ u with Γ̄ = γ⁰Γ†γ⁰: the string reversed, each γ5 → −γ5.
            var sign = 1L
            val chains = k.chains.map { ch ->
                ch.reversed().map { g ->
                    when (g) {
                        is G.I -> G.I(renameIdx(g.i))
                        G.Five -> { sign = -sign; G.Five }
                        else -> g
                    }
                }
            }
            val key = TermKey(Mono.of(factors), chains)
            out[key] = (out[key] ?: CQ.ZERO) + c.conj() * CQ.of(sign)
        }
        return Expr(out.filterValues { !it.isZero })
    }

    /** The spin sum at an external fermion: p̸ + m for u and ū, p̸ − m for v and v̄. */
    private fun spinSum(s: Spinor): Expr = slash(mom(s.p)) + diracOne * s.mass * CQ.of(s.diracSign)

    fun compute(amps: List<Amplitude>, kin: Kinematics): SquaredResult {
        val notes = ArrayList<String>()
        val first = amps.first()
        // Average over incoming spins and colors.
        var average = Rational.ONE
        val avgParts = ArrayList<String>()
        for (x in first.externals.filter { it.incoming }) {
            val spins = when (x.particle.spin) {
                Spin.Fermion -> if (x.particle.family == Family.Neutrino) 1 else 2
                Spin.Vector -> if (kin.mass(x) == null) 2 else 3
                else -> 1
            }
            val colors = when (x.particle.color) { ColorRep.Triplet -> 3; ColorRep.Octet -> 8; ColorRep.None -> 1 }
            average *= Rational.of(1, (spins * colors).toLong())
            if (spins * colors != 1) avgParts.add("${spins * colors}")
        }
        val averageTex = if (avgParts.isEmpty()) "" else "\\frac{1}{${avgParts.joinToString("\\cdot ")}}"
        if (first.externals.count { it.particle === SM.gluon } >= 2)
            notes.add("Gluon polarizations are summed with \\(-g_{\\mu\\nu}\\), which also counts unphysical ones: with two or more external gluons, subtract the ghost diagrams or use physical polarization sums.")
        if (first.externals.any { it.particle.family == Family.Neutrino })
            notes.add("Neutrinos are left-handed: an incoming neutrino's single helicity isn't averaged.")

        // External color indices by leg number, the same in every diagram.
        fun externalColors(a: Amplitude): Map<CIdx, Int> =
            a.externals.mapNotNull { x -> a.colorIndices[x.line.id]?.let { it to x.number } }.toMap()
        val extOrder = first.externals.filter { it.particle.color != ColorRep.None }.map { it.number }
        fun colorTensor(a: Amplitude, t: AmpTerm, side: Int): Array<Complex> {
            val ext = externalColors(a)
            // Name externals by leg number and internals by side, so they're summed separately.
            val remap = HashMap<CIdx, CIdx>()
            fun r(c: CIdx): CIdx = remap.getOrPut(c) {
                val n = ext[c]
                if (n != null) CIdx(-1000 - n, c.adjoint, "") else CIdx(c.id * 10 + side + 5_000_000, c.adjoint, c.hint)
            }
            val factors = t.color.map { f -> when (f) { is ColorFactor.T -> ColorFactor.T(r(f.a), r(f.i), r(f.j)); is ColorFactor.F -> ColorFactor.F(r(f.a), r(f.b), r(f.c)); is ColorFactor.D -> ColorFactor.D(r(f.i), r(f.j)) } }
            val externals = extOrder.map { n -> first.externals.first { it.number == n }.let { x -> CIdx(-1000 - n, x.particle.color == ColorRep.Octet, "") } }
            return Color.tensor(factors, externals)
        }

        val sum = ExprSum()
        for (a in amps) for (b in amps) {
            val sign = a.fermionSign * b.fermionSign
            for (ta in a.terms) for (tb in b.terms) {
                val color = Color.exact(Color.contract(colorTensor(a, ta, 0), colorTensor(b, tb, 1)))
                if (color.isZero) continue
                val conjB = conjugate(tb.expr)
                val piece = pairUp(ta.expr, a, conjB, b, kin)
                sum.add(piece, color * CQ.of(sign.toLong()))
            }
        }
        var total = sum.toExpr()
        total = Rules.simplifyRoots(total.substitute(Dim, Expr.const(4))) * average
        val dots = total
        val result = Rules.simplifyRoots(kin.apply(total))
        val traceTex = traceTex(amps, averageTex)
        return SquaredResult(average, averageTex, traceTex, dots, result, notes)
    }

    /**
     * Σ over spins and polarizations of one term of ℳ times one of ℳ*: the fermion lines are
     * followed through both into traces.
     */
    private fun pairUp(ea: Expr, a: Amplitude, eb: Expr, b: Amplitude, kin: Kinematics): Expr {
        val out = ExprSum()
        // Each leg must sit at the same end of its line in ℳ and ℳ*; with Majorana fermions or
        // fermion-number-violating vertices a line may have to be transposed first:
        // X̄₁ΓY₂ = −Ȳ′₂Γ′X′₁ (Γ′ = CΓᵀC⁻¹, u ↔ v at both ends).
        val (ta, tb) = transposes(a, b)
        val ends = a.chains.mapIndexed { i, ch -> if (ta[i]) ch.rightLeg to ch.leftLeg else ch.leftLeg to ch.rightLeg }
        val spinorOf = HashMap<Int, Spinor>()
        val original = HashMap<Int, Spinor>()
        a.chains.forEach { original[it.leftLeg.number] = it.left; original[it.rightLeg.number] = it.right }
        for ((l, r) in ends) {
            val sl = original[l.number]!!; val sr = original[r.number]!!
            spinorOf[l.number] = Spinor(if (l.incoming) Spinor.Kind.VBar else Spinor.Kind.UBar, sl.p, sl.mass)
            spinorOf[r.number] = Spinor(if (r.incoming) Spinor.Kind.U else Spinor.Kind.V, sr.p, sr.mass)
        }
        // Terms with the same Dirac strings and the same indexed factors (vectors, metrics, ε)
        // share their traces and contractions: only the scalar coefficients differ.
        val sa = structures(ea); val sb = structures(eb)
        for ((ka, ca) in sa) for ((kb, cb) in sb) {
            val shape = shape(ka, kb, a, b, ta, tb, spinorOf, kin)
            if (shape.isZero) continue
            out.addProduct(shape, ca * cb)
        }
        return out.toExpr()
    }

    private fun structures(e: Expr): Map<TermKey, Expr> {
        val out = LinkedHashMap<TermKey, Expr>()
        for ((k, c) in e.terms) {
            val indexed = k.mono.factors.filter { (at, _) -> at is Vec || at is Met || at is Eps }
            val scalar = k.mono.factors.filter { (at, _) -> !(at is Vec || at is Met || at is Eps) }
            val key = TermKey(Mono(indexed), k.chains)
            out[key] = (out[key] ?: Expr.ZERO) + Expr(mapOf(TermKey(Mono(scalar), emptyList()) to c))
        }
        return out.filterValues { !it.isZero }
    }

    /** Traces and contractions for one structure of ℳ and one of ℳ*. */
    private fun shape(k1: TermKey, k2: TermKey, a: Amplitude, b: Amplitude, ta: BooleanArray, tb: BooleanArray, spinorOf: Map<Int, Spinor>, kin: Kinematics): Expr {
        fun turn(str: GString): Pair<GString, Long> {
            var sign = -1L
            str.forEach { if (it !is G.Five) sign = -sign }
            return str.reversed() to sign
        }
        run {
            val mono = k1.mono * k2.mono
            var factor = CQ.ONE
            // Chains of ℳ: left leg → right leg. Barred chains of ℳ*: they run the other way.
            val mChains = a.chains.mapIndexed { i, ch ->
                if (!ta[i]) Triple(ch.leftLeg.number, ch.rightLeg.number, k1.chains[i])
                else turn(k1.chains[i]).let { (s, sg) -> factor = factor * CQ.of(sg); Triple(ch.rightLeg.number, ch.leftLeg.number, s) }
            }
            val cChains = b.chains.mapIndexed { i, ch ->
                if (!tb[i]) Triple(ch.rightLeg.number, ch.leftLeg.number, k2.chains[i])
                else turn(k2.chains[i]).let { (s, sg) -> factor = factor * CQ.of(sg); Triple(ch.leftLeg.number, ch.rightLeg.number, s) }
            }
            var scalar = Expr(mapOf(TermKey(mono, emptyList()) to factor))
            scalar = polarizationSums(scalar, a, kin)
            val done = HashSet<Int>()
            var traces = Expr.ONE
            for (start in mChains.indices) {
                if (start in done) continue
                var d = diracOne
                var cur = start
                val home = mChains[start].first
                var guard = 0
                while (guard++ < 20) {
                    done.add(cur)
                    val (_, r, str) = mChains[cur]
                    d = d.dirac(Expr(mapOf(TermKey(Mono.ONE, listOf(str)) to CQ.ONE))).dirac(spinSum(spinorOf[r]!!))
                    val back = cChains.first { it.first == r }
                    d = d.dirac(Expr(mapOf(TermKey(Mono.ONE, listOf(back.third)) to CQ.ONE))).dirac(spinSum(spinorOf[back.second]!!))
                    if (back.second == home) break
                    cur = mChains.indexOfFirst { it.first == back.second }
                }
                val tr = d.mapTerms { k, c -> trace(k.chains.first()) * Expr(mapOf(TermKey(k.mono, emptyList()) to c)) }
                traces *= tr
            }
            return contract(scalar * traces)
        }
    }

    /**
     * Which lines of ℳ and of ℳ* to transpose so that every leg is on the same end in both:
     * t_a xor t_b = end_a xor end_b for each leg, solved line by line.
     */
    private fun transposes(a: Amplitude, b: Amplitude): Pair<BooleanArray, BooleanArray> {
        val na = a.chains.size
        val ta = BooleanArray(na); val tb = BooleanArray(b.chains.size)
        // Node i < na: a's line i; na + j: b's line j. Edges carry the required xor.
        val where = HashMap<Int, MutableList<Pair<Int, Int>>>()
        a.chains.forEachIndexed { i, ch -> where.getOrPut(ch.leftLeg.number) { ArrayList() }.add(i to 0); where.getOrPut(ch.rightLeg.number) { ArrayList() }.add(i to 1) }
        b.chains.forEachIndexed { j, ch -> where.getOrPut(ch.leftLeg.number) { ArrayList() }.add(na + j to 0); where.getOrPut(ch.rightLeg.number) { ArrayList() }.add(na + j to 1) }
        val adj = HashMap<Int, MutableList<Pair<Int, Int>>>()
        for ((_, list) in where) {
            if (list.size != 2) continue
            val (x, y) = list
            val w = x.second xor y.second
            adj.getOrPut(x.first) { ArrayList() }.add(y.first to w)
            adj.getOrPut(y.first) { ArrayList() }.add(x.first to w)
        }
        val value = HashMap<Int, Int>()
        for (root in 0 until na + b.chains.size) {
            if (root in value) continue
            value[root] = 0
            val queue = ArrayDeque(listOf(root))
            while (queue.isNotEmpty()) {
                val u = queue.removeFirst()
                for ((v, w) in adj[u] ?: emptyList()) {
                    val want = value[u]!! xor w
                    if (v !in value) { value[v] = want; queue.add(v) }
                }
            }
        }
        for (i in 0 until na) ta[i] = value[i] == 1
        for (j in b.chains.indices) tb[j] = value[na + j] == 1
        return ta to tb
    }

    /** ε_n^μ ε_n^{*ν} → −g^{μν} (+ k^μk^ν/M² when massive). */
    private fun polarizationSums(e: Expr, a: Amplitude, kin: Kinematics): Expr = e.mapTerms { k, c ->
        var cur = Expr(mapOf(TermKey(k.mono, k.chains) to c))
        for (x in a.externals.filter { it.particle.isVector }) {
            val n = x.number
            val vs = k.mono.factors.map { it.first }.filterIsInstance<Vec>().filter { it.p == "eps$n" || it.p == "eps$n*" }
            if (vs.size != 2) continue
            val (v1, v2) = vs
            val m = kin.mass(x)
            var sum = met(v1.i, v2.i) * CQ.of(-1)
            if (m != null) sum += atom(Vec(x.momentum, v1.i)) * atom(Vec(x.momentum, v2.i)) * sym(m, -2)
            cur = cur.mapTerms { kk, cc ->
                Expr(mapOf(TermKey(kk.mono.without(v1).without(v2), kk.chains) to cc)) * sum
            }
        }
        cur
    }

    /** The traces written out, for one diagram whose amplitude is a single term. */
    private fun traceTex(amps: List<Amplitude>, averageTex: String): String? {
        if (amps.size != 1) return null
        val a = amps[0]
        if (a.chains.isEmpty() || a.terms.size != 1) return null
        val e = contractKeepingPolarizations(a.terms[0].expr)
        if (e.terms.size != 1) return null
        val (k, _) = e.terms.entries.first()
        if (k.chains.any { ch -> ch.any { it is G.S && it.p.startsWith("eps") } }) return null
        val names = IndexNames()
        val scalarTex = Tex.of(Expr(mapOf(TermKey(k.mono, emptyList()) to e.terms.values.first())), names)
        val sb = StringBuilder()
        val done = HashSet<Int>()
        val spin = HashMap<Int, Spinor>()
        a.chains.forEach { spin[it.leftLeg.number] = it.left; spin[it.rightLeg.number] = it.right }
        fun sTex(s: Spinor): String {
            val m = s.mass
            val p = "\\slashed{${MomNames.tex(s.p)}}"
            return if (m.isZero) p else "\\left($p ${if (s.diracSign > 0) "+" else "-"} ${Tex.of(m)}\\right)"
        }
        fun primed(i: Idx) = Idx(i.id + CONJ_OFFSET, null).also { names.reserve(it, names.name(i) + "'") }
        for ((ci, ch) in a.chains.withIndex()) {
            if (ci in done) continue
            done.add(ci)
            val str = k.chains[ci]
            val bar = str.reversed().map { if (it is G.I) G.I(primed(it.i)) else it }
            val seen = HashSet<Idx>()
            if (sb.isNotEmpty()) sb.append("\\, ")
            sb.append("\\mathrm{Tr}\\left[${sTex(ch.left)}\\,${Tex.chainTex(str, names, seen)}\\,${sTex(ch.right)}\\,${Tex.chainTex(bar, names, seen)}\\right]")
        }
        val scalar2 = if (scalarTex == "1") "" else "\\left|$scalarTex\\right|^{2}"
        return "$averageTex\\sum|\\mathcal{M}|^{2} = $averageTex $scalar2\\, $sb".replace("\\sum", "\\sum_{\\text{spins}}")
    }

    /** Contracts indices, except that ε stays a vector (so it can be recognised). */
    private fun contractKeepingPolarizations(e: Expr) = contract(e)
}
