package com.example.feynman.physics

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** A complex double, for numerical evaluation of loop integrals and color sums. */
data class Complex(val re: Double, val im: Double = 0.0) {
    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun times(d: Double) = Complex(re * d, im * d)
    operator fun div(o: Complex): Complex {
        val n = o.re * o.re + o.im * o.im
        return Complex((re * o.re + im * o.im) / n, (im * o.re - re * o.im) / n)
    }
    operator fun unaryMinus() = Complex(-re, -im)
    fun conj() = Complex(re, -im)
    val abs get() = hypot(re, im)
    val arg get() = atan2(im, re)
    /** The principal logarithm. */
    fun ln() = Complex(ln(abs), arg)
    fun pow(n: Int): Complex {
        var r = ONE
        var b = if (n >= 0) this else ONE / this
        var k = abs(n)
        while (k > 0) { if (k and 1 == 1) r *= b; b *= b; k = k shr 1 }
        return r
    }
    fun sqrt(): Complex {
        val m = sqrt(abs)
        val a = arg / 2
        return Complex(m * cos(a), m * sin(a))
    }
    fun exp(): Complex = Complex(exp(re) * cos(im), exp(re) * sin(im))
    val isFinite get() = re.isFinite() && im.isFinite()

    companion object {
        val ZERO = Complex(0.0)
        val ONE = Complex(1.0)
        val I = Complex(0.0, 1.0)
    }
}

object Numbers {
    /**
     * The simplest fraction within [tol] of [x] (denominators up to [maxDen]), or null.
     * Color factors come out of numerical sums, and they're always simple fractions.
     */
    fun rationalize(x: Double, maxDen: Long = 2000, tol: Double = 1e-9): Rational? {
        if (!x.isFinite()) return null
        if (abs(x) < tol) return Rational.ZERO
        var h0 = 0L; var h1 = 1L; var k0 = 1L; var k1 = 0L
        var v = x
        repeat(40) {
            val a = kotlin.math.floor(v).toLong()
            val h2 = a * h1 + h0
            val k2 = a * k1 + k0
            if (k2 > maxDen) return null
            if (abs(h2.toDouble() / k2 - x) < tol * maxOf(1.0, abs(x))) return Rational.of(h2, k2)
            h0 = h1; h1 = h2; k0 = k1; k1 = k2
            val f = v - a
            if (abs(f) < 1e-15) return null
            v = 1 / f
        }
        return null
    }

    /** A number for people: up to 6 significant digits, scientific notation when needed. */
    fun format(x: Double): String {
        if (x == 0.0) return "0"
        if (!x.isFinite()) return if (x.isNaN()) "undefined" else if (x > 0) "∞" else "−∞"
        val a = abs(x)
        val s = if (a >= 1e-4 && a < 1e7) {
            val digits = (5 - kotlin.math.floor(kotlin.math.log10(a)).toInt()).coerceIn(0, 10)
            "%.${digits}f".format(java.util.Locale.ROOT, x).let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }
        } else {
            val e = kotlin.math.floor(kotlin.math.log10(a)).toInt()
            val m = x / Math.pow(10.0, e.toDouble())
            val ms = "%.5f".format(java.util.Locale.ROOT, m).trimEnd('0').trimEnd('.')
            "$ms × 10^$e"
        }
        return s.replace("-", "−")
    }

    fun formatComplex(z: Complex): String {
        val scale = maxOf(abs(z.re), abs(z.im))
        val re = if (abs(z.re) < 1e-12 * scale) 0.0 else z.re
        val im = if (abs(z.im) < 1e-12 * scale) 0.0 else z.im
        return when {
            im == 0.0 -> format(re)
            re == 0.0 -> format(im) + " i"
            im < 0 -> format(re) + " − " + format(-im) + " i"
            else -> format(re) + " + " + format(im) + " i"
        }
    }

    /** As LaTeX: 1.2345 \times 10^{-3}. */
    fun texOf(x: Double): String {
        val s = format(x)
        val m = Regex("(.*) × 10\\^(−?\\d+)").matchEntire(s)
        return (if (m != null) m.groupValues[1] + " \\times 10^{" + m.groupValues[2].replace("−", "-") + "}" else s).replace("−", "-")
    }

    fun texOfComplex(z: Complex): String {
        val scale = maxOf(abs(z.re), abs(z.im))
        val re = if (abs(z.re) < 1e-12 * scale) 0.0 else z.re
        val im = if (abs(z.im) < 1e-12 * scale) 0.0 else z.im
        return when {
            im == 0.0 -> texOf(re)
            re == 0.0 -> texOf(im) + "\\,i"
            im < 0 -> texOf(re) + " - " + texOf(-im) + "\\,i"
            else -> texOf(re) + " + " + texOf(im) + "\\,i"
        }
    }
}

/** Gauss–Legendre nodes and weights on [0, 1]. */
object Quadrature {
    private val cache = HashMap<Int, Pair<DoubleArray, DoubleArray>>()

    fun gauss(n: Int): Pair<DoubleArray, DoubleArray> = cache.getOrPut(n) {
        val x = DoubleArray(n)
        val w = DoubleArray(n)
        for (i in 0 until n) {
            // Newton's method on the Legendre polynomial, from Chebyshev's guess.
            var z = cos(Math.PI * (i + 0.75) / (n + 0.5))
            var pp = 0.0
            for (iteration in 0 until 100) {
                var p1 = 1.0
                var p2 = 0.0
                for (j in 1..n) { val p3 = p2; p2 = p1; p1 = ((2 * j - 1) * z * p2 - (j - 1) * p3) / j }
                pp = n * (z * p1 - p2) / (z * z - 1)
                val z1 = z
                z = z1 - p1 / pp
                if (abs(z - z1) < 1e-15) break
            }
            x[i] = (1 - z) / 2
            w[i] = 1 / ((1 - z * z) * pp * pp)
        }
        x to w
    }

    /**
     * ∫ over the simplex x₁ + … + x_n ≤ 1 of [f] (a function of the n free parameters), by
     * Gauss–Legendre on the unit cube mapped onto the simplex (x₁ = u₁, x₂ = (1 − x₁)u₂, …),
     * with the interval split into [panels] pieces in each direction for rough integrands.
     */
    fun simplex(n: Int, points: Int = 24, panels: Int = 4, f: (DoubleArray) -> Complex): Complex {
        if (n == 0) return f(DoubleArray(0))
        val (gx, gw) = gauss(points)
        val nodes = DoubleArray(points * panels)
        val weights = DoubleArray(points * panels)
        for (p in 0 until panels) for (i in 0 until points) {
            nodes[p * points + i] = (p + gx[i]) / panels
            weights[p * points + i] = gw[i] / panels
        }
        var total = Complex.ZERO
        val x = DoubleArray(n)
        fun rec(level: Int, remaining: Double, jac: Double) {
            if (level == n) { total += f(x) * jac; return }
            for (i in nodes.indices) {
                x[level] = remaining * nodes[i]
                rec(level + 1, remaining - x[level], jac * remaining * weights[i])
            }
        }
        rec(0, 1.0, 1.0)
        return total
    }
}
