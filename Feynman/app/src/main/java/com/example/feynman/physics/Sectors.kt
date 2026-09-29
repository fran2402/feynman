package com.example.feynman.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Numbers for multi-loop Feynman-parameter integrals
 *
 *     ∫_{x ≥ 0} dx δ(1 − Σx) P(x) U(x)^{a_U} F(x)^{a_F},   a = a₀ + a₁ε,
 *
 * by sector decomposition (Binoth & Heinrich, Nucl. Phys. B 585 (2000) 741; Heinrich, Int. J.
 * Mod. Phys. A 23 (2008) 1457): the simplex is cut into cubes where one x is largest, then each
 * cube again until U and F are a monomial times a polynomial that doesn't vanish at the origin.
 * The singular powers t^{-1-n+bε} are subtracted (Taylor expansion to order n) and integrated
 * analytically, which gives the poles in ε; what's left is finite and integrated by
 * randomized quasi-Monte Carlo. F − i0 is used when F < 0; if F changes sign inside the region
 * (above a threshold) the numbers would need a contour deformation, which isn't done here.
 */

/** A truncated Laurent series Σ c_k ε^k, k = low … low + size − 1. */
class Series(val low: Int, val re: DoubleArray, val im: DoubleArray) {
    val high get() = low + re.size - 1
    operator fun get(k: Int): Complex = if (k < low || k > high) Complex.ZERO else Complex(re[k - low], im[k - low])

    fun times(o: Series, top: Int): Series {
        val lo = low + o.low
        val hi = min(top, high + o.high)
        if (hi < lo) return zero(lo)
        val r = DoubleArray(hi - lo + 1); val i = DoubleArray(hi - lo + 1)
        for (a in re.indices) {
            val ka = low + a
            if (ka + o.low > hi) break
            val ar = re[a]; val ai = im[a]
            if (ar == 0.0 && ai == 0.0) continue
            for (b in o.re.indices) {
                val k = ka + o.low + b
                if (k > hi) break
                val br = o.re[b]; val bi = o.im[b]
                r[k - lo] += ar * br - ai * bi
                i[k - lo] += ar * bi + ai * br
            }
        }
        return Series(lo, r, i)
    }

    operator fun plus(o: Series): Series {
        val lo = min(low, o.low); val hi = max(high, o.high)
        val r = DoubleArray(hi - lo + 1); val i = DoubleArray(hi - lo + 1)
        for (k in re.indices) { r[low + k - lo] += re[k]; i[low + k - lo] += im[k] }
        for (k in o.re.indices) { r[o.low + k - lo] += o.re[k]; i[o.low + k - lo] += o.im[k] }
        return Series(lo, r, i)
    }

    fun scale(c: Complex) = Series(low, DoubleArray(re.size) { re[it] * c.re - im[it] * c.im }, DoubleArray(re.size) { re[it] * c.im + im[it] * c.re })
    fun scale(d: Double) = Series(low, DoubleArray(re.size) { re[it] * d }, DoubleArray(re.size) { im[it] * d })
    /** 1/this, for a series whose lowest coefficient isn't zero. */
    fun inverse(top: Int): Series {
        val a0 = this[low]
        val n = max(1, top + low + 1)
        val out = Array(n) { Complex.ZERO }
        val inv0 = Complex.ONE / a0
        out[0] = inv0
        for (k in 1 until n) {
            var s = Complex.ZERO
            for (j in 1..k) s += this[low + j] * out[k - j]
            out[k] = -(s * inv0)
        }
        return Series(-low, DoubleArray(n) { out[it].re }, DoubleArray(n) { out[it].im })
    }

    fun truncate(top: Int) = if (high <= top) this else Series(low, re.copyOf(max(0, top - low + 1)), im.copyOf(max(0, top - low + 1)))

