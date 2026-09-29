package com.example.feynman.physics

/*
 * The symbolic algebra behind every calculation: sums of terms, each a complex rational
 * coefficient times a product of atoms (couplings, masses, dot products, vectors with a
 * Lorentz index, metric tensors, Levi-Civita tensors, propagator denominators, the
 * dimension d) and, for open fermion lines, strings of Dirac matrices.
 *
 * Plain Kotlin with no Android in it, so it's tested on the JVM (see jvm/ and app/src/test).
 */

/** An exact complex number re + i·im. */
data class CQ(val re: Rational, val im: Rational) {
    operator fun plus(o: CQ) = CQ(re + o.re, im + o.im)
    operator fun minus(o: CQ) = CQ(re - o.re, im - o.im)
    operator fun times(o: CQ) = CQ(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun times(r: Rational) = CQ(re * r, im * r)
    operator fun unaryMinus() = CQ(-re, -im)
    fun conj() = CQ(re, -im)
    val isZero get() = re.signum == 0 && im.signum == 0
    val isReal get() = im.signum == 0
    val isImaginary get() = re.signum == 0 && im.signum != 0
    fun inverse(): CQ {
        val n = re * re + im * im
        return CQ(re / n, -im / n)
    }
    operator fun div(o: CQ) = this * o.inverse()
    fun toComplex() = Complex(re.toDouble(), im.toDouble())

    companion object {
        val ZERO = CQ(Rational.ZERO, Rational.ZERO)
        val ONE = CQ(Rational.ONE, Rational.ZERO)
        val I = CQ(Rational.ZERO, Rational.ONE)
        fun of(n: Long, d: Long = 1) = CQ(Rational.of(n, d), Rational.ZERO)
        fun of(r: Rational) = CQ(r, Rational.ZERO)
        fun imag(n: Long, d: Long = 1) = CQ(Rational.ZERO, Rational.of(n, d))
    }
}

/** A Lorentz index; its printed name (μ, ν, …) is chosen when an expression is shown. */
data class Idx(val id: Int, val hint: String? = null) : Comparable<Idx> {
    override fun compareTo(other: Idx) = id.compareTo(other.id)
}

/** Hands out fresh indices for one calculation. */
class IndexPool(private var next: Int = 1) {
    fun fresh(hint: String? = null) = Idx(next++, hint)
}

/** Momenta are linear combinations of basic momenta (p1, p2, k, ℓ, polarizations ε1 …). */
typealias Mom = Map<String, Rational>

fun mom(name: String): Mom = mapOf(name to Rational.ONE)
operator fun Mom.plus(o: Mom): Mom {
    val out = LinkedHashMap(this)
    for ((k, v) in o) {
        val s = (out[k] ?: Rational.ZERO) + v
        if (s.signum == 0) out.remove(k) else out[k] = s
    }
    return out
}
operator fun Mom.unaryMinus(): Mom = mapValues { -it.value }
operator fun Mom.minus(o: Mom): Mom = this + (-o)
fun Mom.scale(r: Rational): Mom = if (r.signum == 0) emptyMap() else mapValues { it.value * r }
val Mom.isZero get() = isEmpty()

/** One factor of a term. Atoms sort by [key], which also decides equality. */
sealed class Atom : Comparable<Atom> {
    abstract val key: String
    override fun compareTo(other: Atom) = key.compareTo(other.key)
    override fun equals(other: Any?) = other is Atom && other.key == key
    override fun hashCode() = key.hashCode()
    override fun toString() = key
}

/**
 * A named quantity: a coupling, a mass, a Mandelstam variable, a Feynman parameter.
 * [complex] symbols (CKM elements) have a separate conjugate, named with a trailing *.
 */
class Sym(val name: String, val tex: String, val complex: Boolean = false, val order: Int = 50) : Atom() {
    override val key = "s%02d:%s".format(order, name)
    fun conj(): Sym = if (!complex) this
    else if (name.endsWith("*")) Sym(name.dropLast(1), tex.removeSuffix("^{*}"), true, order)
    else Sym("$name*", "$tex^{*}", true, order)
}

/** The spacetime dimension d (4 − 2ε in dimensional regularization). */
object Dim : Atom() { override val key = "d" }

/** a·b of two basic momenta. */
class Dot private constructor(val a: String, val b: String) : Atom() {
    override val key = "D:$a.$b"
    companion object { fun of(a: String, b: String) = if (a <= b) Dot(a, b) else Dot(b, a) }
}

/** p^μ. */
class Vec(val p: String, val i: Idx) : Atom() { override val key = "V:$p:${i.id}" }

/** g^{μν}. */
class Met private constructor(val i: Idx, val j: Idx) : Atom() {
    override val key = "M:${i.id}:${j.id}"
    companion object { fun of(i: Idx, j: Idx) = if (i.id <= j.id) Met(i, j) else Met(j, i) }
}

/** One slot of a Levi-Civita tensor: an index or a momentum. */
sealed class Slot : Comparable<Slot> {
    data class I(val i: Idx) : Slot()
    data class P(val p: String) : Slot()
    val sortKey get() = when (this) { is I -> "0:%06d".format(i.id); is P -> "1:$p" }
    override fun compareTo(other: Slot) = sortKey.compareTo(other.sortKey)
}

/** ε^{abcd} with its slots in canonical order (the sign is kept in the coefficient). */
class Eps private constructor(val slots: List<Slot>) : Atom() {
    override val key = "E:" + slots.joinToString(",") { it.sortKey }
    companion object {
        /** The sign and the sorted tensor, or null if two slots are the same (it's zero). */
        fun of(slots: List<Slot>): Pair<Int, Eps>? {
            if (slots.toSet().size < slots.size) return null
            val arr = slots.toMutableList()
            var sign = 1
            // Bubble sort, counting swaps.
            for (i in arr.indices) for (j in 0 until arr.size - 1 - i) {
                if (arr[j] > arr[j + 1]) { val t = arr[j]; arr[j] = arr[j + 1]; arr[j + 1] = t; sign = -sign }
            }
            return sign to Eps(arr)
        }
    }
}

/**
 * A propagator denominator (q² − m²), kept whole so the answer shows 1/(s − m_Z²).
 * [content] is its value, which is rewritten when the kinematics are substituted.
 */
class Den(
    val content: Expr,
    val display: String? = null,
    /** The momentum and mass² it was made from (kept for loop integrals; not part of its identity). */
    val q: Mom? = null,
    val m2: Expr? = null,
) : Atom() {
    override val key = "Q:" + content.canonical()
}

/** Dirac matrices along an open fermion line. */
sealed class G {
    data class I(val i: Idx) : G()
    data class S(val p: String) : G()
    object Five : G() { override fun toString() = "γ5" }
    val sortKey get() = when (this) { is S -> "1:$p"; is I -> "2:%06d".format(i.id); Five -> "9" }
}

typealias GString = List<G>

/** A product of atoms with integer powers, sorted by atom. */
class Mono(val factors: List<Pair<Atom, Int>>) {
    val key: String = factors.joinToString("*") { (a, n) -> if (n == 1) a.key else "${a.key}^$n" }
    override fun equals(other: Any?) = other is Mono && other.key == key
    override fun hashCode() = key.hashCode()
    override fun toString() = key

