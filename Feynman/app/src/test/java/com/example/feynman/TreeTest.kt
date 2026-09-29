package com.example.feynman

import com.example.feynman.physics.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Textbook cross sections, built from drawn diagrams. */
class TreeTest {
    private fun ctx(massless: Boolean = false, conv: Conventions = Conventions()) =
        RuleContext(conv, massOf = { p -> if (massless && p.isFermion) null else p.mass })

    /** e⁻(1) e⁺(2) → μ⁻(3) μ⁺(4) through a photon. */
    private val eeToMuMu = Diagram(
        listOf(Point(1, 0f, 0f), Point(2, 0f, 2f), Point(3, 1f, 1f), Point(4, 2f, 1f), Point(5, 3f, 0f), Point(6, 3f, 2f)),
        listOf(Line(1, 1, 3, "e"), Line(2, 3, 2, "e"), Line(3, 3, 4, "A"), Line(4, 4, 5, "mu"), Line(5, 6, 4, "mu")),
    )

    @Test fun eeToMuMuMassless() {
        val c = ctx(massless = true)
        val amp = Amplitude.build(eeToMuMu, c)
        assertEquals(emptyList<String>(), amp.issues.map { it.message })
        val kin = Kinematics(amp.externals, c.massOf)
        val sq = Squared.compute(listOf(amp), kin)
        val s = sym(Mandelstam.s); val t = sym(Mandelstam.t); val u = sym(Mandelstam.u); val e = sym(Couplings.e)
        // 2e⁴(t² + u²)/s²
        val expected = e.pow(4) * (t * t + u * u) * sym(Mandelstam.s, -2) * CQ.of(2)
        val got = sq.result.mapAtoms { a, n -> if (a is Den) sym(Mandelstam.s, n) else null }
        assertEquals(Tex.of(expected), Tex.of(got))
    }

    @Test fun eeToMuMuMassive() {
        val c = ctx()
        val amp = Amplitude.build(eeToMuMu, c)
        val kin = Kinematics(amp.externals, c.massOf)
        val sq = Squared.compute(listOf(amp), kin)
        // (1/4)Σ|M|² = 8e⁴/s² [(p1·p3)(p2·p4) + (p1·p4)(p2·p3) + m_μ²(p1·p2) + m_e²(p3·p4) + 2m_e²m_μ²]
        val d = { a: String, b: String -> atom(Dot.of(a, b)) }
        val me2 = sym(SM.electron.mass!!, 2); val mm2 = sym(SM.muon.mass!!, 2)
        val bracket = d("p1", "p3") * d("p2", "p4") + d("p1", "p4") * d("p2", "p3") + mm2 * d("p1", "p2") + me2 * d("p3", "p4") + me2 * mm2 * CQ.of(2)
        val expected = kin.apply(bracket * sym(Couplings.e, 4) * CQ.of(8))
        val got = sq.result.mapAtoms { a, n -> if (a is Den) sym(Mandelstam.s, n + 2).let { if (n == -2) Expr.ONE else it } else null }
        assertEquals(Tex.of(expected), Tex.of(got))
    }
}
