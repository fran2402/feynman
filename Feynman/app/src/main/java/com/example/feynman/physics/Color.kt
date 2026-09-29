package com.example.feynman.physics

/*
 * SU(3) color factors, summed numerically with the Gell-Mann matrices (T^a = λ^a/2 and
 * f^{abc} from [T^a, T^b] = i f^{abc} T^c) and turned back into fractions: every color
 * factor of a diagram is a simple fraction like 4/3 or 9/2.
 */
object Color {
    private val lambda: Array<Array<Array<Complex>>> by lazy {
        val z = Complex.ZERO
        val o = Complex.ONE
        val i = Complex.I
        val r3 = Complex(1 / kotlin.math.sqrt(3.0))
        fun m(vararg e: Complex) = arrayOf(arrayOf(e[0], e[1], e[2]), arrayOf(e[3], e[4], e[5]), arrayOf(e[6], e[7], e[8]))
        arrayOf(
            m(z, o, z, o, z, z, z, z, z),
            m(z, -i, z, i, z, z, z, z, z),
            m(o, z, z, z, -o, z, z, z, z),
            m(z, z, o, z, z, z, o, z, z),
            m(z, z, -i, z, z, z, i, z, z),
            m(z, z, z, z, z, o, z, o, z),
            m(z, z, z, z, z, -i, z, i, z),
            m(r3, z, z, z, r3, z, z, z, r3 * -2.0),
        )
    }

    /** (T^a)_{ij}. */
    fun t(a: Int, i: Int, j: Int): Complex = lambda[a][i][j] * 0.5

    /** f^{abc}, real and totally antisymmetric. */
    val f: Array<Array<DoubleArray>> by lazy {
        Array(8) { a -> Array(8) { b -> DoubleArray(8) { c ->
            // f^{abc} = −2i tr([T^a, T^b] T^c)
            var tr = Complex.ZERO
            for (x in 0 until 3) for (y in 0 until 3) for (w in 0 until 3) {
                val comm = t(a, x, y) * t(b, y, w) - t(b, x, y) * t(a, y, w)
                tr += comm * t(c, w, x)
            }
            (tr * Complex(0.0, -2.0)).re
        } } }
    }

    private fun value(factor: ColorFactor, v: Map<CIdx, Int>): Complex = when (factor) {
        is ColorFactor.T -> t(v[factor.a]!!, v[factor.i]!!, v[factor.j]!!)
        is ColorFactor.F -> Complex(f[v[factor.a]!!][v[factor.b]!!][v[factor.c]!!])
    }

    private fun indicesOf(f: ColorFactor) = when (f) {
        is ColorFactor.T -> listOf(f.a, f.i, f.j)
        is ColorFactor.F -> listOf(f.a, f.b, f.c)
    }

    private fun range(i: CIdx) = if (i.adjoint) 8 else 3

    /**
     * The product of [factors] summed over every index not in [externals], for each value of
     * the external indices (listed in that order, the first varying slowest).
     */
    fun tensor(factors: List<ColorFactor>, externals: List<CIdx>): Array<Complex> {
        val size = externals.fold(1) { acc, i -> acc * range(i) }
        val out = Array(size) { Complex.ZERO }
        val internal = factors.flatMap { indicesOf(it) }.distinct().filter { it !in externals }
        val v = HashMap<CIdx, Int>()
        fun sumInternal(k: Int): Complex {
            if (k == internal.size) {
                var p = Complex.ONE
                for (f in factors) { p *= value(f, v); if (p.re == 0.0 && p.im == 0.0) return p }
                return p
            }
            var s = Complex.ZERO
            for (x in 0 until range(internal[k])) { v[internal[k]] = x; s += sumInternal(k + 1) }
            return s
        }
        fun rec(k: Int, flat: Int) {
            if (k == externals.size) { out[flat] = sumInternal(0); return }
            for (x in 0 until range(externals[k])) { v[externals[k]] = x; rec(k + 1, flat * range(externals[k]) + x) }
        }
        rec(0, 0)
        return out
    }

    /** Σ over all external colors of A·B*, for two tensors over the same external indices. */
    fun contract(a: Array<Complex>, b: Array<Complex>): Complex {
        var s = Complex.ZERO
        for (i in a.indices) s += a[i] * b[i].conj()
        return s
    }

    /** A color factor as a fraction when it is one (it always should be), else rounded. */
    fun exact(z: Complex): CQ {
        val re = Numbers.rationalize(z.re) ?: Rational.parseDecimal("%.10f".format(java.util.Locale.ROOT, z.re))
        val im = Numbers.rationalize(z.im) ?: Rational.parseDecimal("%.10f".format(java.util.Locale.ROOT, z.im))
        return CQ(re, im)
    }

    /**
     * Writes a tensor over the external indices as a multiple of a standard structure when
     * possible: 1, δ_{ij}, δ^{ab}, (T^a)_{ij} or f^{abc}. Returns the LaTeX, or null.
     */
    fun describe(tensor: Array<Complex>, externals: List<CIdx>): String? {
        if (tensor.all { it.abs < 1e-12 }) return "0"
        val basis: Pair<String, Array<Complex>>? = when {
            externals.isEmpty() -> "" to arrayOf(Complex.ONE)
            externals.size == 2 && externals.all { !it.adjoint } -> "\\delta_{${externals[0].hint}${externals[1].hint}}" to
                Array(9) { k -> if (k / 3 == k % 3) Complex.ONE else Complex.ZERO }
            externals.size == 2 && externals.all { it.adjoint } -> "\\delta^{${externals[0].hint}${externals[1].hint}}" to
                Array(64) { k -> if (k / 8 == k % 8) Complex.ONE else Complex.ZERO }
            externals.size == 3 && externals.count { it.adjoint } == 1 -> {
                val a = externals.first { it.adjoint }
                val (i, j) = externals.filter { !it.adjoint }
                "T^{${a.hint}}_{${i.hint}${j.hint}}" to tensor(listOf(ColorFactor.T(a, i, j)), externals)
            }
            externals.size == 3 && externals.all { it.adjoint } -> "f^{${externals[0].hint}${externals[1].hint}${externals[2].hint}}" to
                tensor(listOf(ColorFactor.F(externals[0], externals[1], externals[2])), externals)
            else -> null
        }
        basis ?: return null
        val (tex, b) = basis
        // Least squares: c = ⟨b, t⟩/⟨b, b⟩, then check.
        var num = Complex.ZERO
        var den = 0.0
        for (k in b.indices) { num += b[k].conj() * tensor[k]; den += b[k].abs * b[k].abs }
        val c = num * (1 / den)
        for (k in b.indices) if ((tensor[k] - b[k] * c).abs > 1e-9) return null
        val cq = exact(c)
        val coef = Tex.of(Expr.const(cq))
        return when {
            tex.isEmpty() -> coef
            coef == "1" -> tex
            coef == "-1" -> "-$tex"
            else -> "${if (Tex.needsParen(coef)) "\\left($coef\\right)" else coef}\\,$tex"
        }
    }
}