    companion object {
        fun zero(low: Int = 0) = Series(low, DoubleArray(0), DoubleArray(0))
        fun const(c: Complex) = Series(0, doubleArrayOf(c.re), doubleArrayOf(c.im))
        fun const(d: Double) = Series(0, doubleArrayOf(d), doubleArrayOf(0.0))
        /** a₀ + a₁ε. */
        fun linear(a0: Double, a1: Double) = Series(0, doubleArrayOf(a0, a1), doubleArrayOf(0.0, 0.0))

        /** e^{c ε} up to ε^top. */
        fun expOf(c: Complex, top: Int): Series {
            val n = max(1, top + 1)
            val r = DoubleArray(n); val i = DoubleArray(n)
            var term = Complex.ONE
            for (k in 0 until n) {
                r[k] = term.re; i[k] = term.im
                term = term * c * (1.0 / (k + 1))
            }
            return Series(0, r, i)
        }

        /** 1/(a₀ + a₁ε): a pole 1/(a₁ε) when a₀ = 0, else a geometric series. */
        fun inverseLinear(a0: Double, a1: Double, top: Int): Series {
            if (abs(a0) < 1e-12) return Series(-1, doubleArrayOf(1 / a1), doubleArrayOf(0.0))
            val n = max(1, top + 1)
            val r = DoubleArray(n) { k -> (1 / a0) * (-a1 / a0).pow(k) }
            return Series(0, r, DoubleArray(n))
        }

        private val zeta = doubleArrayOf(0.0, 0.0, 1.6449340668482264, 1.2020569031595942, 1.0823232337111382, 1.0369277551433699,
            1.0173430619844491, 1.0083492773819228, 1.0040773561979443, 1.0020083928260822, 1.0009945751278181, 1.0004941886041195, 1.0002460865533080)
        private const val EULER = 0.5772156649015329

        /** Γ(n + cε) as a Laurent series, for an integer n. */
        fun gamma(n: Int, c: Double, top: Int): Series {
            // ln Γ(1 + x) = −γx + Σ_{k≥2} (−1)^k ζ(k) x^k/k, with x = cε.
            val m = max(1, top + 3)
            val lg = DoubleArray(m)
            if (m > 1) lg[1] = -EULER * c
            for (k in 2 until m) lg[k] = (if (k % 2 == 0) 1.0 else -1.0) * zeta.getOrElse(k) { 1.0 } * c.pow(k) / k
            var g1 = expSeries(lg, top + 2)
            var out = g1
            if (n >= 1) for (j in 1 until n) out = out.times(linear(j.toDouble(), c), top + 2)
            else for (j in n..0) out = out.times(inverseLinear(j.toDouble(), c, top + 2), top + 2)
            return out.truncate(top)
        }

        /** exp of a series with no constant term (coefficients by power, from ε⁰). */
        private fun expSeries(a: DoubleArray, top: Int): Series {
            val n = max(1, top + 1)
            val out = DoubleArray(n); out[0] = 1.0
            // f' = a' f
            for (k in 1 until n) {
                var s = 0.0
                for (j in 1..k) s += j * a.getOrElse(j) { 0.0 } * out[k - j]
                out[k] = s / k
            }
            return Series(0, out, DoubleArray(n))
        }
    }
}

/** A polynomial in n variables with complex coefficients. */
class Poly(val n: Int, val exps: List<IntArray>, val cre: DoubleArray, val cim: DoubleArray) {
    val size get() = exps.size
    val isZero get() = exps.isEmpty()
    val maxDeg: IntArray by lazy { IntArray(n) { v -> exps.maxOfOrNull { it[v] } ?: 0 } }

    fun eval(t: DoubleArray): Complex {
        var sr = 0.0; var si = 0.0
        for ((m, e) in exps.withIndex()) {
            var p = 1.0
            for (v in 0 until n) { val k = e[v]; if (k != 0) p *= if (k == 1) t[v] else t[v].pow(k) }
            sr += cre[m] * p; si += cim[m] * p
        }
        return Complex(sr, si)
    }

    /** The common monomial taken out, and what's left. */
    fun factor(): Pair<IntArray, Poly> {
        if (isZero) return IntArray(n) to this
        val m = IntArray(n) { v -> exps.minOf { it[v] } }
        if (m.all { it == 0 }) return m to this
        return m to Poly(n, exps.map { e -> IntArray(n) { e[it] - m[it] } }, cre, cim)
    }

    val hasConstant get() = exps.any { e -> e.all { it == 0 } }

    /** t_i → t_k t_i for i in [set] other than k. */
    fun split(set: List<Int>, k: Int) = Poly(n, exps.map { e -> splitExp(e, set, k) }, cre, cim).merged()

    fun derivative(v: Int): Poly {
        val es = ArrayList<IntArray>(); val r = ArrayList<Double>(); val i = ArrayList<Double>()
        for ((m, e) in exps.withIndex()) if (e[v] > 0) {
            es.add(e.copyOf().also { it[v]-- }); r.add(cre[m] * e[v]); i.add(cim[m] * e[v])
        }
        return Poly(n, es, r.toDoubleArray(), i.toDoubleArray())
    }

