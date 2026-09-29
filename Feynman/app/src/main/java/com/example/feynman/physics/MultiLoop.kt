package com.example.feynman.physics

import kotlin.math.ln

/*
 * Loop integrals with any number of loops (the app uses it for two and three), in d = 4 − 2ε:
 *
 *  1. The N propagators D_j = q_j² − m_j², q_j = Σ_a c_{ja} k_a + p_j, are joined with Feynman
 *     parameters: Π 1/D_j = (−1)^N Γ(N) ∫dx δ(1 − Σx) / (Σ x_j(−D_j))^N.
 *  2. Σ x_j(−D_j) = −kᵀMk − 2k·Q − J with M_{ab} = Σ x_j c_{ja}c_{jb}, Q_a = Σ x_j c_{ja}p_j,
 *     J = Σ x_j(p_j² − m_j²). The Symanzik polynomials are U = det M and
 *     F = Qᵀ adj(M) Q − U J (with −i0), and k_a = ℓ_a − (adj(M)Q)_a/U.
 *  3. Odd powers of ℓ vanish; the others pair up (Wick): each pair ℓ_a^μ ℓ_b^ν gives
 *     −½ g^{μν} adj(M)_{ab}/U and lowers the Γ below by one.
 *  4. ∫Π d^dℓ/(iπ^{d/2}) (r pairs) = Γ(N − Ld/2 − r)/Γ(N) … so that each term is
 *     Γ(N − Ld/2 − r) ∫dx δ(1 − Σx) P(x) U^{N − (L+1)d/2 − 2r − s} F^{Ld/2 − N + r},
 *     s the power of 1/U from the shift.
 *  5. ∫d^dk/(2π)^d = (i/16π²)(4π)^ε ∫d^dk/(iπ^{d/2}); in MS-bar each loop's (4π)^ε e^{−γε}
 *     goes into μ̄, so the answer is (i/16π²)^L Σ c_k ε^k with e^{Lγε} μ̄^{2Lε} kept.
 *
 * The Feynman-parameter integrals are done numerically by sector decomposition (Sectors.kt).
 */
object MultiLoop {
    val uInverse = Sym("Uinv", "\\mathcal{U}^{-1}", order = 46)
    fun x(i: Int) = Sym("fx$i", "x_{$i}", order = 45)

    class Piece(
        /** The propagator denominators, as written. */
        val denominators: List<String>,
        val n: Int,
        val loops: Int,
        val xs: List<Sym>,
        val u: Expr,
        val f: Expr,
        /** k_a = ℓ_a − shift_a/U, as LaTeX. */
        val shifts: List<String>,
        /** The numerator after the shift and the pairings, by the number of pairs r. */
        val reduced: Map<Int, Expr>,
        /** When F = −v·F₀(x) with F₀ ≥ 0 (one scale, no masses): v. */
        val scale: Sym?,
    )

    class Result(val pieces: List<Piece>, val kinematics: Kinematics, val chains: List<Chain>, val notes: List<String>, val loops: Int)

    /** One structure of the answer: coefficients of (i/16π²)^L ε^k, k = −2L … 0, with errors. */
    class Row(val structureTex: String, val coefficients: List<Pair<Int, Complex>>, val errors: List<Double>, val note: String? = null)

    private fun isLoopDen(a: Atom, names: List<String>) = a is Den && a.content.atoms().any { it is Dot && (it.a in names || it.b in names) }

    fun prepare(amp: Amplitude, kin: Kinematics): Result? {
        val topo = amp.topology
        val names = topo.loopMomenta
        if (names.isEmpty()) return null
        val notes = ArrayList<String>()
        if (amp.terms.size > 1) notes.add("The color structures are added with the color factors left out.")
        val groups = LinkedHashMap<String, Pair<List<Pair<Den, Int>>, Expr>>()
        for (t in amp.terms) for ((k, c) in t.expr.terms) {
            val dens = k.mono.factors.filter { (a, _) -> isLoopDen(a, names) }.map { (a, n) -> (a as Den) to n }
            val keep = k.mono.factors.filter { (a, _) -> !isLoopDen(a, names) }
            val term = Expr(mapOf(TermKey(Mono(keep), k.chains) to c))
            val key = dens.joinToString("|") { "${it.first.key}^${it.second}" }
            val prev = groups[key]
            groups[key] = dens to ((prev?.second ?: Expr.ZERO) + term)
        }
        val pieces = ArrayList<Piece>()
        for ((_, g) in groups) {
            val (dens, num) = g
            if (num.isZero) continue
            val list = dens.flatMap { (d, n) -> List(-n) { d } }
            if (list.isEmpty()) { notes.add("A term without loop propagators vanishes in dimensional regularization."); continue }
            piece(list, contract(num), names, kin, amp.chains)?.let { pieces.add(it) }
        }
        if (pieces.isEmpty()) return null
        if (pieces.size > 1) notes.add("The propagators give ${pieces.size} integrals with different denominators; they're added.")
        return Result(pieces, kin, amp.chains, notes.distinct(), names.size)
    }

