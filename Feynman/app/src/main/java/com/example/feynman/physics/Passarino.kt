package com.example.feynman.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round

/*
 * Scalar one-loop functions (Passarino & Veltman, Nucl. Phys. B 160 (1979) 151), finite parts
 * in the MS-bar scheme with the loop factor i/(16π²) and 1/ε̄ taken out:
 *
 *   A₀(m²)          = m² (1/ε̄ + 1 − ln m²/μ²)
 *   B₀(p²; m₁², m₂²) = 1/ε̄ − ∫₀¹ dx ln[(x m₁² + (1−x) m₂² − x(1−x) p² − i0)/μ²]
 *   C₀              = −∫dF 1/(Δ − i0),      D₀ = ∫dF 1/(Δ − i0)²
 *
 * Every integral ∫₀¹ xᵏ ln(ax² + bx + c − i0) dx is done in closed form (the logarithm split at
 * the roots of the quadratic), so A₀, B₀ and any two-point function come out exactly; C₀ has its
 * inner integral done in closed form, leaving one smooth integral; D₀ is integrated numerically.
 */
object Passarino {
    private fun c(x: Double) = Complex(x)

    /** ∫ xᵏ ln(x − r) dx = (x^{k+1} − r^{k+1}) ln(x − r)/(k+1) − Σⱼ r^{k−j} x^{j+1}/((j+1)(k+1)). */
    private fun antiderivative(k: Int, r: Complex, x: Double): Complex {
        val xr = c(x) - r
        var out = (c(x).pow(k + 1) - r.pow(k + 1)) * xr.ln() * (1.0 / (k + 1))
        for (j in 0..k) out -= r.pow(k - j) * c(Math.pow(x, (j + 1).toDouble()) / ((j + 1) * (k + 1)))
        return out
    }

    /** ∫₀¹ xᵏ ln(a x² + b x + c − i0) dx, exactly. */
    fun intXkLnQ(k: Int, a: Double, b: Double, cc: Double): Complex {
        val scale = max(abs(a), max(abs(b), abs(cc))).coerceAtLeast(1e-300)
        val eta = 1e-14 * scale
        val q = { x: Double -> Complex(a * x * x + b * x + cc, -eta) }
        return when {
            abs(a) > 1e-13 * scale -> {
                // Roots of a x² + b x + c − iη.
                val disc = (c(b * b) - Complex(4 * a * cc, -4 * a * eta)).sqrt()
                val r1 = (c(-b) + disc) * (1 / (2 * a))
                val r2 = (c(-b) - disc) * (1 / (2 * a))
                val la = c(a).ln()
                // ln Q = ln a + ln(x − r₁) + ln(x − r₂) + 2πi n, n fixed by x = ½.
                val g = la + (c(0.5) - r1).ln() + (c(0.5) - r2).ln()
                val n = round((q(0.5).ln() - g).im / (2 * PI))
                val body = antiderivative(k, r1, 1.0) - antiderivative(k, r1, 0.0) + antiderivative(k, r2, 1.0) - antiderivative(k, r2, 0.0)
                body + (la + Complex(0.0, 2 * PI * n)) * (1.0 / (k + 1))
            }
            abs(b) > 1e-13 * scale -> {
                val r = Complex(-cc / b, eta / b)
                val lb = c(b).ln()
                val n = round((q(0.5).ln() - (lb + (c(0.5) - r).ln())).im / (2 * PI))
                antiderivative(k, r, 1.0) - antiderivative(k, r, 0.0) + (lb + Complex(0.0, 2 * PI * n)) * (1.0 / (k + 1))
            }
            else -> q(0.0).ln() * (1.0 / (k + 1))
        }
    }

    /** Finite part of A₀(m²). */
    fun a0(m2: Double, mu2: Double): Complex = if (m2 == 0.0) Complex.ZERO else c(m2 * (1 - ln(m2 / mu2)))

