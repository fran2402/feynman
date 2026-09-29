package com.example.feynman.physics

/*
 * One-loop integrals in dimensional regularization, d = 4 − 2ε:
 *
 *  1. Feynman parameters join the n loop propagators D_i = (k + o_i)² − m_i²:
 *     1/(D_1⋯D_n) = (n − 1)! ∫dF 1/(Σ x_i D_i)^n, with Σ x_i = 1;
 *  2. the shift k = ℓ − P, P = Σ x_i o_i, gives Σ x_i D_i = ℓ² − Δ, Δ = P² − Σ x_i(o_i² − m_i²);
 *  3. odd powers of ℓ vanish and ℓ^{μ1}⋯ℓ^{μ2a} → (ℓ²)^a Σ(pairings of g)/[d(d+2)⋯(d+2a−2)];
 *  4. ∫d^dℓ/(2π)^d (ℓ²)^a/(ℓ² − Δ)^n = (−1)^{n+a} i/(4π)^{d/2} · Γ(a+d/2)Γ(n−a−d/2)/(Γ(d/2)Γ(n)) · Δ^{d/2+a−n}
 *     (Peskin & Schroeder, (A.44)–(A.47));
 *  5. expanded in ε with 1/ε̄ = 1/ε − γ_E + ln 4π and Δ → Δ/μ² in the logarithm (MS-bar).
 *
 * The UV pole is integrated over the Feynman parameters exactly; the finite part is left as
 * an integral over them, evaluated numerically for given masses and momenta.
 */

/** One loop propagator denominator (k + o)² − m², as written. */
class LoopDenominator(val tex: String, val offset: Mom, val m2: Expr, val m2Tex: String)

/** The integral for one set of denominators (in a general Rξ gauge there can be several). */
class LoopPiece(
    val denominators: List<LoopDenominator>,
    val xs: List<Sym>,
    /** The shift P (as Σ coefficient × momentum). */
    val shift: List<Pair<Expr, String>>,
    val delta: Expr,
    /** Coefficient of i/(16π²ε̄), integrated over the Feynman parameters. */
    val pole: Expr,
    /** The finite part's integrand over the Feynman parameters (times i/(16π²)). */
    val finiteIntegrand: Expr,
    /** Its polynomial pieces integrated exactly; what's left needs numbers (or a closed form). */
    val finitePolynomial: Expr,
    val finiteRest: Expr,
    /** A₀, B₀, C₀ or D₀ with its arguments. */
    val scalarName: String,
    /** The numerator after the shift and symmetric integration, by power a of ℓ². */
    val reduced: Map<Int, Expr>,
    /** The numerator of this piece before the shift. */
    val numerator: Expr,
) {
    val n get() = denominators.size
    val free get() = if (n == 1) emptyList() else xs.dropLast(1)
}

class LoopResult(
    val pieces: List<LoopPiece>,
    /** The numerator after the traces, before the shift. */
    val numerator: Expr,
    val kinematics: Kinematics,
    val notes: List<String>,
    val chains: List<Chain>,
) {
    val pole: Expr get() = sum(pieces.map { it.pole })
    val finitePolynomial: Expr get() = sum(pieces.map { it.finitePolynomial })
    val isFinite get() = pole.isZero
    // The first (usually only) piece, for single-integral results.
    val denominators get() = pieces.first().denominators
    val n get() = pieces.first().n
    val xs get() = pieces.first().xs
    val delta get() = pieces.first().delta
    val finiteIntegrand get() = pieces.first().finiteIntegrand
    val finiteRest get() = pieces.first().finiteRest
    val shift get() = pieces.first().shift
    val scalarName get() = pieces.first().scalarName
    val reduced get() = pieces.first().reduced
}

object Loop {
    val deltaSym = Sym("Delta", "\\Delta", order = 60)
    val lnDelta = Sym("lnDelta", "\\ln\\frac{\\Delta}{\\mu^{2}}", order = 61)
    const val LOOP = "k"
    const val ELL = "l"

