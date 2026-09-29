package com.example.feynman.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Numbers for the symbolic answers: a value for every coupling, mass and invariant, then the
 * UV pole and finite part of a loop amplitude (in closed form for two-point integrals, by
 * Gauss–Legendre quadrature on the simplex otherwise) or a squared tree amplitude.
 */
object Evaluate {
    /** Standard Model inputs (GeV), PDG 2024; s, t, u and p² are just starting points. */
    val defaults: Map<String, Double> by lazy {
        val alpha = 1 / 137.035999
        val e = sqrt(4 * PI * alpha)
        val mW = 80.3692; val mZ = 91.1880
        val cW = mW / mZ
        val sW = sqrt(1 - cW * cW)
        // 2HDM benchmark: tan β = 2, cos(β − α) = 0.1.
        val beta = atan(2.0)
        val alpha2 = beta - kotlin.math.acos(0.1)
        mapOf(
            "e" to e, "g" to e / sW, "gs" to sqrt(4 * PI * 0.1180), "cW" to cW, "sW" to sW,
            "mW" to mW, "mZ" to mZ, "mh" to 125.20,
            "m_e" to 0.000510999, "m_mu" to 0.105658, "m_tau" to 1.77693,
            "m_u" to 0.00216, "m_d" to 0.00470, "m_s" to 0.0935, "m_c" to 1.2730, "m_b" to 4.183, "m_t" to 172.57,
            "s" to 200.0 * 200.0, "t" to -5000.0, "u" to -35000.0, "psq" to 50.0,
            "V_ud" to 0.97367, "V_us" to 0.22431, "V_ub" to 0.00382, "V_cd" to 0.221, "V_cs" to 0.975, "V_cb" to 0.0411,
            "V_td" to 0.0086, "V_ts" to 0.0415, "V_tb" to 1.010,
            "xi" to 1.0,
            "m_phi" to 1.0, "lambda" to 0.1, "kappa" to 1.0,
            "mH" to 600.0, "mA" to 600.0, "mHp" to 620.0, "mZp" to 3000.0, "gZp" to e / sW,
            "calpha" to cos(alpha2), "salpha" to sin(alpha2), "cbeta" to cos(beta), "sbeta" to sin(beta),
        )
    }

    /** Total widths (GeV) for Breit–Wigner propagators, by mass symbol. */
    val widths: Map<String, Double> = mapOf(
        "mZ" to 2.4955, "mW" to 2.085, "mh" to 0.0037, "m_t" to 1.42,
        "mH" to 5.0, "mA" to 5.0, "mHp" to 5.0, "mZp" to 90.0,
    )

    /** One Lorentz/Dirac structure of the answer and its numerical coefficient(s). */
    class Row(val structureTex: String, val pole: Complex, val finite: Complex)

    private fun value(a: Sym, values: Map<String, Double>): Double? {
        if (a == Rules.sqrt2) return sqrt(2.0)
        return values[a.name] ?: values[a.name.removeSuffix("*")]
    }

    /** Splits each term into its numbers (symbols) and its structure (everything else). */
    private fun split(e: Expr): Map<TermKey, List<Pair<CQ, List<Pair<Atom, Int>>>>> {
        val out = LinkedHashMap<TermKey, MutableList<Pair<CQ, List<Pair<Atom, Int>>>>>()
        for ((k, c) in e.terms) {
            val numeric = k.mono.factors.filter { (a, _) -> a is Sym || a is Den }
            val rest = k.mono.factors.filter { (a, _) -> !(a is Sym || a is Den) }
            out.getOrPut(TermKey(Mono(rest), k.chains)) { ArrayList() }.add(c to numeric)
        }
        return out
    }

