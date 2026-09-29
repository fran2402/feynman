package com.example.feynman

import com.example.feynman.physics.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Two diagrams with a relative minus sign: e⁻e⁺ → e⁻e⁺. */
class BhabhaTest {
    private val c = RuleContext(Conventions(), massOf = { p -> if (p.isFermion) null else p.mass })

    // e⁻(1) top-left in, e⁺(2) bottom-left in, e⁻(3) top-right out, e⁺(4) bottom-right out.
    private val sChannel = Diagram(
        listOf(Point(1, 0f, 0f), Point(2, 0f, 2f), Point(3, 1f, 1f), Point(4, 2f, 1f), Point(5, 3f, 0f), Point(6, 3f, 2f)),
        listOf(Line(1, 1, 3, "e"), Line(2, 3, 2, "e"), Line(3, 3, 4, "A"), Line(4, 4, 5, "e"), Line(5, 6, 4, "e")),
    )
    private val tChannel = Diagram(
        listOf(Point(1, 0f, 0f), Point(2, 0f, 2f), Point(3, 1.5f, 0.5f), Point(4, 1.5f, 1.5f), Point(5, 3f, 0f), Point(6, 3f, 2f)),
        listOf(Line(1, 1, 3, "e"), Line(2, 3, 5, "e"), Line(3, 3, 4, "A"), Line(4, 4, 2, "e"), Line(5, 6, 4, "e")),
    )

    @Test fun bhabhaMassless() {
        val a = Amplitude.build(sChannel, c)
        val b = Amplitude.build(tChannel, c)
        assertEquals(-a.fermionSign, b.fermionSign)
        val kin = Kinematics(a.externals, c.massOf)
        val sq = Squared.compute(listOf(a, b), kin)
        val s = sym(Mandelstam.s); val t = sym(Mandelstam.t); val u = sym(Mandelstam.u)
        // Den atoms → s or t.
        val got = sq.result.mapAtoms { at, n -> if (at is Den) (if (Tex.of(at.content) == "s") sym(Mandelstam.s, n) else sym(Mandelstam.t, n)) else null }
        // 2e⁴[(s² + u²)/t² + 2u²/(st) + (t² + u²)/s²]
        val expected = sym(Couplings.e, 4) * CQ.of(2) * ((s * s + u * u) * sym(Mandelstam.t, -2) + u * u * sym(Mandelstam.s, -1) * sym(Mandelstam.t, -1) * CQ.of(2) + (t * t + u * u) * sym(Mandelstam.s, -2))
        assertEquals(Tex.of(expected), Tex.of(got))
    }
}