    /** The parametric form of one integral: [dens] with loop momenta [names] and numerator [numerator0]. */
    fun piece(dens: List<Den>, numerator0: Expr, names: List<String>, kin: Kinematics, chains: List<Chain> = emptyList()): Piece? {
        val nL = names.size
        val n = dens.size
        var numerator = numerator0
        val c = Array(n) { j -> IntArray(0).let { Array(nL) { a -> dens[j].q?.get(names[a]) ?: Rational.ZERO } } }
        var ps: List<Mom> = dens.map { d -> (d.q ?: return null).filterKeys { it !in names } }
        if (kin.twoPoint) {
            val (a, b) = kin.externals
            val sgn = if (a.incoming != b.incoming) Rational.ONE else Rational.of(-1)
            numerator = Loop.replaceMomentum(numerator, b.momentum, listOf(Expr.const(sgn) to a.momentum))
            ps = ps.map { o ->
                val cb = o[b.momentum] ?: return@map o
                (o - mom(b.momentum).scale(cb)) + mom(a.momentum).scale(cb * sgn)
            }
        }
        val xs = (1..n).map { x(it) }
        val xe = xs.map { sym(it) }
        val m = Array(nL) { a -> Array(nL) { b -> sum((0 until n).map { j -> xe[j] * (c[j][a] * c[j][b]) }) } }
        val (u, adj) = determinant(m)
        // Q_a by external momentum.
        val q = List(nL) { a ->
            val out = LinkedHashMap<String, Expr>()
            for (j in 0 until n) if (c[j][a].signum != 0) for ((p, cp) in ps[j]) out[p] = (out[p] ?: Expr.ZERO) + xe[j] * (c[j][a] * cp)
            out.filterValues { !it.isZero }
        }
        fun dotQ(x1: Map<String, Expr>, x2: Map<String, Expr>): Expr {
            var s = Expr.ZERO
            for ((p1, c1) in x1) for ((p2, c2) in x2) s += c1 * c2 * atom(Dot.of(p1, p2))
            return s
        }
        var j = Expr.ZERO
        for (i in 0 until n) j += xe[i] * (dot(ps[i], ps[i]) - (dens[i].m2 ?: Expr.ZERO))
        var qaq = Expr.ZERO
        for (a in 0 until nL) for (b in 0 until nL) qaq += adj[a][b] * dotQ(q[a], q[b])
        val f = Rules.simplifyRoots(kin.apply(qaq - u * j))
        // k_a = ℓ_a − (adj Q)_a / U.
        val lNames = if (nL == 1) listOf(Loop.ELL) else (1..nL).map { "l$it" }
        val shifts = ArrayList<String>()
        var e = numerator
        for (a in 0 until nL) {
            val s = LinkedHashMap<String, Expr>()
            for (b in 0 until nL) for ((p, cp) in q[b]) s[p] = (s[p] ?: Expr.ZERO) + adj[a][b] * cp
            val repl = listOf(Expr.ONE to lNames[a]) + s.filterValues { !it.isZero }.map { (p, cp) -> cp * sym(uInverse) * CQ.of(-1) to p }
            val ell = if (nL == 1) "\\ell" else "\\ell_{${a + 1}}"
            shifts.add("${MomNames.tex(names[a])} = $ell" + s.filterValues { !it.isZero }.entries.joinToString("") { (p, cp) ->
                if (cp.terms.size == 1 && Tex.of(cp).startsWith("-")) " + \\frac{${Tex.of(-cp)}}{\\mathcal{U}}\\,${MomNames.tex(p)}"
                else " - \\frac{${Tex.of(cp)}}{\\mathcal{U}}\\,${MomNames.tex(p)}"
            })
            e = contract(Loop.replaceMomentum(e, names[a], repl))
        }
        val reduced = wick(e, lNames, adj).mapValues { (_, v) ->
            val w = kin.apply(v)
            if (chains.isNotEmpty() && !kin.twoPoint) reduceChains(w, chains.map { it.left to it.right }) else w
        }.filterValues { !it.isZero }
        val denTex = dens.map { d ->
            val m2 = d.m2 ?: Expr.ZERO
            val qq = d.q ?: emptyMap()
            val s = "\\left(${MomNames.tex(qq)}\\right)^{2}"
            if (m2.isZero) s else "$s - ${d.display?.substringAfterLast(" - ") ?: Tex.of(m2)}"
        }
        return Piece(denTex, n, nL, xs, u, f, shifts, reduced, singleScale(f, xs))
    }