    /** t_v = 0. */
    fun atZero(v: Int): Poly {
        val keep = exps.indices.filter { exps[it][v] == 0 }
        return Poly(n, keep.map { exps[it] }, DoubleArray(keep.size) { cre[keep[it]] }, DoubleArray(keep.size) { cim[keep[it]] })
    }

    operator fun times(o: Poly): Poly {
        val es = ArrayList<IntArray>(); val r = ArrayList<Double>(); val i = ArrayList<Double>()
        for ((a, ea) in exps.withIndex()) for ((b, eb) in o.exps.withIndex()) {
            es.add(IntArray(n) { ea[it] + eb[it] })
            r.add(cre[a] * o.cre[b] - cim[a] * o.cim[b]); i.add(cre[a] * o.cim[b] + cim[a] * o.cre[b])
        }
        return Poly(n, es, r.toDoubleArray(), i.toDoubleArray()).merged()
    }

    /** Like terms added, zeros dropped. */
    fun merged(): Poly {
        val map = LinkedHashMap<List<Int>, Complex>()
        for ((m, e) in exps.withIndex()) {
            val key = e.toList()
            map[key] = (map[key] ?: Complex.ZERO) + Complex(cre[m], cim[m])
        }
        val scale = map.values.maxOfOrNull { it.abs } ?: 0.0
        val kept = map.entries.filter { it.value.abs > 1e-14 * scale }
        return Poly(n, kept.map { it.key.toIntArray() }, DoubleArray(kept.size) { kept[it].value.re }, DoubleArray(kept.size) { kept[it].value.im })
    }

    /** The smallest set of variables that, set to zero, makes this vanish. */
    fun hittingSet(): List<Int> {
        val vars = (0 until n).filter { v -> exps.any { it[v] > 0 } }
        for (size in 1..vars.size) {
            var found: List<Int>? = null
            fun rec(start: Int, chosen: List<Int>) {
                if (found != null) return
                if (chosen.size == size) {
                    if (exps.all { e -> chosen.any { e[it] > 0 } }) found = chosen
                    return
                }
                for (j in start until vars.size) rec(j + 1, chosen + vars[j])
            }
            rec(0, emptyList())
            found?.let { return it }
        }
        return vars
    }

    companion object {
        fun splitExp(e: IntArray, set: List<Int>, k: Int): IntArray {
            val out = e.copyOf()
            for (i in set) if (i != k) out[k] += e[i]
            return out
        }
        fun one(n: Int) = Poly(n, listOf(IntArray(n)), doubleArrayOf(1.0), doubleArrayOf(0.0))
    }
}

/** A sector: t_orig = monomials of the sector's variables; U and F as monomial × polynomial. */
class Sector(
    /** Row i: the exponents of the new variables in the original t_i. */
    val map: Array<IntArray>,
    val jacobian: IntArray,
    val mU: IntArray, val u: Poly,
    val mF: IntArray, val f: Poly,
)

object Sectors {
    /** Iterated decomposition of the cube [0,1]ⁿ for U and F; null if it doesn't terminate. */
    fun decompose(u: Poly, f: Poly, maxDepth: Int = 24, maxSectors: Int = 4000): List<Sector>? {
        val n = u.n
        val out = ArrayList<Sector>()
        var failed = false
        fun rec(map: Array<IntArray>, jac: IntArray, pu: Poly, pf: Poly, depth: Int) {
            if (failed) return
            val (mU, ru) = pu.factor()
            val (mF, rf) = if (pf.isZero) IntArray(n) to pf else pf.factor()
            val bad = when {
                !ru.hasConstant -> ru
                !rf.isZero && !rf.hasConstant -> rf
                else -> null
            }
            if (bad == null) { out.add(Sector(map, jac, mU, ru, mF, rf)); return }
            if (depth >= maxDepth || out.size > maxSectors) { failed = true; return }
            val set = bad.hittingSet()
            for (k in set) {
                val m2 = Array(n) { Poly.splitExp(map[it], set, k) }
                val j2 = Poly.splitExp(jac, set, k).also { it[k] += set.size - 1 }
                rec(m2, j2, pu.split(set, k), pf.split(set, k), depth + 1)
            }
        }
        rec(Array(n) { i -> IntArray(n).also { it[i] = 1 } }, IntArray(n), u, f, 0)
        return if (failed) null else out
    }

    /** A polynomial in the original variables written in a sector's variables. */
    fun inSector(p: Poly, s: Sector): Poly {
        val n = p.n
        return Poly(n, p.exps.map { e -> IntArray(n) { k -> (0 until n).sumOf { i -> e[i] * s.map[i][k] } } }, p.cre, p.cim).merged()
    }

