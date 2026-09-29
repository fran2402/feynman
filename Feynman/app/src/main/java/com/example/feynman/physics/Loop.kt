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

class LoopDenominator(val line: Line, val particle: Particle, val offset: Mom, val mass: Sym?)

class LoopResult(
    val denominators: List<LoopDenominator>,
    val xs: List<Sym>,
    /** The shift P (as Σ coefficient × momentum). */
    val shift: List<Pair<Expr, String>>,
    val delta: Expr,
    /** Coefficient of i/(16π²ε̄), integrated over the Feynman parameters. */
    val pole: Expr,
    /** The finite part's integrand over the Feynman parameters (times i/(16π²)). */
    val finiteIntegrand: Expr,
    /** Its polynomial pieces integrated exactly; what's left needs numbers. */
    val finitePolynomial: Expr,
    val finiteRest: Expr,
    /** A₀, B₀, C₀ or D₀ with its arguments. */
    val scalarName: String,
    /** The numerator after the traces, before the shift. */
    val numerator: Expr,
    /** The numerator after the shift and symmetric integration, by power a of ℓ². */
    val reduced: Map<Int, Expr>,
    val kinematics: Kinematics,
    val notes: List<String>,
    val chains: List<Chain>,
) {
    val n get() = denominators.size
    val isFinite get() = pole.isZero
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

    fun evaluate(amp: Amplitude, kin: Kinematics, reduceDirac: Boolean = true): LoopResult? {
        val topo = amp.topology
        if (topo.loops != 1) return null
        val notes = ArrayList<String>()
        // Loop denominators: the loop lines' Den atoms.
        val dens = ArrayList<LoopDenominator>()
        for ((line, q) in amp.loopLines) {
            val p = SM.byId(line.particle) ?: continue
            val ck = q[LOOP] ?: continue
            val offset = (q - mom(LOOP).scale(ck)).scale(ck.reciprocal())
            val mass = when {
                p.isFermion -> amp.ctx.massOf(p)
                p === SM.photon || p === SM.gluon || p === SM.ghostA || p === SM.ghostG -> null
                else -> p.mass
            }
            dens.add(LoopDenominator(line, p, offset, mass))
        }
        if (dens.isEmpty()) return null
        val n = dens.size
        // The numerator: the amplitude without the loop denominators (colors are separate).
        var numerator = Expr.ZERO
        for (t in amp.terms) {
            numerator += t.expr.mapTerms { k, c ->
                val keep = k.mono.factors.filter { (a, _) ->
                    !(a is Den && a.content.atoms().any { it is Dot && (it.a == LOOP || it.b == LOOP) })
                }
                Expr(mapOf(TermKey(Mono(keep), k.chains) to c))
            }
        }
        if (amp.terms.size > 1) notes.add("The four-gluon vertex's color structures are added with the color factors left out.")
        numerator = contract(numerator)
        // A two-point function: p₂ = ±p₁.
        var offsets = dens.map { it.offset }
        if (kin.twoPoint) {
            val (a, b) = kin.externals
            val sgn = if (a.incoming != b.incoming) Rational.ONE else Rational.of(-1)
            numerator = replaceMomentum(numerator, b.momentum, listOf(Expr.const(sgn) to a.momentum))
            offsets = offsets.map { o ->
                val c = o[b.momentum] ?: return@map o
                (o - mom(b.momentum).scale(c)) + mom(a.momentum).scale(c * sgn)
            }
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
        val masses2 = dens.map { d -> d.mass?.let { sym(it, 2) } ?: Expr.ZERO }
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
        pole = kin.apply(pole.substitute(deltaSym, delta))
        finite = kin.apply(finite.substitute(deltaSym, delta))
        pole = Rules.simplifyRoots(pole)
        finite = Rules.simplifyRoots(finite)
        if (delta.isZero) notes.add("Δ = 0: the integral has no scale and vanishes in dimensional regularization.")
        val free = if (n == 1) emptyList() else xs.dropLast(1)
        val poleInt = integrateSimplex(pole, free)
        val (poly, rest) = finite.terms.entries.partition { (k, _) -> k.mono.power(lnDelta) == 0 && k.mono.power(deltaSym) == 0 }
        val finitePoly = integrateSimplex(Expr(poly.associate { it.key to it.value }), free)
        val finiteRest = Expr(rest.associate { it.key to it.value })
        val name = scalarName(dens, offsets, kin)
        return LoopResult(dens, xs, shift, delta, poleInt, finite, finitePoly, finiteRest, name, numerator, reduced, kin, notes.distinct(), amp.chains)
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

    private fun scalarName(dens: List<LoopDenominator>, offsets: List<Mom>, kin: Kinematics): String {
        fun m2(d: LoopDenominator) = d.mass?.let { "${it.tex}^{2}" } ?: "0"
        fun inv(a: Mom, b: Mom): String {
            val diff = a - b
            if (diff.isEmpty()) return "0"
            val e = kin.apply(dot(diff, diff))
            return Tex.of(e)
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