    /** det M and its adjugate, for L ≤ 3. */
    private fun determinant(m: Array<Array<Expr>>): Pair<Expr, Array<Array<Expr>>> {
        val l = m.size
        return when (l) {
            1 -> m[0][0] to arrayOf(arrayOf(Expr.ONE))
            2 -> (m[0][0] * m[1][1] - m[0][1] * m[1][0]) to arrayOf(arrayOf(m[1][1], -m[0][1]), arrayOf(-m[1][0], m[0][0]))
            3 -> {
                fun cof(r: Int, c: Int): Expr {
                    val rows = (0 until 3).filter { it != r }; val cols = (0 until 3).filter { it != c }
                    val d = m[rows[0]][cols[0]] * m[rows[1]][cols[1]] - m[rows[0]][cols[1]] * m[rows[1]][cols[0]]
                    return if ((r + c) % 2 == 0) d else -d
                }
                val adj = Array(3) { a -> Array(3) { b -> cof(b, a) } }
                val det = m[0][0] * cof(0, 0) + m[0][1] * cof(0, 1) + m[0][2] * cof(0, 2)
                det to adj
            }
            else -> {
                // Laplace expansion (slow, but L > 3 isn't used).
                fun minor(mm: List<List<Expr>>, r: Int, cc: Int) = mm.filterIndexed { i, _ -> i != r }.map { row -> row.filterIndexed { k, _ -> k != cc } }
                fun det(mm: List<List<Expr>>): Expr = if (mm.size == 1) mm[0][0] else sum(mm.indices.map { k -> mm[0][k] * det(minor(mm, 0, k)) * CQ.of(if (k % 2 == 0) 1 else -1) })
                val list = m.map { it.toList() }
                det(list) to Array(l) { a -> Array(l) { b -> det(minor(list, b, a)) * CQ.of(if ((a + b) % 2 == 0) 1 else -1) } }
            }
        }
    }

    private fun pairings(n: Int): List<List<Pair<Int, Int>>> {
        if (n == 0) return listOf(emptyList())
        val out = ArrayList<List<Pair<Int, Int>>>()
        fun rec(rest: List<Int>, acc: List<Pair<Int, Int>>) {
            if (rest.isEmpty()) { out.add(acc); return }
            val a = rest[0]
            for (k in 1 until rest.size) rec(rest.subList(1, k) + rest.subList(k + 1, rest.size), acc + (a to rest[k]))
        }
        rec((0 until n).toList(), emptyList())
        return out
    }

