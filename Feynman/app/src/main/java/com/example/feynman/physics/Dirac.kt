package com.example.feynman.physics

/*
 * Lorentz and Dirac algebra: contracting repeated indices, traces of Dirac matrices (with γ5
 * in naive anticommuting form), contractions γ^μ … γ_μ in d dimensions, putting open lines
 * into a standard order and the Dirac equation for the spinors at their ends.
 */

/** Fresh indices for dummy sums made inside traces (kept far from the diagram's own). */
internal object Dummies {
    private var next = 1_000_000
    @Synchronized fun fresh() = Idx(next++)
}

/** A term being worked on: atoms with positive powers listed one by one. */
private class Work(var coef: CQ, val atoms: MutableList<Atom>, val rest: MutableList<Pair<Atom, Int>>, val chains: MutableList<MutableList<G>>) {
    fun copy() = Work(coef, atoms.toMutableList(), rest.toMutableList(), chains.map { it.toMutableList() }.toMutableList())
    fun toExpr(): Expr {
        val list = atoms.map { it to 1 } + rest
        return Expr(mapOf(TermKey(Mono.of(list), chains.map { it.toList() }) to coef))
    }
}

private fun indicesOf(a: Atom): List<Idx> = when (a) {
    is Vec -> listOf(a.i)
    is Met -> listOf(a.i, a.j)
    is Eps -> a.slots.mapNotNull { (it as? Slot.I)?.i }
    else -> emptyList()
}

private fun renameIn(a: Atom, from: Idx, to: Idx): Expr = when (a) {
    is Vec -> atom(Vec(a.p, if (a.i == from) to else a.i))
    is Met -> met(if (a.i == from) to else a.i, if (a.j == from) to else a.j)
    is Eps -> levi(a.slots.map { if (it is Slot.I && it.i == from) Slot.I(to) else it })
    else -> atom(a)
}

private fun isIndexed(a: Atom) = a is Vec || a is Met || a is Eps

/** Contracts every repeated Lorentz index. */
fun contract(e: Expr): Expr = e.mapTerms { k, c -> contractTerm(k, c) }

private fun contractTerm(k: TermKey, c: CQ): Expr {
    val atoms = ArrayList<Atom>()
    val rest = ArrayList<Pair<Atom, Int>>()
    for ((a, n) in k.mono.factors) if (n > 0 && isIndexed(a)) repeat(n) { atoms.add(a) } else rest.add(a to n)
    val w = Work(c, atoms, rest, k.chains.map { it.toMutableList() }.toMutableList())
    return process(w)
}

private fun process(start: Work): Expr {
    var out = Expr.ZERO
    val queue = ArrayDeque<Work>()
    queue.add(start)
    while (queue.isNotEmpty()) {
        val w = queue.removeFirst()
        val next = step(w)
        if (next == null) out += w.toExpr()
        else next.forEach { queue.add(it) }
    }
    return out
}

