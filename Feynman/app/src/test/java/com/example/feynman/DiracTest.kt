package com.example.feynman

import com.example.feynman.physics.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiracTest {
    private val pool = IndexPool()
    private fun d(a: String, b: String) = atom(Dot.of(a, b))

    @Test fun traceOfTwoSlashes() {
        // tr(p̸ q̸) = 4 p·q
        assertEquals(d("p", "q") * CQ.of(4), trace(listOf(G.S("p"), G.S("q"))))
    }

    @Test fun traceOfFourSlashes() {
        // tr(a̸ b̸ c̸ d̸) = 4[(a·b)(c·d) − (a·c)(b·d) + (a·d)(b·c)]
        val t = trace(listOf(G.S("a"), G.S("b"), G.S("c"), G.S("e")))
        val expected = (d("a", "b") * d("c", "e") - d("a", "c") * d("b", "e") + d("a", "e") * d("b", "c")) * CQ.of(4)
        assertEquals(expected, t)
    }

    @Test fun contractedGammasInATrace() {
        // tr(γ^μ γ_μ) = 4d, tr(γ^μ p̸ γ_μ q̸) = −4(d − 2) p·q
        val mu = pool.fresh()
        assertEquals(atom(Dim) * CQ.of(4), trace(listOf(G.I(mu), G.I(mu))))
        val t = trace(listOf(G.I(mu), G.S("p"), G.I(mu), G.S("q")))
        val expected = (atom(Dim) - Expr.const(2)) * d("p", "q") * CQ.of(-4)
        assertEquals(expected, t)
    }

    @Test fun gammaFiveTraces() {
        val (m, n, r, s) = List(4) { pool.fresh() }
        assertTrue(trace(listOf(G.I(m), G.I(n), G.Five)).isZero)
        val t = trace(listOf(G.I(m), G.I(n), G.I(r), G.I(s), G.Five))
        assertEquals(levi(listOf(Slot.I(m), Slot.I(n), Slot.I(r), Slot.I(s))) * CQ.imag(-4), t)
        // ε^{μνρσ} ε_{μνρσ} = −24
        val e = levi(listOf(Slot.I(m), Slot.I(n), Slot.I(r), Slot.I(s)))
        assertEquals(Expr.const(-24), contract(e * e).substitute(Dim, Expr.const(4)))
    }

    @Test fun sixGammasWithFiveAgreesWithReordering() {
        // tr(a b c d e f γ5) computed by the reduction must be antisymmetric under swapping a, b
        // up to the 2(a·b) term: tr(a b X γ5) + tr(b a X γ5) = 2 a·b tr(X γ5).
        val g = listOf("a", "b", "c", "e", "f", "h").map { G.S(it) }
        val t1 = trace(g + G.Five)
        val t2 = trace(listOf(g[1], g[0]) + g.drop(2) + G.Five)
        val t3 = trace(g.drop(2) + G.Five) * d("a", "b") * CQ.of(2)
        assertEquals(t3, t1 + t2)
    }

    @Test fun sandwichIdentities() {
        // γ^μ a̸ γ_μ = −(d − 2) a̸ ; γ^μ a̸ b̸ γ_μ = 4 a·b − (4 − d) a̸ b̸
        val mu = pool.fresh()
        val one = contract(Expr(mapOf(TermKey(Mono.ONE, listOf(listOf(G.I(mu), G.S("a"), G.I(mu)))) to CQ.ONE)))
        assertEquals((atom(Dim) - Expr.const(2)) * CQ.of(-1) * gammas(G.S("a")), one)
        val two = contract(Expr(mapOf(TermKey(Mono.ONE, listOf(listOf(G.I(mu), G.S("a"), G.S("b"), G.I(mu)))) to CQ.ONE)))
        val ordered = two.mapTerms { k, c -> sum(normalOrder(k.chains[0]) { it.sortKey }.map { (cc, s) -> Expr(mapOf(TermKey(k.mono, listOf(s)) to c)) * cc }) }
        val expected = d("a", "b") * CQ.of(4) * diracOne - (Expr.const(4) - atom(Dim)) * gammas(G.S("a"), G.S("b"))
        assertEquals(expected, ordered)
    }
}