    /**
     * Every ℓ_a gets an index; odd numbers vanish; each pairing of (a, μ), (b, ν) gives
     * −½ g^{μν} adj_{ab} (the 1/U is in the U power). Returns the numerator by the number of pairs.
     */
    fun wick(e: Expr, lNames: List<String>, adj: Array<Array<Expr>>): Map<Int, Expr> {
        val out = HashMap<Int, Expr>()
        val cache = HashMap<Int, List<List<Pair<Int, Int>>>>()
        for ((k, c) in e.terms) {
            val slots = ArrayList<Pair<Int, Idx>>()
            val extra = ArrayList<Expr>()
            val factors = ArrayList<Pair<Atom, Int>>()
            fun li(p: String) = lNames.indexOf(p)
            for ((a, n) in k.mono.factors) {
                when {
                    n > 0 && a is Vec && li(a.p) >= 0 -> repeat(n) { slots.add(li(a.p) to a.i) }
                    n > 0 && a is Dot && li(a.a) >= 0 && li(a.b) >= 0 -> repeat(n) {
                        val i = Dummies.fresh(); val j = Dummies.fresh()
                        slots.add(li(a.a) to i); slots.add(li(a.b) to j); extra.add(met(i, j))
                    }
                    n > 0 && a is Dot && (li(a.a) >= 0 || li(a.b) >= 0) -> repeat(n) {
                        val i = Dummies.fresh()
                        val (l, other) = if (li(a.a) >= 0) a.a to a.b else a.b to a.a
                        slots.add(li(l) to i); extra.add(atom(Vec(other, i)))
                    }
                    n > 0 && a is Eps && a.slots.any { it is Slot.P && li(it.p) >= 0 } -> repeat(n) {
                        var cur = a.slots
                        val newSlots = cur.map { s -> if (s is Slot.P && li(s.p) >= 0) Dummies.fresh().let { i -> slots.add(li(s.p) to i); Slot.I(i) } else s }
                        extra.add(levi(newSlots))
                    }
                    else -> factors.add(a to n)
                }
            }
            val chains = k.chains.map { ch -> ch.map { g -> if (g is G.S && li(g.p) >= 0) Dummies.fresh().let { i -> slots.add(li(g.p) to i); G.I(i) } else g } }
            if (slots.size % 2 == 1) continue
            val r = slots.size / 2
            var base = Expr(mapOf(TermKey(Mono.of(factors), chains) to c))
            for (x in extra) base *= x
            var paired = Expr.ZERO
            for (p in cache.getOrPut(slots.size) { pairings(slots.size) }) {
                var t = Expr.ONE
                for ((x, y) in p) t = t * met(slots[x].second, slots[y].second) * adj[slots[x].first][slots[y].first] * Rational.of(-1, 2)
                paired += t
            }
            out[r] = (out[r] ?: Expr.ZERO) + contract(base * paired)
        }
        return out.mapValues { contract(it.value) }.filterValues { !it.isZero }
    }

    /** F = −v·F₀(x) with F₀'s coefficients ≥ 0 and v a single invariant: v, else null. */
    private fun singleScale(f: Expr, xs: List<Sym>): Sym? {
        if (f.isZero) return null
        val others = f.atoms().filter { it !in xs }
        val v = others.singleOrNull() as? Sym ?: return null
        if (!v.isKinematic) return null
        for ((k, cq) in f.terms) {
            if (k.mono.power(v) != 1) return null
            if (!cq.isReal || cq.re.signum > 0) return null
        }
        return v
    }

    // --- Numbers ----------------------------------------------------------------------------

    /** For tests: called with each sector's exponents and result. */
    var trace: ((String) -> Unit)? = null

    /** Accuracy: points per random shift and the number of shifts, per sector. */
    class Accuracy(val points: Int = 4096, val shifts: Int = 5)

    private fun toPoly(e: Expr, xs: List<Sym>, values: Map<String, Double>): Poly? {
        val n = xs.size
        val exps = ArrayList<IntArray>(); val re = ArrayList<Double>(); val im = ArrayList<Double>()
        for ((k, c) in e.terms) {
            var v = c.toComplex()
            val ex = IntArray(n)
            for ((a, p) in k.mono.factors) {
                val i = xs.indexOf(a)
                if (i >= 0) { ex[i] += p; continue }
                val num = Evaluate.scalar(atom(a, p), values) ?: return null
                v *= num
            }
            exps.add(ex); re.add(v.re); im.add(v.im)
        }
        return Poly(n, exps, re.toDoubleArray(), im.toDoubleArray()).merged()
    }

    private fun dropVar(p: Poly, l: Int): Poly = Poly(p.n - 1, p.exps.map { e -> IntArray(p.n - 1) { if (it < l) e[it] else e[it + 1] } }, p.cre, p.cim).merged()