    fun feynmanParameter(i: Int, n: Int) = if (n == 2 && i == 1) Sym("x", "x", order = 45) else if (n == 2) Sym("x_2", "x_2", order = 45)
    else Sym("x$i", "x_{$i}", order = 45)

    /**
     * Rewrites the basic momentum [name] as Σ coefficient·momentum in vectors, dot products,
     * ε tensors and Dirac strings.
     */
    fun replaceMomentum(e: Expr, name: String, repl: List<Pair<Expr, String>>): Expr = e.mapTerms { k, c ->
        var out = Expr(mapOf(TermKey(Mono.ONE, emptyList()) to c))
        for ((a, n) in k.mono.factors) {
            val x: Expr = when {
                n > 0 && a is Vec && a.p == name -> sum(repl.map { (co, b) -> co * atom(Vec(b, a.i)) }).pow(n)
                n > 0 && a is Dot && (a.a == name || a.b == name) -> {
                    val other = if (a.a == name) a.b else a.a
                    val left = repl
                    val right = if (other == name) repl else listOf(Expr.ONE to other)
                    var s = Expr.ZERO
                    for ((c1, b1) in left) for ((c2, b2) in right) s += c1 * c2 * atom(Dot.of(b1, b2))
                    s.pow(n)
                }
                n > 0 && a is Eps && a.slots.any { it is Slot.P && it.p == name } -> {
                    var s = Expr.ONE
                    repeat(n) {
                        var one = Expr.ZERO
                        // Linear in each slot holding the momentum (at most once, or ε vanishes).
                        for ((co, b) in repl) one += co * levi(a.slots.map { sl -> if (sl is Slot.P && sl.p == name) Slot.P(b) else sl })
                        s *= one
                    }
                    s
                }
                else -> atom(a, n)
            }
            out *= x
        }
        // Dirac strings.
        var chains: List<Pair<Expr, List<GString>>> = listOf(Expr.ONE to emptyList())
        for (ch in k.chains) {
            var options: List<Pair<Expr, List<G>>> = listOf(Expr.ONE to emptyList())
            for (g in ch) {
                options = if (g is G.S && g.p == name) options.flatMap { (co, s) -> repl.map { (c2, b) -> co * c2 to s + G.S(b) } }
                else options.map { (co, s) -> co to s + g }
            }
            chains = chains.flatMap { (co, list) -> options.map { (c2, s) -> co * c2 to list + listOf(s) } }
        }
        var total = Expr.ZERO
        for ((co, list) in chains) {
            val withChains = Expr(out.terms.mapKeys { (kk, _) -> TermKey(kk.mono, list) })
            total += withChains * co
        }
        total
    }

    private fun pairings(list: List<Idx>): List<List<Pair<Idx, Idx>>> {
        if (list.isEmpty()) return listOf(emptyList())
        val first = list[0]
        val out = ArrayList<List<Pair<Idx, Idx>>>()
        for (j in 1 until list.size) {
            val rest = list.subList(1, j) + list.subList(j + 1, list.size)
            for (p in pairings(rest)) out.add(listOf(first to list[j]) + p)
        }
        return out
    }