/** One rewrite of [w], or null if nothing more can be contracted. */
private fun step(w: Work): List<Work>? {
    // Two Levi-Civita tensors multiply out into metrics: ε^{a…}ε^{b…} = −det g(a_i, b_j).
    val epsList = w.atoms.withIndex().filter { it.value is Eps }
    if (epsList.size >= 2) {
        val e1 = epsList[0].value as Eps
        val e2 = epsList[1].value as Eps
        val base = w.copy()
        base.atoms.removeAt(epsList[1].index)
        base.atoms.removeAt(epsList[0].index)
        val det = determinant(e1.slots, e2.slots) * CQ.of(-1)
        return expand(base, det)
    }
    // Count where each index occurs.
    val occ = HashMap<Idx, MutableList<Pair<Int, Int>>>() // (-1 - atom position) or (chain, position)
    w.atoms.forEachIndexed { pos, a -> indicesOf(a).forEach { occ.getOrPut(it) { ArrayList() }.add(-1 to pos) } }
    w.chains.forEachIndexed { ci, ch -> ch.forEachIndexed { pos, g -> if (g is G.I) occ.getOrPut(g.i) { ArrayList() }.add(ci to pos) } }
    for ((idx, places) in occ) {
        if (places.size < 2) continue
        val (p1, p2) = places[0] to places[1]
        val a1 = if (p1.first == -1) w.atoms[p1.second] else null
        val a2 = if (p2.first == -1) w.atoms[p2.second] else null
        // Metric: rename the other occurrence.
        val metPlace = listOf(p1, p2).firstOrNull { it.first == -1 && w.atoms[it.second] is Met }
        if (metPlace != null) {
            val m = w.atoms[metPlace.second] as Met
            if (m.i == m.j) continue
            val other = if (m.i == idx) m.j else m.i
            val otherPlace = if (metPlace == p1) p2 else p1
            val n = w.copy()
            n.atoms.removeAt(metPlace.second)
            if (otherPlace.first == -1) {
                val pos = if (otherPlace.second > metPlace.second) otherPlace.second - 1 else otherPlace.second
                val a = n.atoms.removeAt(pos)
                return expand(n, renameIn(a, idx, other))
            } else {
                n.chains[otherPlace.first][otherPlace.second] = G.I(other)
                return listOf(n)
            }
        }
        if (a1 is Vec && a2 is Vec) {
            val n = w.copy()
            n.atoms.removeAt(maxOf(p1.second, p2.second)); n.atoms.removeAt(minOf(p1.second, p2.second))
            n.atoms.add(Dot.of(a1.p, a2.p))
            return listOf(n)
        }
        val vecPlace = listOf(p1, p2).firstOrNull { it.first == -1 && w.atoms[it.second] is Vec }
        if (vecPlace != null) {
            val v = w.atoms[vecPlace.second] as Vec
            val otherPlace = if (vecPlace == p1) p2 else p1
            if (otherPlace.first >= 0) {
                val n = w.copy()
                n.atoms.removeAt(vecPlace.second)
                n.chains[otherPlace.first][otherPlace.second] = G.S(v.p)
                return listOf(n)
            }
            val other = w.atoms[otherPlace.second]
            if (other is Eps) {
                val n = w.copy()
                n.atoms.removeAt(maxOf(vecPlace.second, otherPlace.second)); n.atoms.removeAt(minOf(vecPlace.second, otherPlace.second))
                return expand(n, levi(other.slots.map { if (it is Slot.I && it.i == idx) Slot.P(v.p) else it }))
            }
        }
        // An index twice in one ε: zero (Eps.of already catches most of these).
        if (a1 is Eps && a1 === a2) return emptyList()
        // Twice on one fermion line: γ^μ … γ_μ.
        if (p1.first >= 0 && p1.first == p2.first) {
            val ci = p1.first
            val lo = minOf(p1.second, p2.second)
            val hi = maxOf(p1.second, p2.second)
            val ch = w.chains[ci]
            val inside = ch.subList(lo + 1, hi).toList()
            val results = sandwich(inside)
            return results.map { (coef, str) ->
                val n = w.copy()
                val newChain = (ch.subList(0, lo) + str + ch.subList(hi + 1, ch.size)).toMutableList()
                n.chains[ci] = newChain
                n to coef
            }.flatMap { (n, coef) -> expand(n, coef) }
        }
    }
    return null
}

/** Multiplies [base] by the scalar expression [e], one work item per term. */
private fun expand(base: Work, e: Expr): List<Work> = e.terms.map { (k, c) ->
    val n = base.copy()
    n.coef = n.coef * c
    for ((a, p) in k.mono.factors) if (p > 0 && isIndexed(a)) repeat(p) { n.atoms.add(a) } else n.rest.add(a to p)
    n
}

private fun slotPair(a: Slot, b: Slot): Expr = when {
    a is Slot.I && b is Slot.I -> met(a.i, b.i)
    a is Slot.I && b is Slot.P -> atom(Vec(b.p, a.i))
    a is Slot.P && b is Slot.I -> atom(Vec(a.p, b.i))
    else -> atom(Dot.of((a as Slot.P).p, (b as Slot.P).p))
}

private fun determinant(a: List<Slot>, b: List<Slot>): Expr {
    val n = a.size
    if (n == 0) return Expr.ONE
    var out = Expr.ZERO
    for (j in 0 until n) {
        val minor = determinant(a.drop(1), b.filterIndexed { k, _ -> k != j })
        val term = slotPair(a[0], b[j]) * minor
        out = if (j % 2 == 0) out + term else out - term
    }
    return out
}

/**
 * γ^μ S γ_μ in d dimensions for the string S between them, as (coefficient, string) pairs:
 * γ^μ γ_μ = d, and γ^μ S′ a γ_μ = 2 a̸ S′ − (γ^μ S′ γ_μ) a.
 */