    operator fun times(o: Mono): Mono {
        if (factors.isEmpty()) return o
        if (o.factors.isEmpty()) return this
        val map = java.util.TreeMap<Atom, Int>()
        for ((a, n) in factors) map[a] = (map[a] ?: 0) + n
        for ((a, n) in o.factors) map[a] = (map[a] ?: 0) + n
        return Mono(map.entries.filter { it.value != 0 }.map { it.key to it.value })
    }

    fun power(a: Atom) = factors.firstOrNull { it.first == a }?.second ?: 0
    fun without(a: Atom) = Mono(factors.filter { it.first != a })
    fun inverse() = Mono(factors.map { it.first to -it.second })

    companion object {
        val ONE = Mono(emptyList())
        fun of(a: Atom, n: Int = 1) = if (n == 0) ONE else Mono(listOf(a to n))
        fun of(list: List<Pair<Atom, Int>>): Mono {
            val map = java.util.TreeMap<Atom, Int>()
            for ((a, n) in list) map[a] = (map[a] ?: 0) + n
            return Mono(map.entries.filter { it.value != 0 }.map { it.key to it.value })
        }
    }
}

/** One term's shape: the scalar atoms and the Dirac string of each open fermion line. */
data class TermKey(val mono: Mono, val chains: List<GString>)

/**
 * A sum of terms. Scalar expressions have no chains; an amplitude with open fermion lines
 * has one Dirac string per line in every term.
 */
class Expr(val terms: Map<TermKey, CQ>) {
    val isZero get() = terms.isEmpty()

