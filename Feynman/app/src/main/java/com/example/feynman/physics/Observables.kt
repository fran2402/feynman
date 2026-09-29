package com.example.feynman.physics

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

/*
 * Cross sections and decay widths from the spin-averaged |ℳ|² (in dot products), with
 * explicit momenta:
 *   2 → 2:  dσ/dcos θ = |ℳ|² |p_f| / (32π s |p_i|)            (centre-of-mass frame)
 *   1 → 2:  Γ = |p| |ℳ|² / (8π M²)
 *   1 → 3:  Γ = ∫ dm₁₂² dm₂₃² |ℳ|² / ((2π)³ 32 M³)                 (Dalitz plot, PDG eq. 49.22)
 * with a factor 1/n! for n identical particles in the final state.
 */
object Observables {
    /** GeV⁻² in picobarns. */
    const val PB = 0.3893793721e9

    class Curve(val xs: DoubleArray, val ys: DoubleArray, val xLabel: String, val yLabel: String)

    enum class Kind { Scattering, Decay2, Decay3, None }

    fun kind(kin: Kinematics): Kind = when {
        kin.incoming.size == 2 && kin.outgoing.size == 2 -> Kind.Scattering
        kin.incoming.size == 1 && kin.outgoing.size == 2 -> Kind.Decay2
        kin.incoming.size == 1 && kin.outgoing.size == 3 -> Kind.Decay3
        else -> Kind.None
    }

    // Physical masses: a neutralino's may be negative in the mass matrix's convention.
    private fun mass(kin: Kinematics, x: External, values: Map<String, Double>) = kin.mass(x)?.let { values[it.name] }?.let { kotlin.math.abs(it) } ?: 0.0

    /** Källén's λ(a, b, c) = a² + b² + c² − 2ab − 2bc − 2ca. */
    fun kallen(a: Double, b: Double, c: Double) = a * a + b * b + c * c - 2 * a * b - 2 * b * c - 2 * c * a

    /** 1/n! for identical final-state particles. */
    fun symmetry(kin: Kinematics): Double {
        var f = 1.0
        kin.outgoing.groupBy { it.particle.id + it.anti }.values.forEach { g -> for (k in 2..g.size) f /= k }
        return f
    }

