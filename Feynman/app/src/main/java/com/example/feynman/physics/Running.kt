package com.example.feynman.physics

import kotlin.math.PI
import kotlin.math.ln

/*
 * One-loop running couplings, with each fermion switched on at its mass (step thresholds):
 *   QED:  μ dα/dμ = (2α²/3π) Σ_f N_c Q_f²          (from δZ₃ of the vacuum polarization)
 *   QCD:  μ dα_s/dμ = −(β₀/2π) α_s²,  β₀ = 11 − 2n_f/3
 * QED starts from α(m_e) = 1/137.036, QCD from α_s(m_Z) = 0.1180. Light-quark thresholds are
 * only indicative at one loop (hadronic effects need data).
 */
object Running {
    private class Fermion(val mass: Double, val charge: Double, val colors: Int)

    private val fermions = listOf(
        Fermion(0.000510999, 1.0, 1), Fermion(0.105658, 1.0, 1), Fermion(1.77693, 1.0, 1),
        Fermion(0.00216, 2.0 / 3, 3), Fermion(0.00470, 1.0 / 3, 3), Fermion(0.0935, 1.0 / 3, 3),
        Fermion(1.2730, 2.0 / 3, 3), Fermion(4.183, 1.0 / 3, 3), Fermion(172.57, 2.0 / 3, 3),
    )

    /** α(μ) at one loop. */
    fun alpha(mu: Double): Double {
        var inv = 137.035999
        var scale = 0.000510999
        val thresholds = fermions.map { it.mass }.filter { it > scale && it < mu }.sorted() + mu
        for (t in thresholds) {
            if (t <= scale) continue
            val b = fermions.filter { it.mass <= scale * 1.0000001 }.sumOf { it.colors * it.charge * it.charge }
            inv -= 2 / (3 * PI) * b * ln(t / scale)
            scale = t
        }
        return 1 / inv
    }

    /** Active quark flavours at μ. */
    fun flavours(mu: Double) = fermions.count { it.colors == 3 && it.mass < mu }.coerceAtLeast(3)

    /** α_s(μ) at one loop (for μ ≳ 1 GeV). */
    fun alphaS(mu: Double): Double {
        val mZ = 91.1880
        var inv = 1 / 0.1180
        var scale = mZ
        val quarkMasses = listOf(1.2730, 4.183, 172.57)
        val stops = if (mu > mZ) quarkMasses.filter { it in mZ..mu } + mu else quarkMasses.filter { it in mu..mZ }.sortedDescending() + mu
        for (t in stops) {
            // Flavours active between scale and t.
            val mid = kotlin.math.sqrt(scale * t)
            val beta0 = 11 - 2.0 * flavours(mid) / 3
            inv += beta0 / (2 * PI) * ln(t / scale)
            scale = t
        }
        return 1 / inv
    }

    const val QED_TEX = "\\mu\\frac{d\\alpha}{d\\mu} = \\frac{2\\alpha^{2}}{3\\pi}\\sum_{f} N_c Q_f^{2}"
    const val QCD_TEX = "\\mu\\frac{d\\alpha_s}{d\\mu} = -\\frac{\\beta_0}{2\\pi}\\alpha_s^{2},\\quad \\beta_0 = 11 - \\frac{2}{3}n_f"
}
