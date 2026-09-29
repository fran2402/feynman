package com.example.feynman.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Numbers for the symbolic answers: a value for every coupling, mass and invariant, then the
 * UV pole and finite part of a loop amplitude (the Feynman-parameter integral done by
 * Gauss–Legendre quadrature on the simplex) or a squared tree amplitude.
 */
object Evaluate {
    /** Standard Model inputs (GeV), PDG 2024; s, t, u and p² are just starting points. */
    val defaults: Map<String, Double> by lazy {
        val alpha = 1 / 137.035999
        val e = sqrt(4 * PI * alpha)
        val mW = 80.3692; val mZ = 91.1880
        val cW = mW / mZ
        val sW = sqrt(1 - cW * cW)
        mapOf(
            "e" to e, "g" to e / sW, "gs" to sqrt(4 * PI * 0.1180), "cW" to cW, "sW" to sW,
            "mW" to mW, "mZ" to mZ, "mh" to 125.20,
            "m_e" to 0.000510999, "m_mu" to 0.105658, "m_tau" to 1.77693,
            "m_u" to 0.00216, "m_d" to 0.00470, "m_s" to 0.0935, "m_c" to 1.2730, "m_b" to 4.183, "m_t" to 172.57,
            "s" to 200.0 * 200.0, "t" to -5000.0, "u" to -35000.0, "psq" to 50.0,
            "V_ud" to 0.97367, "V_us" to 0.22431, "V_ub" to 0.00382, "V_cd" to 0.221, "V_cs" to 0.975, "V_cb" to 0.0411,
            "V_td" to 0.0086, "V_ts" to 0.0415, "V_tb" to 1.010,
        )
    }

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

    /** A scalar expression's value (null if a symbol has no value). */
    fun scalar(e: Expr, values: Map<String, Double>, extra: (Sym) -> Complex? = { null }): Complex? {
        var total = Complex.ZERO
        for ((k, c) in e.terms) {
            var v = c.toComplex()
            for ((a, n) in k.mono.factors) {
                val base: Complex = when (a) {
                    is Sym -> extra(a) ?: value(a, values)?.let { Complex(it) } ?: return null
                    is Den -> scalar(a.content, values, extra) ?: return null
                    Dim -> Complex(4.0)
                    else -> return null
                }
                v *= base.pow(n)
            }
            total += v
        }
        return total
    }

    fun loop(r: LoopResult, values: Map<String, Double>, mu: Double, open: (Int) -> String, close: (Int) -> String): List<Row>? {
        val free = if (r.n == 1) emptyList() else r.xs.dropLast(1)
        val poleParts = split(r.pole)
        val finParts = split(r.finiteIntegrand)
        val keys = (poleParts.keys + finParts.keys).distinct()
        val rows = ArrayList<Row>()
        val scale = maxOf(1e-12, values.values.maxOfOrNull { abs(it) } ?: 1.0)
        for (key in keys) {
            val pole = scalar(scalarOf(poleParts[key]), values) ?: return null
            val finExpr = scalarOf(finParts[key])
            var failed = false
            val fin = Quadrature.simplex(free.size, points = if (free.size <= 1) 48 else 20, panels = if (free.size <= 1) 6 else 3) { x ->
                val xv = free.withIndex().associate { (i, s) -> s.name to x[i] }
                val vals = values + xv
                val delta = scalar(r.delta, vals)
                if (delta == null) { failed = true; return@simplex Complex.ZERO }
                // Δ − i0: below threshold Δ > 0; above it the log picks up −iπ.
                val d = Complex(delta.re, delta.im - 1e-12 * scale * scale)
                val v = scalar(finExpr, vals) { s ->
                    when (s) {
                        Loop.deltaSym -> d
                        Loop.lnDelta -> (d / Complex(mu * mu)).ln()
                        else -> null
                    }
                }
                if (v == null) { failed = true; Complex.ZERO } else v
            }
            if (failed) return null
            val structure = Expr(mapOf(key to CQ.ONE))
            val tex = if (key.mono.factors.isEmpty() && key.chains.all { it.isEmpty() } && key.chains.isEmpty()) "1" else Tex.of(structure, IndexNames(), open, close)
            rows.add(Row(tex, pole, fin))
        }
        return rows
    }

    private fun scalarOf(parts: List<Pair<CQ, List<Pair<Atom, Int>>>>?): Expr =
        sum((parts ?: emptyList()).map { (c, f) -> Expr(mapOf(TermKey(Mono.of(f), emptyList()) to c)) })

    /** 1/(16π²), the loop factor taken out of the results. */
    val loopFactor = 1 / (16 * PI * PI)
}