    /**
     * A scalar expression's value (null if a symbol has no value). [dots] gives dot products of
     * momenta (for phase-space integration); with [breitWigner], a propagator of an unstable
     * particle is |q² − M² + iMΓ|⁻² when squared (and its real part to the first power).
     */
    fun scalar(
        e: Expr,
        values: Map<String, Double>,
        extra: (Sym) -> Complex? = { null },
        dots: (Dot) -> Double? = { null },
        breitWigner: Boolean = false,
    ): Complex? {
        var total = Complex.ZERO
        for ((k, c) in e.terms) {
            var v = c.toComplex()
            for ((a, n) in k.mono.factors) {
                if (a is Den && breitWigner && n < 0) {
                    val d = scalar(a.content, values, extra, dots, false) ?: return null
                    val m = widthMass(a)
                    val g = m?.let { widths[it.name] }
                    val mv = m?.let { value(it, values) }
                    if (g != null && mv != null) {
                        val mg = mv * g
                        val abs2 = d.re * d.re + mg * mg
                        // (1/D)(1/D*) for even powers; Re(1/D) for the odd one left over.
                        var f = Complex(Math.pow(abs2, (n / 2).toDouble()))
                        if (n % 2 != 0) f *= Complex(d.re / abs2)
                        v *= f
                        continue
                    }
                }
                val base: Complex = when (a) {
                    is Sym -> extra(a) ?: value(a, values)?.let { Complex(it) } ?: return null
                    is Den -> scalar(a.content, values, extra, dots, false) ?: return null
                    is Dot -> dots(a)?.let { Complex(it) } ?: return null
                    Dim -> Complex(4.0)
                    else -> return null
                }
                v *= base.pow(n)
            }
            total += v
        }
        return total
    }

    /** The mass of the unstable particle in a propagator denominator (m² is its mass term). */
    private fun widthMass(d: Den): Sym? {
        val m2 = d.m2 ?: return null
        val syms = m2.atoms().filterIsInstance<Sym>().filter { it.name in widths }
        return syms.singleOrNull()
    }

    fun loop(r: LoopResult, values: Map<String, Double>, mu: Double, open: (Int) -> String, close: (Int) -> String): List<Row>? {
        val rows = LinkedHashMap<TermKey, Pair<Complex, Complex>>()
        for (piece in r.pieces) {
            val poleParts = split(piece.pole)
            val finParts = split(piece.finiteIntegrand)
            for (key in (poleParts.keys + finParts.keys).distinct()) {
                val pole = scalar(scalarOf(poleParts[key]), values) ?: return null
                val fin = finite(piece, scalarOf(finParts[key]), values, mu) ?: return null
                val (p0, f0) = rows[key] ?: (Complex.ZERO to Complex.ZERO)
                rows[key] = (p0 + pole) to (f0 + fin)
            }
        }
        return rows.map { (key, v) ->
            val structure = Expr(mapOf(key to CQ.ONE))
            val tex = if (key.mono.factors.isEmpty() && key.chains.all { it.isEmpty() }) "1" else Tex.of(structure, IndexNames(), open, close)
            Row(tex, v.first, v.second)
        }
    }

    /** ∫dF of one structure's finite integrand: closed form for up to two denominators, quadrature otherwise. */
    private fun finite(piece: LoopPiece, integrand: Expr, values: Map<String, Double>, mu: Double): Complex? {
        val free = piece.free
        if (integrand.isZero) return Complex.ZERO
        closedForm(piece, integrand, values, mu)?.let { return it }
        val scale = maxOf(1e-12, values.values.maxOfOrNull { abs(it) } ?: 1.0)
        var failed = false
        val v = Quadrature.simplex(free.size, points = if (free.size <= 1) 48 else 20, panels = if (free.size <= 1) 6 else 3) { x ->
            val vals = values + free.withIndex().associate { (i, s) -> s.name to x[i] }
            val delta = scalar(piece.delta, vals)
            if (delta == null) { failed = true; return@simplex Complex.ZERO }
            // Δ − i0: below threshold Δ > 0; above it the log picks up −iπ.
            val d = Complex(delta.re, delta.im - 1e-12 * scale * scale)
            scalar(integrand, vals, { s ->
                when (s) {
                    Loop.deltaSym -> d
                    Loop.lnDelta -> (d / Complex(mu * mu)).ln()
                    else -> null
                }
            }) ?: run { failed = true; Complex.ZERO }
        }
        return if (failed) null else v
    }