    /**
     * Symmetric integration: every ℓ is turned into ℓ^μ with its own index, odd powers dropped,
     * and ℓ^{μ1}⋯ℓ^{μ2a} replaced by (1/2^a) Σ pairings (the rest of the factor is in the master
     * formula). Returns the coefficient of each power a.
     */
    fun tensorReduce(e: Expr): Map<Int, Expr> {
        val out = HashMap<Int, Expr>()
        for ((k, c) in e.terms) {
            val idx = ArrayList<Idx>()
            val extra = ArrayList<Expr>()
            val factors = ArrayList<Pair<Atom, Int>>()
            for ((a, n) in k.mono.factors) {
                when {
                    n > 0 && a is Vec && a.p == ELL -> repeat(n) { idx.add(a.i) }
                    n > 0 && a is Dot && a.a == ELL && a.b == ELL -> repeat(n) {
                        val i = Dummies.fresh(); val j = Dummies.fresh()
                        idx.add(i); idx.add(j); extra.add(met(i, j))
                    }
                    n > 0 && a is Dot && (a.a == ELL || a.b == ELL) -> repeat(n) {
                        val i = Dummies.fresh()
                        idx.add(i); extra.add(atom(Vec(if (a.a == ELL) a.b else a.a, i)))
                    }
                    n > 0 && a is Eps && a.slots.any { it is Slot.P && it.p == ELL } -> repeat(n) {
                        // ε with ℓ in a slot: give it an index.
                        val i = Dummies.fresh()
                        idx.add(i)
                        extra.add(levi(a.slots.map { if (it is Slot.P && it.p == ELL) Slot.I(i) else it }))
                    }
                    else -> factors.add(a to n)
                }
            }
            val chains = k.chains.map { ch ->
                ch.map { g -> if (g is G.S && g.p == ELL) Dummies.fresh().let { i -> idx.add(i); G.I(i) } else g }
            }
            if (idx.size % 2 == 1) continue
            val a = idx.size / 2
            var base = Expr(mapOf(TermKey(Mono.of(factors), chains) to c))
            for (x in extra) base *= x
            var paired = Expr.ZERO
            for (p in pairings(idx)) {
                var t = Expr.ONE
                for ((i, j) in p) t *= met(i, j)
                paired += t
            }
            val term = contract(base * paired) * Rational.of(1, 1L shl a)
            out[a] = (out[a] ?: Expr.ZERO) + term
        }
        return out.mapValues { contract(it.value) }.filterValues { !it.isZero }
    }

    private fun dDerivative(e: Expr): Expr = e.mapTerms { k, c ->
        val p = k.mono.power(Dim)
        if (p <= 0) Expr.ZERO else Expr(mapOf(TermKey(k.mono.without(Dim) * Mono.of(Dim, p - 1), k.chains) to c * CQ.of(p.toLong())))
    }

    private fun atFour(e: Expr) = e.substitute(Dim, Expr.const(4))

    /** Harmonic number H_k. */
    private fun harmonic(k: Int): Rational = (1..k).fold(Rational.ZERO) { acc, j -> acc + Rational.of(1, j.toLong()) }
    private fun factorial(k: Int): Long = (1..k).fold(1L) { acc, j -> acc * j }

    private fun isLoopDen(a: Atom) = a is Den && a.content.atoms().any { it is Dot && (it.a == LOOP || it.b == LOOP) }

    fun evaluate(amp: Amplitude, kin: Kinematics, reduceDirac: Boolean = true): LoopResult? {
        val topo = amp.topology
        if (topo.loops != 1) return null
        val notes = ArrayList<String>()
        if (amp.terms.size > 1) notes.add("The four-gluon vertex's color structures are added with the color factors left out.")
        // Group the terms by their loop denominators.
        val groups = LinkedHashMap<String, Pair<List<Pair<Den, Int>>, Expr>>()
        var all = Expr.ZERO
        for (t in amp.terms) for ((k, c) in t.expr.terms) {
            val dens = k.mono.factors.filter { (a, _) -> isLoopDen(a) }.map { (a, n) -> (a as Den) to n }
            val keep = k.mono.factors.filter { (a, _) -> !isLoopDen(a) }
            val term = Expr(mapOf(TermKey(Mono(keep), k.chains) to c))
            val key = dens.joinToString("|") { "${it.first.key}^${it.second}" }
            val prev = groups[key]
            groups[key] = dens to ((prev?.second ?: Expr.ZERO) + term)
            all += term
        }
        val pieces = ArrayList<LoopPiece>()
        for ((_, g) in groups) {
            val (dens, num) = g
            if (num.isZero) continue
            val list = dens.flatMap { (d, n) -> List(-n) { d } }
            if (list.isEmpty()) { notes.add("A term without loop propagators vanishes in dimensional regularization."); continue }
            piece(amp, kin, list, contract(num), reduceDirac, notes)?.let { pieces.add(it) }
        }
        if (pieces.isEmpty()) return null
        if (pieces.size > 1) notes.add("The propagators give ${pieces.size} integrals with different denominators; their poles and finite parts are added.")
        return LoopResult(pieces, contract(all), kin, notes.distinct(), amp.chains)
    }

