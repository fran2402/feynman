package com.example.feynman.physics

import java.math.BigInteger

/** Printed names of momenta: p1 → p_1, l → ℓ, eps2 → ε_2, eps2* → ε*_2. */
object MomNames {
    fun tex(name: String): String {
        if (name == "l") return "\\ell"
        if (name.startsWith("eps")) {
            val star = name.endsWith("*")
            val n = name.removePrefix("eps").removeSuffix("*")
            return if (star) "\\varepsilon^{*}_{$n}" else "\\varepsilon_{$n}"
        }
        val m = Regex("([a-zA-Z]+)(\\d+)").matchEntire(name) ?: return name
        return "${m.groupValues[1]}_{${m.groupValues[2]}}"
    }

    /** A combination like p_1 + p_2 − k. */
    fun tex(k: Mom): String {
        if (k.isEmpty()) return "0"
        val sb = StringBuilder()
        for ((i, e) in k.entries.sortedBy { order(it.key) }.withIndex()) {
            val (name, c) = e
            val neg = c.signum < 0
            val a = c.abs()
            if (i == 0) { if (neg) sb.append("-") } else sb.append(if (neg) " - " else " + ")
            if (a != Rational.ONE) sb.append(Tex.rational(a)).append(" ")
            sb.append(tex(name))
        }
        return sb.toString()
    }

    /** Loop momenta come first, then external ones in order. */
    fun order(name: String) = when {
        name == "l" -> "0"
        name.startsWith("k") -> "1$name"
        name.startsWith("x") -> "2$name"
        else -> "3" + name.padStart(6, '0')
    }
}

/** Gives Lorentz indices their printed names (μ, ν, ρ, …), keeping hints. */
class IndexNames {
    private val names = HashMap<Idx, String>()
    private val pool = listOf("\\mu", "\\nu", "\\rho", "\\sigma", "\\alpha", "\\beta", "\\lambda", "\\kappa", "\\tau", "\\eta", "\\zeta", "\\xi", "\\omega")
    private var next = 0
    fun name(i: Idx): String = names.getOrPut(i) {
        i.hint ?: run {
            val used = names.values.toSet()
            var n: String
            do {
                n = if (next < pool.size) pool[next] else pool[next % pool.size] + "_{${next / pool.size}}"
                next++
            } while (n in used)
            n
        }
    }
    fun reserve(i: Idx, name: String) { names[i] = name }
}

object Tex {
    fun rational(r: Rational): String =
        if (r.isInteger) r.num.toString() else "\\frac{${r.num.abs()}}{${r.den}}".let { if (r.signum < 0) "-$it" else it }

    /**
     * An expression as LaTeX. [names] names the indices; negative powers go under a fraction bar.
     * Chains are printed between [chainOpen] and [chainClose] for each line.
     */
    fun of(
        e: Expr,
        names: IndexNames = IndexNames(),
        chainOpen: (Int) -> String = { "" },
        chainClose: (Int) -> String = { "" },
    ): String {
        if (e.isZero) return "0"
        val terms = e.terms.entries.sortedWith(compareBy({ termOrder(it.key.mono) }, { it.key.mono.key }, { it.key.chains.toString() }))
        val sb = StringBuilder()
        for ((i, t) in terms.withIndex()) {
            val (key, c) = t
            val (sign, body) = term(c, key, names, chainOpen, chainClose)
            if (i == 0) { if (sign < 0) sb.append("-") } else sb.append(if (sign < 0) " - " else " + ")
            sb.append(body)
        }
        return sb.toString()
    }

    /** Terms with more momenta and invariants come first: s − m_Z², not −m_Z² + s. */
    private fun termOrder(m: Mono): Int = -m.factors.sumOf { (a, n) -> if (n > 0 && (a !is Sym || a.isKinematic)) n else 0 }

    /** The sign and the rest of one term. */
    private fun term(c: CQ, key: TermKey, names: IndexNames, open: (Int) -> String, close: (Int) -> String): Pair<Int, String> {
        val numParts = ArrayList<String>()
        val denParts = ArrayList<String>()
        // i and real/imaginary coefficients.
        val (sign, coefNum, coefDen, imagUnit, complexCoef) = coefficient(c)
        val seen = HashSet<Idx>()
        val ordered = key.mono.factors.sortedWith(compareBy({ rank(it.first) }, { it.first.key }))
        val products = ordered.count { it.second > 0 } > 1
        for ((a, n) in ordered) {
            val base = atomTex(a, names, seen)
            val paren = (a is Den && needsParen(base)) || (a is Dot && a.a != a.b && (kotlin.math.abs(n) != 1 || products))
            val b = if (paren) "\\left($base\\right)" else base
            val pw = if (a is Dot && a.a == a.b) {
                // p² already has its square.
                val k = 2 * kotlin.math.abs(n)
                MomNames.tex(a.a).let { if (it.contains("_")) "$it^{$k}" else "$it^{$k}" }
            } else if (kotlin.math.abs(n) == 1) b else "$b^{${kotlin.math.abs(n)}}"
            if (n > 0) numParts.add(pw) else denParts.add(pw)
        }
        val chains = key.chains.mapIndexed { ci, ch -> open(ci) + chainTex(ch, names, seen) + close(ci) }
        val num = StringBuilder()
        if (complexCoef != null) num.append(complexCoef)
        val symbols = numParts.joinToString(" ")
        val coefPart = if (coefNum == BigInteger.ONE) "" else coefNum.toString()
        val top = listOf(coefPart, if (imagUnit) "i" else "", symbols).filter { it.isNotEmpty() }.joinToString(" ").trim()
        val bottom = listOf(if (coefDen == BigInteger.ONE) "" else coefDen.toString(), denParts.joinToString(" ")).filter { it.isNotEmpty() }.joinToString(" ")
        val scalar = when {
            complexCoef != null && bottom.isEmpty() -> (complexCoef + " " + symbols).trim()
            complexCoef != null -> "\\frac{${(complexCoef + " " + symbols).trim()}}{$bottom}"
            bottom.isEmpty() -> top
            else -> "\\frac{${top.ifEmpty { "1" }}}{$bottom}"
        }
        val chainText = chains.joinToString(" ")
        val body = when {
            scalar.isEmpty() && chainText.isEmpty() -> "1"
            scalar.isEmpty() -> chainText
            chainText.isEmpty() -> scalar
            else -> "$scalar\\, $chainText"
        }
        return sign to body
    }