    /**
     * Two-point (and one-point) integrals exactly: the integrand is Σ c_j x^j (ln Δ/μ²)^{0 or 1}
     * with Δ quadratic in x, and ∫₀¹ x^j ln(Δ − i0) dx is known in closed form.
     */
    private fun closedForm(piece: LoopPiece, integrand: Expr, values: Map<String, Double>, mu: Double): Complex? {
        if (piece.n > 2) return null
        if (integrand.terms.keys.any { it.mono.power(Loop.deltaSym) != 0 }) return null
        if (piece.n == 1) {
            val d = scalar(piece.delta, values) ?: return null
            return scalar(integrand, values, { s -> if (s == Loop.lnDelta) Complex(d.re, -1e-30).let { (it / Complex(mu * mu)).ln() } else null })
        }
        val x = piece.xs[0]
        // Δ(x) = a x² + b x + c from three values.
        val d0 = scalar(piece.delta, values + (x.name to 0.0))?.re ?: return null
        val dh = scalar(piece.delta, values + (x.name to 0.5))?.re ?: return null
        val d1 = scalar(piece.delta, values + (x.name to 1.0))?.re ?: return null
        val a = 2 * (d1 + d0 - 2 * dh)
        val b = d1 - d0 - a
        val cc = d0
        var total = Complex.ZERO
        for ((k, coef) in integrand.terms) {
            val j = k.mono.power(x)
            val logs = k.mono.power(Loop.lnDelta)
            if (j < 0 || logs !in 0..1) return null
            val rest = Expr(mapOf(TermKey(k.mono.without(x).without(Loop.lnDelta), k.chains) to coef))
            val c = scalar(rest, values) ?: return null
            val integral = if (logs == 0) Complex(1.0 / (j + 1)) else Passarino.intXkLnQ(j, a, b, cc) - Complex(ln(mu * mu) / (j + 1))
            total += c * integral
        }
        return total
    }

    /**
     * The finite parts of each integral's scalar function (A₀, B₀, C₀ or D₀, the 1/ε̄ left out):
     * A₀ and B₀ in closed form, C₀ with its inner integral in closed form, D₀ numerically.
     */
    fun scalarIntegrals(r: LoopResult, values: Map<String, Double>, mu: Double): List<Pair<String, Complex>> = r.pieces.mapNotNull { piece ->
        val free = piece.free
        fun delta(x: DoubleArray): Double? = scalar(piece.delta, values + free.withIndex().associate { (i, s) -> s.name to x[i] })?.re
        val v: Complex? = when (piece.n) {
            1 -> delta(DoubleArray(0))?.let { Passarino.a0(it, mu * mu) }
            2 -> {
                val d0 = delta(doubleArrayOf(0.0)); val dh = delta(doubleArrayOf(0.5)); val d1 = delta(doubleArrayOf(1.0))
                if (d0 == null || dh == null || d1 == null) null else {
                    val a = 2 * (d1 + d0 - 2 * dh); val b = d1 - d0 - a
                    -(Passarino.intXkLnQ(0, a, b, d0) - Complex(ln(mu * mu)))
                }
            }
            3 -> runCatching { Passarino.c0 { x1, x2 -> delta(doubleArrayOf(x1, x2)) ?: error("no value") } }.getOrNull()
            4 -> runCatching {
                val scale = maxOf(1.0, values.values.maxOf { abs(it) })
                Passarino.d0({ x -> delta(x) ?: error("no value") }, scale * scale)
            }.getOrNull()
            else -> null
        }
        v?.let { piece.scalarName to it }
    }

    private fun scalarOf(parts: List<Pair<CQ, List<Pair<Atom, Int>>>>?): Expr =
        sum((parts ?: emptyList()).map { (c, f) -> Expr(mapOf(TermKey(Mono.of(f), emptyList()) to c)) })

    /** 1/(16π²), the loop factor taken out of the results. */
    val loopFactor = 1 / (16 * PI * PI)
}