    private fun piece(amp: Amplitude, kin: Kinematics, loopDens: List<Den>, numerator0: Expr, reduceDirac: Boolean, notes: MutableList<String>): LoopPiece? {
        var numerator = numerator0
        // Each denominator is (±k + r)² − m² = (k + o)² − m².
        var offsets = loopDens.map { d ->
            val q = d.q ?: return null
            val ck = q[LOOP] ?: return null
            (q - mom(LOOP).scale(ck)).scale(ck.reciprocal())
        }
        val masses2 = loopDens.map { it.m2 ?: Expr.ZERO }
        val n = loopDens.size
        // A two-point function: p₂ = ±p₁.
        if (kin.twoPoint) {
            val (a, b) = kin.externals
            val sgn = if (a.incoming != b.incoming) Rational.ONE else Rational.of(-1)
            numerator = replaceMomentum(numerator, b.momentum, listOf(Expr.const(sgn) to a.momentum))
            offsets = offsets.map { o ->
                val c = o[b.momentum] ?: return@map o
                (o - mom(b.momentum).scale(c)) + mom(a.momentum).scale(c * sgn)
            }
        }
        val dens = loopDens.mapIndexed { i, d ->
            val o = MomNames.tex(offsets[i])
            val kp = if (offsets[i].isEmpty()) "k^{2}" else "\\left(k ${if (o.startsWith("-")) "- ${o.drop(1)}" else "+ $o"}\\right)^{2}"
            val m2t = if (masses2[i].isZero) "0" else (d.display?.substringAfterLast(" - ") ?: Tex.of(masses2[i]))
            LoopDenominator(if (masses2[i].isZero) kp else "$kp - $m2t", offsets[i], masses2[i], m2t)
        }
        // Feynman parameters; the last is 1 − the others.
        val xs = (1..n).map { feynmanParameter(it, n) }
        val xExpr: List<Expr> = if (n == 1) listOf(Expr.ONE) else xs.dropLast(1).map { sym(it) }.let { it + (Expr.ONE - sum(it)) }
        // P = Σ x_i o_i as (coefficient, basic momentum) pairs.
        val shiftMap = LinkedHashMap<String, Expr>()
        for ((i, o) in offsets.withIndex()) for ((b, c) in o) shiftMap[b] = (shiftMap[b] ?: Expr.ZERO) + xExpr[i] * c
        val shift = shiftMap.entries.filter { !it.value.isZero }.map { it.value to it.key }
        fun dotOf(a: List<Pair<Expr, String>>, b: List<Pair<Expr, String>>): Expr {
            var s = Expr.ZERO
            for ((c1, m1) in a) for ((c2, m2) in b) s += c1 * c2 * atom(Dot.of(m1, m2))
            return s
        }
        var cTerm = Expr.ZERO
        for ((i, o) in offsets.withIndex()) {
            val ol = o.map { (b, c) -> Expr.const(c) to b }
            cTerm += xExpr[i] * (dotOf(ol, ol) - masses2[i])
        }
        var delta = dotOf(shift, shift) - cTerm
        delta = kin.apply(delta)
        // k = ℓ − P.
        val repl = listOf(Expr.ONE to ELL) + shift.map { (c, b) -> c * CQ.of(-1) to b }
        var shifted = replaceMomentum(numerator, LOOP, repl)
        shifted = contract(shifted)
        val reduced = tensorReduce(shifted).mapValues { (_, v) ->
            val e = kin.apply(v)
            if (reduceDirac && amp.chains.isNotEmpty() && !kin.twoPoint) reduceChains(e, amp.chains.map { it.left to it.right }) else e
        }
        // Master integrals, expanded in ε. The overall factor i/(16π²) is taken out.
        var pole = Expr.ZERO
        var finite = Expr.ZERO
        val d = sym(deltaSym)
        for ((a, coef) in reduced) {
            val sign = if ((n + a) % 2 == 0) 1L else -1L
            val m = n - a - 2
            val c4 = atFour(coef)
            if (m >= 1) {
                finite += c4 * sym(deltaSym, -m) * CQ.of(sign * factorial(m - 1))
            } else {
                val k = -m
                val pre = Expr.const(Rational.of(if (k % 2 == 0) 1 else -1, factorial(k))) * CQ.of(sign)
                val dk = if (k == 0) Expr.ONE else d.pow(k)
                pole += c4 * dk * pre
                finite += c4 * dk * pre * (Expr.const(harmonic(k)) - sym(lnDelta))
                finite += atFour(dDerivative(coef)) * dk * pre * CQ.of(-2)
            }
        }
        // Positive powers of Δ are written out; the logarithm and 1/Δⁿ stay.
        pole = Rules.simplifyRoots(kin.apply(pole.substitute(deltaSym, delta)))
        finite = Rules.simplifyRoots(kin.apply(finite.substitute(deltaSym, delta)))
        if (delta.isZero) notes.add("Δ = 0: the integral has no scale and vanishes in dimensional regularization.")
        val free = if (n == 1) emptyList() else xs.dropLast(1)
        val poleInt = integrateSimplex(pole, free)
        val (poly, rest) = finite.terms.entries.partition { (k, _) -> k.mono.power(lnDelta) == 0 && k.mono.power(deltaSym) == 0 }
        val finitePoly = integrateSimplex(Expr(poly.associate { it.key to it.value }), free)
        val finiteRest = Expr(rest.associate { it.key to it.value })
        val name = scalarName(dens, kin)
        return LoopPiece(dens, xs, shift, delta, poleInt, finite, finitePoly, finiteRest, name, reduced, numerator)
    }