    private data class Coef(val sign: Int, val num: BigInteger, val den: BigInteger, val imag: Boolean, val complex: String?)

    private fun coefficient(c: CQ): Coef {
        if (c.isReal) return Coef(c.re.signum, c.re.num.abs(), c.re.den, false, null)
        if (c.isImaginary) return Coef(c.im.signum, c.im.num.abs(), c.im.den, true, null)
        val re = rational(c.re)
        val im = rational(c.im.abs())
        val s = "\\left($re ${if (c.im.signum < 0) "-" else "+"} ${if (im == "1") "" else im}i\\right)"
        return Coef(1, BigInteger.ONE, BigInteger.ONE, false, s)
    }

    fun atomTex(a: Atom, names: IndexNames, seen: MutableSet<Idx> = HashSet()): String = when (a) {
        is Sym -> a.tex
        Dim -> "d"
        is Dot -> if (a.a == a.b) "${MomNames.tex(a.a)}^{2}" else "${MomNames.tex(a.a)}\\cdot ${MomNames.tex(a.b)}"
        is Vec -> MomNames.tex(a.p).let { p -> if (p.contains("_")) "{$p}${index(a.i, names, seen)}" else p + index(a.i, names, seen) }
        is Met -> "g" + indexPair(a.i, a.j, names, seen)
        is Eps -> "\\epsilon^{" + a.slots.joinToString(" ") { s -> when (s) { is Slot.I -> names.name(s.i); is Slot.P -> MomNames.tex(s.p) } } + "}"
        is Den -> a.display ?: of(a.content, names)
        else -> a.key
    }

    private fun index(i: Idx, names: IndexNames, seen: MutableSet<Idx>): String =
        if (seen.add(i)) "^{${names.name(i)}}" else "_{${names.name(i)}}"

    private fun indexPair(i: Idx, j: Idx, names: IndexNames, seen: MutableSet<Idx>): String {
        val up1 = seen.add(i)
        val up2 = seen.add(j)
        return if (up1 && up2) "^{${names.name(i)}${names.name(j)}}"
        else if (!up1 && !up2) "_{${names.name(i)}${names.name(j)}}"
        else "^{${names.name(if (up1) i else j)}}_{${names.name(if (up1) j else i)}}"
    }

    fun chainTex(ch: List<G>, names: IndexNames, seen: MutableSet<Idx> = HashSet()): String =
        if (ch.isEmpty()) "" else ch.joinToString(" ") { g ->
            when (g) {
                is G.I -> "\\gamma" + index(g.i, names, seen)
                is G.S -> slashTex(g.p)
                G.Five -> "\\gamma^{5}"
            }
        }

    /** p̸ with the slash on the letter only: \slashed{p}_{1}. */
    fun slashTex(p: String): String {
        val t = MomNames.tex(p)
        val u = t.indexOf('_')
        return if (u > 0 && !t.startsWith("\\varepsilon")) "\\slashed{${t.substring(0, u)}}${t.substring(u)}" else "\\slashed{$t}"
    }

    /** Display order inside a term: couplings and masses, invariants, dot products, tensors, denominators. */
    private fun rank(a: Atom): Int = when (a) {
        is Sym -> if (a.isKinematic) 2 else if (a.name.startsWith("x")) 3 else if (a.name == "lnDelta") 7 else 1
        Dim -> 0
        is Dot -> 4
        is Vec, is Met, is Eps -> 5
        is Den -> 6
        else -> 8
    }

    /** A factor in brackets unless it's a single product. */
    fun paren(s: String): String = if (needsParen(s)) "\\left($s\\right)" else s

    fun needsParen(s: String): Boolean {
        var depth = 0
        for ((i, ch) in s.withIndex()) {
            when (ch) {
                '{', '(' -> depth++
                '}', ')' -> depth--
                '+', '-' -> if (depth == 0 && i > 0) return true
            }
            if (s.startsWith("\\left", i)) depth++
            if (s.startsWith("\\right", i)) depth--
        }
        return false
    }
}
