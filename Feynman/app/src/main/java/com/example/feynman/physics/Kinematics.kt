package com.example.feynman.physics

/*
 * External kinematics: on-shell masses, momentum conservation, and Mandelstam variables for
 * 2 → 2 (s, t, u) and the fixed dot products of 1 → 2 decays.
 */
object Mandelstam {
    val s = Sym("s", "s", order = 40)
    val t = Sym("t", "t", order = 41)
    val u = Sym("u", "u", order = 42)
    val p2 = Sym("psq", "p^{2}", order = 43)
}

/** Symbols that stand for kinematic invariants (they're printed first in a sum). */
val Sym.isKinematic get() = order in 40..49

class Kinematics(val externals: List<External>, private val massOf: (Particle) -> Sym?) {
    /** The mass of an external line (null when massless). */
    fun mass(x: External): Sym? = when (x.particle.spin) {
        Spin.Fermion -> massOf(x.particle)
        Spin.Vector -> if (x.particle === SM.photon || x.particle === SM.gluon) null else x.particle.mass
        else -> x.particle.mass
    }

    private fun m2(x: External): Expr = mass(x)?.let { sym(it, 2) } ?: Expr.ZERO

    /** Two external lines: a self-energy or 2-point function, with p² left free. */
    val twoPoint get() = externals.size == 2
    val incoming get() = externals.filter { it.incoming }
    val outgoing get() = externals.filter { !it.incoming }

    /** Dot products of external momenta as invariants. */
    val rules: Map<Dot, Expr> by lazy {
        val r = HashMap<Dot, Expr>()
        val n = externals.size
        if (twoPoint) {
            // p₂ = p₁ = p: everything is p².
            val (a, b) = externals
            val p = sym(Mandelstam.p2)
            r[Dot.of(a.momentum, a.momentum)] = p
            r[Dot.of(b.momentum, b.momentum)] = p
            // Both incoming or both outgoing would mean p₂ = −p₁.
            r[Dot.of(a.momentum, b.momentum)] = if (a.incoming != b.incoming) p else p * CQ.of(-1)
            return@lazy r
        }
        for (x in externals) r[Dot.of(x.momentum, x.momentum)] = m2(x)
        val ins = incoming
        val outs = outgoing
        if (ins.size == 2 && outs.size == 2) {
            val (p1, p2) = ins
            val (p3, p4) = outs
            val s = sym(Mandelstam.s); val t = sym(Mandelstam.t); val u = sym(Mandelstam.u)
            val half = Rational.of(1, 2)
            r[Dot.of(p1.momentum, p2.momentum)] = (s - m2(p1) - m2(p2)) * half
            r[Dot.of(p3.momentum, p4.momentum)] = (s - m2(p3) - m2(p4)) * half
            r[Dot.of(p1.momentum, p3.momentum)] = (m2(p1) + m2(p3) - t) * half
            r[Dot.of(p2.momentum, p4.momentum)] = (m2(p2) + m2(p4) - t) * half
            r[Dot.of(p1.momentum, p4.momentum)] = (m2(p1) + m2(p4) - u) * half
            r[Dot.of(p2.momentum, p3.momentum)] = (m2(p2) + m2(p3) - u) * half
        } else if (n == 3) {
            // Two on one side, one on the other: a·b = (M² − m_a² − m_b²)/2 for the pair, and so on.
            val single = if (ins.size == 1) ins[0] else outs[0]
            val (a, b) = if (ins.size == 1) outs else ins
            val half = Rational.of(1, 2)
            r[Dot.of(a.momentum, b.momentum)] = (m2(single) - m2(a) - m2(b)) * half
            r[Dot.of(single.momentum, a.momentum)] = (m2(single) + m2(a) - m2(b)) * half
            r[Dot.of(single.momentum, b.momentum)] = (m2(single) + m2(b) - m2(a)) * half
        }
        r
    }

    /** What the substitution means, as LaTeX lines. */
    val description: List<String> get() {
        val out = ArrayList<String>()
        if (twoPoint) {
            out.add("p = ${MomNames.tex(externals[0].momentum)} = ${if (externals[0].incoming != externals[1].incoming) "" else "-"}${MomNames.tex(externals[1].momentum)}")
            return out
        }
        val masses = externals.joinToString(",\\ ") { x -> "${MomNames.tex(x.momentum)}^{2} = ${mass(x)?.let { "${it.tex}^{2}" } ?: "0"}" }
        out.add(masses)
        if (incoming.size == 2 && outgoing.size == 2) {
            val (p1, p2) = incoming.map { MomNames.tex(it.momentum) }
            val (p3, p4) = outgoing.map { MomNames.tex(it.momentum) }
            out.add("s = ($p1 + $p2)^{2},\\ t = ($p1 - $p3)^{2},\\ u = ($p1 - $p4)^{2}")
        }
        return out
    }

    /** Rewrites dot products of external momenta, including inside propagator denominators. */
    fun apply(e: Expr): Expr {
        var cur = e
        for ((dot, value) in rules) cur = cur.substitute(dot, value)
        return cur.mapAtoms { a, n ->
            if (a is Den) {
                val c = apply(a.content)
                if (c.isZero) null else atom(Den(c), n)
            } else null
        }
    }

    /** Σ m² of the externals, for s + t + u. */
    val massSum: Expr get() = externals.fold(Expr.ZERO) { acc, x -> acc + m2(x) }
}