    /** Finite part of B₀(p²; m₁², m₂²), in closed form. */
    fun b0(p2: Double, m1: Double, m2: Double, mu2: Double): Complex {
        // Δ(x) = p² x² + (m₁² − m₂² − p²) x + m₂²
        val lnDelta = intXkLnQ(0, p2, m1 - m2 - p2, m2)
        return -(lnDelta - c(ln(mu2)))
    }

    /**
     * C₀ = −∫dF 1/(Δ − i0) for Δ(x₁, x₂) given as a function; the x₂ integral is done in closed
     * form for each x₁ (Δ is quadratic in x₂), the x₁ integral by Gauss–Legendre.
     */
    fun c0(delta: (Double, Double) -> Double): Complex {
        val (gx, gw) = Quadrature.gauss(32)
        var total = Complex.ZERO
        val panels = 8
        for (p in 0 until panels) for (i in gx.indices) {
            val x1 = (p + gx[i]) / panels
            val w = gw[i] / panels
            val top = 1 - x1
            if (top <= 0) continue
            // Δ(x₁, x₂) = A x₂² + B x₂ + C from three points.
            val d0 = delta(x1, 0.0); val dh = delta(x1, top / 2); val d1 = delta(x1, top)
            // In u = x₂/top ∈ [0, 1]: A' u² + B' u + C'.
            val cc = d0
            val aa = 2 * (d1 + d0 - 2 * dh)
            val bb = d1 - d0 - aa
            total += inverseQuadratic(aa, bb, cc) * (w * top)
        }
        return -total
    }

    /** ∫₀¹ du/(a u² + b u + c − i0), in closed form. */
    fun inverseQuadratic(a: Double, b: Double, cc: Double): Complex {
        val scale = max(abs(a), max(abs(b), abs(cc))).coerceAtLeast(1e-300)
        val eta = 1e-14 * scale
        return when {
            abs(a) > 1e-12 * scale -> {
                val disc = (c(b * b) - Complex(4 * a * cc, -4 * a * eta)).sqrt()
                val r1 = (c(-b) + disc) * (1 / (2 * a))
                val r2 = (c(-b) - disc) * (1 / (2 * a))
                // 1/(a(u−r₁)(u−r₂)) = [1/(u−r₁) − 1/(u−r₂)]/(a(r₁−r₂))
                val f = { u: Double -> (c(u) - r1).ln() - (c(u) - r2).ln() }
                (f(1.0) - f(0.0)) / ((r1 - r2) * a)
            }
            abs(b) > 1e-12 * scale -> ((Complex(b + cc, -eta)).ln() - Complex(cc, -eta).ln()) * (1 / b)
            else -> Complex(1.0) / Complex(cc, -eta)
        }
    }

    /** D₀ = ∫dF 1/(Δ − i0)², numerically over the three Feynman parameters. */
    fun d0(delta: (DoubleArray) -> Double, scale: Double): Complex =
        Quadrature.simplex(3, points = 16, panels = 3) { x -> Complex(1.0) / Complex(delta(x), -1e-6 * scale).pow(2) }

    /** The closed form of B₀ as usually written (Denner, Fortsch. Phys. 41 (1993) 307, eq. (4.23)). */
    const val B0_TEX = "B_0(p^{2}; m_1^{2}, m_2^{2}) = \\frac{1}{\\bar\\epsilon} + 2 - \\ln\\frac{m_1 m_2}{\\mu^{2}} + \\frac{m_1^{2} - m_2^{2}}{p^{2}}\\ln\\frac{m_2}{m_1} - \\frac{m_1 m_2}{p^{2}}\\left(\\frac{1}{r} - r\\right)\\ln r"
    const val B0_R_TEX = "r^{2} + \\frac{m_1^{2} + m_2^{2} - p^{2} - i0}{m_1 m_2}\\, r + 1 = 0"
    const val A0_TEX = "A_0(m^{2}) = m^{2}\\left(\\frac{1}{\\bar\\epsilon} + 1 - \\ln\\frac{m^{2}}{\\mu^{2}}\\right)"
}