fun sandwich(s: List<G>): List<Pair<Expr, List<G>>> {
    if (s.isEmpty()) return listOf(atom(Dim) to emptyList())
    val last = s.last()
    val front = s.dropLast(1)
    val inner = sandwich(front)
    if (last is G.Five) return inner.map { (c, str) -> c * CQ.of(-1) to str + G.Five }
    val out = ArrayList<Pair<Expr, List<G>>>()
    out.add(Expr.const(2) to listOf(last) + front)
    inner.forEach { (c, str) -> out.add(c * CQ.of(-1) to str + last) }
    return out
}

private fun pairOf(a: G, b: G): Expr = when {
    a is G.I && b is G.I -> met(a.i, b.i)
    a is G.I && b is G.S -> atom(Vec(b.p, a.i))
    a is G.S && b is G.I -> atom(Vec(a.p, b.i))
    a is G.S && b is G.S -> atom(Dot.of(a.p, b.p))
    else -> error("γ5 has no pairing")
}

private fun slotOf(g: G): Slot = when (g) { is G.I -> Slot.I(g.i); is G.S -> Slot.P(g.p); G.Five -> error("γ5") }

/** tr(S), with tr 1 = 4, contracted. */
fun trace(s: List<G>): Expr {
    // Move every γ5 to the right: each passes the matrices after it (γ5 anticommutes), γ5² = 1.
    var sign = 1
    var fives = 0
    val rest = ArrayList<G>()
    for ((pos, g) in s.withIndex()) {
        if (g is G.Five) {
            val after = s.subList(pos + 1, s.size).count { it !is G.Five }
            if (after % 2 == 1) sign = -sign
            fives++
        } else rest.add(g)
    }
    val t = if (fives % 2 == 0) traceNoFive(rest) else traceFive(rest)
    return contract(t * CQ.of(sign.toLong()))
}

private val traceCache = HashMap<List<G>, Expr>()

private fun traceNoFive(s: List<G>): Expr {
    if (s.size % 2 == 1) return Expr.ZERO
    if (s.isEmpty()) return Expr.const(4)
    if (s.size > 6) return traceNoFiveRaw(s)
    return synchronized(traceCache) { traceCache[s] } ?: traceNoFiveRaw(s).also { synchronized(traceCache) { traceCache[s] = it } }
}

private fun traceNoFiveRaw(s: List<G>): Expr {
    var out = Expr.ZERO
    val a = s[0]
    for (k in 1 until s.size) {
        val rest = s.subList(1, k) + s.subList(k + 1, s.size)
        val term = pairOf(a, s[k]) * traceNoFive(rest)
        out = if (k % 2 == 1) out + term else out - term
    }
    return out
}

/** tr(S γ5), γ5 at the right; tr(γ^μγ^νγ^ργ^σγ5) = −4iε^{μνρσ} (ε^{0123} = −1). */
private fun traceFive(s: List<G>): Expr {
    val n = s.size
    if (n < 4 || n % 2 == 1) return Expr.ZERO
    if (n == 4) return levi(s.map { slotOf(it) }) * CQ.imag(-4)
    // a1 a2 a3 = (a1·a2) a3 + (a2·a3) a1 − (a1·a3) a2 − i ε^{σ a1 a2 a3} γ_σ γ5, then γ5 R γ5 = (−1)^{|R|} R.
    val (a1, a2, a3) = Triple(s[0], s[1], s[2])
    val r = s.subList(3, n)
    var out = pairOf(a1, a2) * traceFive(listOf(a3) + r) +
        pairOf(a2, a3) * traceFive(listOf(a1) + r) -
        pairOf(a1, a3) * traceFive(listOf(a2) + r)
    val sigma = Dummies.fresh()
    val eps = levi(listOf(Slot.I(sigma), slotOf(a1), slotOf(a2), slotOf(a3)))
    val sign = if (r.size % 2 == 0) 1L else -1L
    out += eps * traceNoFive(listOf(G.I(sigma)) + r) * CQ.imag(-sign)
    return out
}

/** The spinor at one end of an open fermion line. */
data class Spinor(val kind: Kind, val p: String, val mass: Expr) {
    enum class Kind { U, V, UBar, VBar }
    val isLeft get() = kind == Kind.UBar || kind == Kind.VBar
    /** p̸ acting on it gives this sign times m. */
    val diracSign get() = if (kind == Kind.U || kind == Kind.UBar) 1L else -1L
}