    operator fun plus(o: Expr): Expr {
        if (isZero) return o
        if (o.isZero) return this
        val out = LinkedHashMap(terms)
        for ((k, v) in o.terms) {
            val s = (out[k] ?: CQ.ZERO) + v
            if (s.isZero) out.remove(k) else out[k] = s
        }
        return Expr(out)
    }

    operator fun minus(o: Expr) = this + o * CQ.of(-1)
    operator fun unaryMinus() = this * CQ.of(-1)

    operator fun times(c: CQ): Expr = if (c.isZero) ZERO else Expr(terms.mapValues { it.value * c })
    operator fun times(r: Rational) = times(CQ.of(r))

    /** The product; the Dirac strings of the two factors are separate lines, one after the other. */
    operator fun times(o: Expr): Expr {
        if (isZero || o.isZero) return ZERO
        val out = HashMap<TermKey, CQ>()
        for ((k1, c1) in terms) for ((k2, c2) in o.terms) {
            val k = TermKey(k1.mono * k2.mono, k1.chains + k2.chains)
            val s = (out[k] ?: CQ.ZERO) + c1 * c2
            if (s.isZero) out.remove(k) else out[k] = s
        }
        return Expr(out)
    }

    /** The product along one fermion line: both have one chain, and the strings are joined. */
    fun dirac(o: Expr): Expr {
        if (isZero || o.isZero) return ZERO
        val out = HashMap<TermKey, CQ>()
        for ((k1, c1) in terms) for ((k2, c2) in o.terms) {
            val c = (k1.chains.singleOrNull() ?: emptyList()) + (k2.chains.singleOrNull() ?: emptyList())
            val k = TermKey(k1.mono * k2.mono, listOf(c))
            val s = (out[k] ?: CQ.ZERO) + c1 * c2
            if (s.isZero) out.remove(k) else out[k] = s
        }
        return Expr(out)
    }

    fun mapTerms(f: (TermKey, CQ) -> Expr): Expr {
        var out = ZERO
        val acc = HashMap<TermKey, CQ>()
        for ((k, c) in terms) {
            val e = f(k, c)
            for ((k2, c2) in e.terms) {
                val s = (acc[k2] ?: CQ.ZERO) + c2
                if (s.isZero) acc.remove(k2) else acc[k2] = s
            }
        }
        out = Expr(acc)
        return out
    }

    /** Replaces positive powers of [atom] by [value] (negative powers stay). */
    fun substitute(atom: Atom, value: Expr): Expr = mapTerms { k, c ->
        val n = k.mono.power(atom)
        if (n <= 0) Expr(mapOf(k to c))
        else {
            var e = Expr(mapOf(TermKey(k.mono.without(atom), k.chains) to c))
            repeat(n) { e = e * value }
            e
        }
    }

    /** Each term's atoms rewritten by [f] (null keeps the atom). */
    fun mapAtoms(f: (Atom, Int) -> Expr?): Expr = mapTerms { k, c ->
        var e = Expr(mapOf(TermKey(Mono.ONE, k.chains) to c))
        for ((a, n) in k.mono.factors) {
            val r = f(a, n)
            e = if (r == null) e * Expr(mapOf(TermKey(Mono.of(a, n), emptyList()) to CQ.ONE)) else e * r
        }
        e
    }

    fun conj(): Expr = mapAtoms { a, n -> if (a is Sym && a.complex) atom(a.conj(), n) else null }.let { e ->
        Expr(e.terms.mapValues { it.value.conj() })
    }

    fun canonical(): String = terms.entries.sortedBy { it.key.mono.key + "|" + it.key.chains }
        .joinToString("+") { "(${it.value.re},${it.value.im})${it.key.mono.key}${if (it.key.chains.isEmpty()) "" else it.key.chains.toString()}" }

    override fun equals(other: Any?) = other is Expr && (this - other).isZero
    override fun hashCode() = canonical().hashCode()
    override fun toString() = canonical()

    /** Every atom that occurs. */
    fun atoms(): Set<Atom> = terms.keys.flatMap { k -> k.mono.factors.map { it.first } }.toSet()

    /** True if no term depends on [a]. */
    fun free(a: Atom) = terms.keys.none { it.mono.power(a) != 0 }

    /** The coefficient of a^n, grouped. */
    fun collect(a: Atom): Map<Int, Expr> {
        val out = HashMap<Int, Expr>()
        for ((k, c) in terms) {
            val n = k.mono.power(a)
            out[n] = (out[n] ?: ZERO) + Expr(mapOf(TermKey(k.mono.without(a), k.chains) to c))
        }
        return out
    }