    // --- The regular part and its derivatives ------------------------------------------------

    /** c(ε) q(t) U^{a_U − nU} F^{a_F − nF}. */
    class Term(val coef: Series, val q: Poly, val nU: Int, val nF: Int)

    /** Σ terms, sharing U and F (already with their monomials taken out). */
    class Reg(val u: Poly, val f: Poly?, val aU: Pair<Double, Double>, val aF: Pair<Double, Double>, val terms: List<Term>) {
        fun derivative(v: Int, top: Int): Reg {
            val du = u.derivative(v)
            val df = f?.derivative(v)
            val out = ArrayList<Term>()
            for (t in terms) {
                val dq = t.q.derivative(v)
                if (!dq.isZero) out.add(Term(t.coef, dq, t.nU, t.nF))
                if (!du.isZero) out.add(Term(t.coef.times(Series.linear(aU.first - t.nU, aU.second), top), t.q * du, t.nU + 1, t.nF))
                if (df != null && !df.isZero) out.add(Term(t.coef.times(Series.linear(aF.first - t.nF, aF.second), top), t.q * df, t.nU, t.nF + 1))
            }
            return Reg(u, f, aU, aF, out)
        }

        fun atZero(v: Int) = Reg(u.atZero(v), f?.atZero(v), aU, aF, terms.map { Term(it.coef, it.q.atZero(v), it.nU, it.nF) }.filter { !it.q.isZero })

        fun eval(t: DoubleArray, top: Int): Series {
            if (terms.isEmpty()) return Series.zero()
            val uv = u.eval(t)
            val fv = f?.eval(t)
            val lu = logOf(uv)
            val lf = fv?.let { logOf(it) }
            var sum: Series? = null
            for (term in terms) {
                val qv = term.q.eval(t)
                if (qv.re == 0.0 && qv.im == 0.0) continue
                var s = powSeries(lu, aU.first - term.nU, aU.second, top).scale(qv)
                if (lf != null) s = s.times(powSeries(lf, aF.first - term.nF, aF.second, top), top)
                s = s.times(term.coef, top)
                sum = sum?.plus(s) ?: s
            }
            return sum ?: Series.zero()
        }
    }

    /** ln(z − i0). */
    fun logOf(z: Complex): Complex = if (z.im == 0.0 && z.re < 0) Complex(ln(-z.re), -PI) else z.ln()

    /** z^{a₀ + a₁ε} from ln z. */
    fun powSeries(lz: Complex, a0: Double, a1: Double, top: Int): Series {
        val base = (lz * a0).exp()
        return Series.expOf(lz * a1, top).scale(base)
    }

    // --- Integration --------------------------------------------------------------------------

    class Estimate(val value: Series, val error: DoubleArray)