    /**
     * The numbers for [r]: for each Lorentz/Dirac structure, the Laurent coefficients of
     * (i/16π²)^L up to ε⁰, with MS-bar scale [mu] (GeV).
     */
    fun evaluate(r: Result, values: Map<String, Double>, mu: Double, open: (Int) -> String, close: (Int) -> String, acc: Accuracy = Accuracy()): List<Row> {
        val rows = LinkedHashMap<TermKey, Pair<Series, DoubleArray>>()
        val notes = LinkedHashMap<TermKey, String>()
        val names = IndexNames()
        for (piece in r.pieces) {
            val l = piece.loops
            val nn = piece.n
            val scale = piece.scale
            val fValues = if (scale != null) values + (scale.name to -1.0) else values
            val uPoly = toPoly(piece.u, piece.xs, values) ?: continue
            val fPoly = toPoly(piece.f, piece.xs, fValues) ?: continue
            // Split the numerator: structure × Σ d^m U^{−s} P(x).
            class Group(val r: Int, val s: Int, val m: Int)
            val parts = LinkedHashMap<TermKey, LinkedHashMap<Group, Expr>>()
            val groupKeys = HashMap<Triple<Int, Int, Int>, Group>()
            for ((pairs, e) in piece.reduced) for ((k, c) in e.terms) {
                val numeric = k.mono.factors.filter { (a, _) -> a is Sym || a is Den || a == Dim }
                val rest = k.mono.factors.filter { (a, _) -> !(a is Sym || a is Den || a == Dim) }
                val s = k.mono.power(uInverse)
                val mm = k.mono.power(Dim)
                val g = groupKeys.getOrPut(Triple(pairs, s, mm)) { Group(pairs, s, mm) }
                val key = TermKey(Mono(rest), k.chains)
                val coef = Expr(mapOf(TermKey(Mono(numeric.filter { it.first != uInverse && it.first != Dim }), emptyList()) to c))
                val byGroup = parts.getOrPut(key) { LinkedHashMap() }
                byGroup[g] = (byGroup[g] ?: Expr.ZERO) + coef
            }
            if (fPoly.isZero) {
                // No scale: zero in dimensional regularization.
                for (key in parts.keys) { rows.putIfAbsent(key, Series.zero() to DoubleArray(0)); notes[key] = "No scale: the integral vanishes in dimensional regularization." }
                continue
            }
            // Is F sign-definite? (Otherwise it's above a threshold.)
            var fNeg = false; var fPos = false
            val rnd = java.util.Random(7)
            val pt = DoubleArray(nn)
            repeat(4000) {
                var sum = 0.0
                for (i in 0 until nn) { pt[i] = -ln(1 - rnd.nextDouble()); sum += pt[i] }
                for (i in 0 until nn) pt[i] /= sum
                val v = fPoly.eval(pt).re
                if (v < 0) fNeg = true
                if (v > 0) fPos = true
            }
            if (fNeg && fPos) {
                for (key in parts.keys) { rows.putIfAbsent(key, Series.zero() to DoubleArray(0)); notes[key] = "F changes sign inside the integration region (above a threshold): the numbers would need a contour deformation, which isn't done here." }
                continue
            }
            // Primary sectors and their decomposition (shared by every term).
            val primary = (0 until nn).map { lv -> lv to Sectors.decompose(dropVar(uPoly, lv), dropVar(fPoly, lv)) }
            if (primary.any { it.second == null }) {
                for (key in parts.keys) { rows.putIfAbsent(key, Series.zero() to DoubleArray(0)); notes[key] = "The sector decomposition didn't terminate for this integral." }
                continue
            }
            val top = 0
            for ((key, byGroup) in parts) {
                var total: Series? = null
                var err2 = DoubleArray(2 * l + 3)
                // Terms with the same r and s share exponents: collect their d^m pieces.
                val byRS = byGroup.entries.groupBy { it.key.r to it.key.s }
                for ((rs, list) in byRS) {
                    val (pairs, s) = rs
                    val polys = list.mapNotNull { (g, e) -> toPoly(e, piece.xs, values)?.let { g.m to it } }.filter { !it.second.isZero }
                    if (polys.isEmpty()) continue
                    val aU = (nn - 2.0 * (l + 1) - 2 * pairs - s) to (l + 1.0)
                    val aF = (2.0 * l - nn + pairs) to (-l.toDouble())
                    var sumRS: Series? = null
                    val errRS = DoubleArray(2 * l + 3)
                    var seed = 1L
                    for ((lv, sectors) in primary) for (sec in sectors!!) {
                        val inSec = polys.map { (mm, p) -> mm to Sectors.inSector(dropVar(p, lv), sec) }.filter { !it.second.isZero }
                        if (inSec.isEmpty()) continue
                        val dim = nn - 1
                        val mP = IntArray(dim) { v -> inSec.minOf { (_, p) -> p.exps.minOf { it[v] } } }
                        val terms = inSec.map { (mm, p) ->
                            var ds = Series.const(1.0)
                            repeat(mm) { ds = ds.times(Series.linear(4.0, -2.0), 8) }
                            Sectors.Term(ds, Poly(dim, p.exps.map { e -> IntArray(dim) { e[it] - mP[it] } }, p.cre, p.cim), 0, 0)
                        }
                        val e = IntArray(dim)
                        val fe = DoubleArray(dim)
                        var ok = true
                        for (v in 0 until dim) {
                            val a0 = sec.jacobian[v] + sec.mU[v] * aU.first + sec.mF[v] * aF.first + mP[v]
                            val a1 = sec.mU[v] * aU.second + sec.mF[v] * aF.second
                            val r0 = Math.round(a0)
                            if (kotlin.math.abs(a0 - r0) > 1e-9) ok = false
                            e[v] = r0.toInt(); fe[v] = a1
                            if (e[v] <= -1 && kotlin.math.abs(a1) < 1e-12) ok = false
                        }
                        if (!ok) { notes[key] = "An unregulated singularity in the Feynman parameters."; continue }
                        val reg = Sectors.Reg(sec.u, sec.f, aU, aF, terms)
                        val est = Sectors.integrate(dim, e, fe, reg, top + 1, acc.points, acc.shifts, seed++)
                        trace?.invoke("sector l=$lv e=${e.toList()} f=${fe.toList()} U=${sec.u.size} F=${sec.f.size} -> " + (est.value.low..est.value.high).joinToString { "%.4f".format(est.value[it].re) })
                        sumRS = sumRS?.plus(est.value) ?: est.value
                        for (k in est.error.indices) {
                            val idx = est.value.low + k + 2 * l + 1
                            if (idx in errRS.indices) errRS[idx] += est.error[k] * est.error[k]
                        }
                    }
                    val integral = sumRS ?: continue
                    // Γ(N − Ld/2 − r) (−1)^N e^{Lγε} μ̄^{2Lε} and, with one scale, (−v − i0)^{a_F}.
                    // Each factor to high enough order: Γ can start at 1/ε, and the integral at 1/ε^{2L−1}.
                    val deep = top + 2 * l + 2
                    var pre = Series.gamma(nn - 2 * l - pairs, l.toDouble(), deep).scale(if (nn % 2 == 0) 1.0 else -1.0)
                    pre = pre.times(Series.expOf(Complex(l * (0.5772156649015329 + ln(mu * mu))), deep + 1), deep)
                    if (scale != null) {
                        val v = values[scale.name] ?: 0.0
                        val lv = Sectors.logOf(Complex(-v))
                        pre = pre.times(Sectors.powSeries(lv, aF.first, aF.second, deep + 1), deep)
                    }
                    val res = integral.times(pre, top)
                    total = total?.plus(res) ?: res
                    // Errors, scaled by the largest prefactor coefficient (a rough bound).
                    val preMax = (pre.low..pre.high).maxOfOrNull { pre[it].abs } ?: 1.0
                    for (k in errRS.indices) err2[k] += errRS[k] * preMax * preMax
                }
                val prev = rows[key]
                val t = total ?: Series.zero()
                rows[key] = (prev?.first?.plus(t) ?: t) to (prev?.second?.let { p -> DoubleArray(err2.size) { p.getOrElse(it) { 0.0 } + err2[it] } } ?: err2)
            }
        }
        val l = r.loops
        return rows.map { (k, v) ->
            val (series, err) = v
            val tex = Tex.of(Expr(mapOf(k to CQ.ONE)), names, open, close)
            val coefs = (-2 * l..0).map { it to series[it] }
            val errs = (-2 * l..0).map { kk -> kotlin.math.sqrt(err.getOrElse(kk + 2 * l + 1) { 0.0 }) }
            Row(tex, coefs, errs, notes[k])
        }
    }
}