/**
 * Puts each open line's string in a standard order (the left spinor's momentum first, the
 * right spinor's last, γ5 at the end) using {a, b} = 2a·b, then applies the Dirac equation
 * to the spinors at the ends. [ends] gives the (left, right) spinors of each line.
 */
fun reduceChains(e: Expr, ends: List<Pair<Spinor, Spinor>>): Expr {
    var cur = contract(e)
    for (ci in ends.indices) {
        val (left, right) = ends[ci]
        cur = cur.mapTerms { k, c -> reduceOne(k, c, ci, left, right) }
        cur = contract(cur)
    }
    return cur
}

private fun reduceOne(k: TermKey, c: CQ, ci: Int, left: Spinor, right: Spinor): Expr {
    val order = { g: G ->
        when {
            g is G.S && g.p == left.p -> "0"
            g is G.S && g.p == right.p -> "8"
            else -> g.sortKey
        }
    }
    val sorted = normalOrder(k.chains[ci], order)
    var out = Expr.ZERO
    for ((coef, str) in sorted) {
        var s = str
        var factor = coef
        var changed = true
        while (changed) {
            changed = false
            val first = s.firstOrNull()
            if (first is G.S && first.p == left.p) {
                factor = factor * left.mass * CQ.of(left.diracSign); s = s.drop(1); changed = true; continue
            }
            val five = s.lastOrNull() is G.Five
            val body = if (five) s.dropLast(1) else s
            val last = body.lastOrNull()
            if (last is G.S && last.p == right.p) {
                // p̸ γ5 u = −γ5 p̸ u.
                factor = factor * right.mass * CQ.of(right.diracSign * if (five) -1 else 1)
                s = body.dropLast(1) + if (five) listOf(G.Five) else emptyList()
                changed = true
            }
        }
        val chains = k.chains.toMutableList()
        chains[ci] = s
        out += Expr(mapOf(TermKey(k.mono, chains) to c)) * factor
    }
    return out
}

/** A Dirac string sorted by [order] through anticommutation, as (coefficient, string) pairs. */
fun normalOrder(s: List<G>, order: (G) -> String): List<Pair<Expr, List<G>>> {
    val out = LinkedHashMap<List<G>, Expr>()
    fun add(str: List<G>, c: Expr) {
        val prev = out[str] ?: Expr.ZERO
        out[str] = prev + c
    }
    fun rec(str: List<G>, c: Expr) {
        if (c.isZero) return
        for (i in 0 until str.size - 1) {
            val a = str[i]; val b = str[i + 1]
            if (b is G.Five && a is G.Five) { rec(str.subList(0, i) + str.subList(i + 2, str.size), c); return }
            if (a is G.Five) { rec(str.subList(0, i) + b + a + str.subList(i + 2, str.size), c * CQ.of(-1)); return }
            if (b is G.Five) continue
            if (a == b) {
                rec(str.subList(0, i) + str.subList(i + 2, str.size), c * pairOf(a, b)); return
            }
            if (order(a) > order(b)) {
                // a b = −b a + 2 a·b
                val rest = str.subList(0, i) + str.subList(i + 2, str.size)
                rec(str.subList(0, i) + listOf(b, a) + str.subList(i + 2, str.size), c * CQ.of(-1))
                rec(rest, c * pairOf(a, b) * CQ.of(2))
                return
            }
        }
        add(str, c)
    }
    rec(s, Expr.ONE)
    return out.entries.filter { !it.value.isZero }.map { it.value to it.key }
}

/**
 * Γ' = CΓᵀC⁻¹ on each term's (single) Dirac string: the vertex read the other way along the
 * fermion flow (Denner et al., Nucl. Phys. B 387 (1992) 467). The string is reversed and every
 * γ^μ and p̸ changes sign; γ5 (so P_L, P_R and 1) stays.
 */
fun reverseDirac(e: Expr): Expr = e.mapTerms { k, c ->
    var sign = 1L
    val chains = k.chains.map { ch -> ch.reversed().also { r -> r.forEach { if (it !is G.Five) sign = -sign } } }
    Expr(mapOf(TermKey(k.mono, chains) to c * CQ.of(sign)))
}