    /** |ℳ|² with the dot products of the given momenta (four-vectors by momentum name). */
    private fun msq(sq: SquaredResult, values: Map<String, Double>, p: Map<String, DoubleArray>, bw: Boolean): Double? {
        fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3]
        return Evaluate.scalar(sq.dots, values, dots = { d -> val a = p[d.a]; val b = p[d.b]; if (a != null && b != null) dot(a, b) else null }, breitWigner = bw)?.re
    }

    /** dσ/dcos θ (pb) at √s and cos θ, in the centre-of-mass frame (θ between the first incoming and first outgoing). */
    fun dSigma(sq: SquaredResult, kin: Kinematics, values: Map<String, Double>, sqrtS: Double, cosT: Double, bw: Boolean = true): Double? {
        val (a, b) = kin.incoming
        val (c, d) = kin.outgoing
        val ma = mass(kin, a, values); val mb = mass(kin, b, values); val mc = mass(kin, c, values); val md = mass(kin, d, values)
        val s = sqrtS * sqrtS
        if (sqrtS <= ma + mb || sqrtS <= mc + md) return 0.0
        val pi = sqrt(max(0.0, kallen(s, ma * ma, mb * mb))) / (2 * sqrtS)
        val pf = sqrt(max(0.0, kallen(s, mc * mc, md * md))) / (2 * sqrtS)
        val ea = sqrt(pi * pi + ma * ma); val eb = sqrt(pi * pi + mb * mb)
        val ec = sqrt(pf * pf + mc * mc); val ed = sqrt(pf * pf + md * md)
        val sinT = sqrt(max(0.0, 1 - cosT * cosT))
        val p = mapOf(
            a.momentum to doubleArrayOf(ea, 0.0, 0.0, pi),
            b.momentum to doubleArrayOf(eb, 0.0, 0.0, -pi),
            c.momentum to doubleArrayOf(ec, pf * sinT, 0.0, pf * cosT),
            d.momentum to doubleArrayOf(ed, -pf * sinT, 0.0, -pf * cosT),
        )
        val m2 = msq(sq, values, p, bw) ?: return null
        return m2 * pf / (32 * PI * s * pi) * PB * symmetry(kin)
    }

    /** σ (pb) at √s: ∫ dcos θ over |cos θ| ≤ [cut] (to keep away from the forward pole of t-channel photons). */
    fun sigma(sq: SquaredResult, kin: Kinematics, values: Map<String, Double>, sqrtS: Double, cut: Double = 1.0, bw: Boolean = true): Double? {
        val (gx, gw) = Quadrature.gauss(24)
        var total = 0.0
        val panels = 4
        for (pn in 0 until panels) for (i in gx.indices) {
            val u = (pn + gx[i]) / panels
            val cosT = -cut + 2 * cut * u
            total += (dSigma(sq, kin, values, sqrtS, cosT, bw) ?: return null) * gw[i] / panels * 2 * cut
        }
        return total
    }

    /** Γ (GeV) of a 1 → 2 or 1 → 3 decay. */
    fun width(sq: SquaredResult, kin: Kinematics, values: Map<String, Double>, bw: Boolean = true): Double? {
        val parent = kin.incoming.single()
        val bigM = mass(kin, parent, values)
        val outs = kin.outgoing
        val ms = outs.map { mass(kin, it, values) }
        if (bigM <= ms.sum()) return 0.0
        return when (outs.size) {
            2 -> {
                val p = sqrt(max(0.0, kallen(bigM * bigM, ms[0] * ms[0], ms[1] * ms[1]))) / (2 * bigM)
                val e1 = sqrt(p * p + ms[0] * ms[0]); val e2 = sqrt(p * p + ms[1] * ms[1])
                val mom = mapOf(
                    parent.momentum to doubleArrayOf(bigM, 0.0, 0.0, 0.0),
                    outs[0].momentum to doubleArrayOf(e1, 0.0, 0.0, p),
                    outs[1].momentum to doubleArrayOf(e2, 0.0, 0.0, -p),
                )
                val m2 = msq(sq, values, mom, bw) ?: return null
                p * m2 / (8 * PI * bigM * bigM) * symmetry(kin)
            }
            3 -> dalitz(sq, kin, values, bigM, ms, bw)?.let { it * symmetry(kin) }
            else -> null
        }
    }

    private fun dalitz(sq: SquaredResult, kin: Kinematics, values: Map<String, Double>, bigM: Double, ms: List<Double>, bw: Boolean): Double? {
        val parent = kin.incoming.single()
        val outs = kin.outgoing
        val (m1, m2, m3) = ms
        val lo12 = (m1 + m2) * (m1 + m2); val hi12 = (bigM - m3) * (bigM - m3)
        val (gx, gw) = Quadrature.gauss(40)
        var total = 0.0
        for (i in gx.indices) {
            val s12 = lo12 + (hi12 - lo12) * gx[i]
            val w12 = sqrt(s12)
            val e2 = (s12 - m1 * m1 + m2 * m2) / (2 * w12)
            val e3 = (bigM * bigM - s12 - m3 * m3) / (2 * w12)
            val q2 = sqrt(max(0.0, e2 * e2 - m2 * m2)); val q3 = sqrt(max(0.0, e3 * e3 - m3 * m3))
            val lo23 = (e2 + e3) * (e2 + e3) - (q2 + q3) * (q2 + q3)
            val hi23 = (e2 + e3) * (e2 + e3) - (q2 - q3) * (q2 - q3)
            var inner = 0.0
            for (j in gx.indices) {
                val s23 = lo23 + (hi23 - lo23) * gx[j]
                val s13 = bigM * bigM + m1 * m1 + m2 * m2 + m3 * m3 - s12 - s23
                // Dot products from the invariants.
                val sij = mapOf(setOf(0, 1) to s12, setOf(1, 2) to s23, setOf(0, 2) to s13)
                val mm = listOf(m1, m2, m3)
                val m2v = Evaluate.scalar(sq.dots, values, dots = { d ->
                    val ia = outs.indexOfFirst { it.momentum == d.a }
                    val ib = outs.indexOfFirst { it.momentum == d.b }
                    val pa = d.a == parent.momentum; val pb = d.b == parent.momentum
                    when {
                        pa && pb -> bigM * bigM
                        ia >= 0 && ia == ib -> mm[ia] * mm[ia]
                        ia >= 0 && ib >= 0 -> (sij[setOf(ia, ib)]!! - mm[ia] * mm[ia] - mm[ib] * mm[ib]) / 2
                        pa || pb -> {
                            // P·p_i = (M² + m_i² − s_jk)/2
                            val i = if (pa) ib else ia
                            val others = (0..2).filter { it != i }.toSet()
                            (bigM * bigM + mm[i] * mm[i] - sij[others]!!) / 2
                        }
                        else -> null
                    }
                }, breitWigner = bw)?.re ?: return null
                inner += m2v * gw[j] * (hi23 - lo23)
            }
            total += inner * gw[i] * (hi12 - lo12)
        }
        return total / (Math.pow(2 * PI, 3.0) * 32 * bigM * bigM * bigM)
    }

    /** dσ/dcos θ over −1 … 1. */
    fun angularCurve(sq: SquaredResult, kin: Kinematics, values: Map<String, Double>, sqrtS: Double, bw: Boolean = true): Curve? {
        val n = 81
        val xs = DoubleArray(n) { -0.99 + 1.98 * it / (n - 1) }
        val ys = DoubleArray(n)
        for (i in 0 until n) ys[i] = dSigma(sq, kin, values, sqrtS, xs[i], bw) ?: return null
        return Curve(xs, ys, "\\cos\\theta", "d\\sigma/d\\cos\\theta\\ \\text{(pb)}")
    }

    /** σ against √s from just above threshold to [top]. */
    fun energyCurve(sq: SquaredResult, kin: Kinematics, values: Map<String, Double>, top: Double, cut: Double, bw: Boolean = true): Curve? {
        val thr = max(kin.incoming.sumOf { mass(kin, it, values) }, kin.outgoing.sumOf { mass(kin, it, values) })
        val lo = max(thr * 1.001, top * 0.02)
        val n = 60
        val xs = DoubleArray(n) { lo + (top - lo) * it / (n - 1) }
        val ys = DoubleArray(n)
        for (i in 0 until n) ys[i] = sigma(sq, kin, values, xs[i], cut, bw) ?: return null
        return Curve(xs, ys, "\\sqrt{s}\\ \\text{(GeV)}", "\\sigma\\ \\text{(pb)}")
    }
}