    /**
     * ∫_{[0,1]ⁿ} Π t_i^{e_i + f_iε} g(t) dt, g = [reg] (regular at the origin), as a Laurent series
     * up to ε^top, by subtraction of the singular powers and quasi-Monte Carlo.
     */
    fun integrate(n: Int, e: IntArray, f: DoubleArray, reg: Reg, top: Int, points: Int, shifts: Int, seed: Long): Estimate {
        val singular = (0 until n).filter { e[it] <= -1 }
        val order = singular.associateWith { -e[it] - 1 } // Taylor order K
        val maxPoles = singular.size
        val inner = top + maxPoles
        // Functions needed: g with some variables zeroed and some derivatives, keyed by (zeroed set, orders).
        val cache = HashMap<String, Reg>()
        fun fn(zero: List<Int>, orders: Map<Int, Int>): Reg {
            val key = zero.sorted().joinToString(",") + "|" + orders.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
            return cache.getOrPut(key) {
                var r = reg
                for ((v, k) in orders) repeat(k) { r = r.derivative(v, inner) }
                for (v in zero) r = r.atZero(v)
                r
            }
        }
        // Configurations: each singular variable either integrated analytically at Taylor order k
        // (factor 1/(k!(a+k+1)), variable at 0 with the k-th derivative) or left as the remainder.
        class Config(val poleOrders: Map<Int, Int>, val rem: List<Int>, val factor: Series)
        val configs = ArrayList<Config>()
        fun build(i: Int, poles: Map<Int, Int>, rem: List<Int>, factor: Series) {
            if (i == singular.size) { configs.add(Config(poles, rem, factor)); return }
            val v = singular[i]
            for (k in 0..order[v]!!) {
                var fk = Series.inverseLinear(e[v] + k + 1.0, f[v], inner)
                var fact = 1.0; for (j in 2..k) fact *= j
                fk = fk.scale(1 / fact)
                build(i + 1, poles + (v to k), rem, factor.times(fk, inner))
            }
            build(i + 1, poles, rem + v, factor)
        }
        build(0, emptyMap(), emptyList(), Series.const(1.0))
        // For each config, the Taylor terms of the remainder: subsets C of rem with orders.
        class Piece(val sign: Double, val c: List<Int>, val orders: Map<Int, Int>, val fn: Reg, val weight: Double)
        val pieces = configs.map { cfg ->
            val list = ArrayList<Piece>()
            fun rec(j: Int, c: List<Int>, ords: Map<Int, Int>, sign: Double, w: Double) {
                if (j == cfg.rem.size) {
                    list.add(Piece(sign, c, ords, fn(cfg.poleOrders.keys.toList() + c, cfg.poleOrders + ords), w))
                    return
                }
                val v = cfg.rem[j]
                rec(j + 1, c, ords, sign, w)
                for (k in 0..order[v]!!) {
                    var fact = 1.0; for (q in 2..k) fact *= q
                    rec(j + 1, c + v, ords + (v to k), -sign, w / fact)
                }
            }
            rec(0, emptyList(), emptyMap(), 1.0, 1.0)
            list
        }
        val dim = n
        val alpha = kronecker(dim)
        val rnd = java.util.Random(seed)
        val estimates = ArrayList<Series>()
        val t = DoubleArray(dim)
        val tz = DoubleArray(dim)
        val wd = DoubleArray(dim)
        repeat(shifts) {
            val shift = DoubleArray(dim) { rnd.nextDouble() }
            var acc: Series = Series.zero()
            for (j in 0 until points) {
                for (d in 0 until dim) {
                    val v = (shift[d] + (j + 1) * alpha[d]) % 1.0
                    // Korobov's cubic transform: the integrand and its slope vanish at the ends.
                    t[d] = v * v * (3 - 2 * v)
                    wd[d] = 6 * v * (1 - v)
                }
                // Regular powers t^{e+fε} for every variable (singular ones' integer parts too).
                val logs = DoubleArray(dim) { ln(max(t[it], 1e-300)) }
                var total: Series? = null
                for ((ci, cfg) in configs.withIndex()) {
                    var bracket: Series? = null
                    for (p in pieces[ci]) {
                        for (d in 0 until dim) tz[d] = t[d]
                        for (v in cfg.poleOrders.keys) tz[v] = 0.0
                        for (v in p.c) tz[v] = 0.0
                        var s = p.fn.eval(tz, inner)
                        var mono = p.weight * p.sign
                        for ((v, k) in p.orders) mono *= t[v].pow(k)
                        s = s.scale(mono)
                        bracket = bracket?.plus(s) ?: s
                    }
                    var term = bracket ?: continue
                    // The monomial for the non-pole variables, and the transform's weight for them
                    // (the pole variables are already integrated: nothing depends on them).
                    var power = 1.0
                    var epsLog = 0.0
                    for (d in 0 until dim) {
                        if (d in cfg.poleOrders) continue
                        power *= t[d].pow(e[d]) * wd[d]
                        epsLog += f[d] * logs[d]
                    }
                    if (power == 0.0) continue
                    term = term.scale(power).times(Series.expOf(Complex(epsLog), inner), inner).times(cfg.factor, top)
                    total = total?.plus(term) ?: term
                }
                if (total != null) acc = acc + total
            }
            estimates.add(acc.scale(1.0 / points))
        }
        val mean = estimates.reduce { a, b -> a + b }.scale(1.0 / shifts)
        val err = DoubleArray(mean.re.size) { k ->
            val order2 = mean.low + k
            val m = mean[order2]
            val v = estimates.sumOf { val d = it[order2] - m; d.re * d.re + d.im * d.im } / max(1, shifts - 1)
            sqrt(v / shifts)
        }
        return Estimate(mean.truncate(top), err)
    }

    /** Roberts' R_d sequence: α_i = φ_d^{−i}, φ_d the positive root of x^{d+1} = x + 1. */
    private fun kronecker(d: Int): DoubleArray {
        var phi = 2.0
        repeat(60) { phi = (1 + phi).pow(1.0 / (d + 1)) }
        return DoubleArray(d) { i -> (1 / phi.pow(i + 1)) % 1.0 }
    }
}