    companion object {
        val ZERO = Expr(emptyMap())
        val ONE = Expr(mapOf(TermKey(Mono.ONE, emptyList()) to CQ.ONE))
        fun const(c: CQ) = if (c.isZero) ZERO else Expr(mapOf(TermKey(Mono.ONE, emptyList()) to c))
        fun const(n: Long, d: Long = 1) = const(CQ.of(n, d))
        fun const(r: Rational) = const(CQ.of(r))
        val I = const(CQ.I)
    }
}

/** Adds many expressions without copying the sum each time. */
class ExprSum {
    private val acc = HashMap<TermKey, CQ>()
    fun add(e: Expr, factor: CQ = CQ.ONE) {
        for ((k, c) in e.terms) {
            val s = (acc[k] ?: CQ.ZERO) + c * factor
            if (s.isZero) acc.remove(k) else acc[k] = s
        }
    }
    /** Adds a × b, term by term. */
    fun addProduct(a: Expr, b: Expr, factor: CQ = CQ.ONE) {
        for ((k1, c1) in a.terms) for ((k2, c2) in b.terms) {
            val k = TermKey(k1.mono * k2.mono, k1.chains + k2.chains)
            val s = (acc[k] ?: CQ.ZERO) + c1 * c2 * factor
            if (s.isZero) acc.remove(k) else acc[k] = s
        }
    }
    fun toExpr() = Expr(HashMap(acc))
}

fun atom(a: Atom, n: Int = 1) = Expr(mapOf(TermKey(Mono.of(a, n), emptyList()) to CQ.ONE))
fun sym(s: Sym, n: Int = 1) = atom(s, n)
fun sum(list: Iterable<Expr>) = list.fold(Expr.ZERO) { a, b -> a + b }
fun Expr.pow(n: Int): Expr { var e = Expr.ONE; repeat(n) { e = e * this }; return e }

/** p^μ for a combination of momenta. */
fun vec(p: Mom, i: Idx): Expr = sum(p.map { (name, c) -> atom(Vec(name, i)) * c })

/** p·q, expanded into dot products of basic momenta. */
fun dot(p: Mom, q: Mom): Expr {
    var e = Expr.ZERO
    for ((a, ca) in p) for ((b, cb) in q) e += atom(Dot.of(a, b)) * (ca * cb)
    return e
}

fun met(i: Idx, j: Idx): Expr = if (i == j) atom(Dim) else atom(Met.of(i, j))

/** A one-line Dirac expression: a single string with coefficient 1. */
fun gammas(vararg g: G): Expr = Expr(mapOf(TermKey(Mono.ONE, listOf(g.toList())) to CQ.ONE))
fun gamma(i: Idx) = gammas(G.I(i))
fun slash(p: Mom): Expr = sum(p.map { (name, c) -> gammas(G.S(name)) * c })
val gamma5 get() = gammas(G.Five)
/** The unit matrix on a fermion line. */
val diracOne get() = gammas()
/** P_L = (1 − γ5)/2 and P_R = (1 + γ5)/2. */
val projL get() = (diracOne - gamma5) * Rational.of(1, 2)
val projR get() = (diracOne + gamma5) * Rational.of(1, 2)

/** (q² − m²) as a propagator denominator raised to −1, shown as written: (p_1 + p_2)^2 - m_Z^2. */
fun propagatorDen(q: Mom, mass: Sym?): Expr =
    propagatorDen(q, if (mass == null) Expr.ZERO else sym(mass, 2), mass?.let { "${it.tex}^{2}" })

/** (q² − m²)⁻¹ with any m² (ξm_W² in an Rξ gauge); [m2Tex] is how m² is written. */
fun propagatorDen(q: Mom, m2: Expr, m2Tex: String?): Expr {
    val content = dot(q, q) - m2
    val qt = MomNames.tex(q)
    val square = if (q.size == 1 && q.values.first().abs() == Rational.ONE) "$qt^{2}".removePrefix("-") else "\\left($qt\\right)^{2}"
    val display = if (m2.isZero) square else "$square - ${m2Tex ?: Tex.paren(Tex.of(m2))}"
    return atom(Den(content, display, q, m2), -1)
}

fun levi(slots: List<Slot>): Expr {
    val (sign, e) = Eps.of(slots) ?: return Expr.ZERO
    return atom(e) * CQ.of(sign.toLong())
}