    /** ∫ over the simplex of the polynomial terms in [free] (all must be polynomial). */
    fun integrateSimplex(e: Expr, free: List<Sym>): Expr {
        if (free.isEmpty()) return e
        val n = free.size
        return e.mapTerms { k, c ->
            val powers = free.map { k.mono.power(it) }
            if (powers.any { it < 0 }) return@mapTerms Expr(mapOf(k to c))
            var mono = k.mono
            free.forEach { mono = mono.without(it) }
            // ∫ Π x_i^{a_i} over x_1 + … + x_n ≤ 1 = Π a_i! / (Σ a_i + n)!
            var num = java.math.BigInteger.ONE
            powers.forEach { num *= bigFactorial(it) }
            val den = bigFactorial(powers.sum() + n)
            Expr(mapOf(TermKey(mono, k.chains) to c)) * Rational.of(num, den)
        }
    }

    private fun bigFactorial(k: Int): java.math.BigInteger = (1..k).fold(java.math.BigInteger.ONE) { acc, j -> acc * java.math.BigInteger.valueOf(j.toLong()) }

    private fun scalarName(dens: List<LoopDenominator>, kin: Kinematics): String {
        val offsets = dens.map { it.offset }
        fun m2(d: LoopDenominator) = d.m2Tex
        fun inv(a: Mom, b: Mom): String {
            val diff = a - b
            if (diff.isEmpty()) return "0"
            return Tex.of(kin.apply(dot(diff, diff)))
        }
        return when (dens.size) {
            1 -> "A_0\\left(${m2(dens[0])}\\right)"
            2 -> "B_0\\left(${inv(offsets[1], offsets[0])};\\ ${m2(dens[0])},\\ ${m2(dens[1])}\\right)"
            3 -> "C_0\\left(${inv(offsets[1], offsets[0])},\\ ${inv(offsets[2], offsets[1])},\\ ${inv(offsets[2], offsets[0])};\\ ${dens.joinToString(",\\ ") { m2(it) }}\\right)"
            4 -> "D_0\\left(${(0 until 4).joinToString(",\\ ") { inv(offsets[(it + 1) % 4], offsets[it]) }},\\ ${inv(offsets[2], offsets[0])},\\ ${inv(offsets[3], offsets[1])};\\ ${dens.joinToString(",\\ ") { m2(it) }}\\right)"
            else -> "\\text{(${dens.size}-point integral)}"
        }
    }
}
