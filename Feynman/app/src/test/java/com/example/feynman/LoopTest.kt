package com.example.feynman

import com.example.feynman.physics.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** One-loop results against Peskin & Schroeder, ch. 7 and 10. */
class LoopTest {
    private val peskin = ConventionPresets.all.first { it.name.startsWith("Peskin") }.conv
    private fun ctx() = RuleContext(peskin)

    /** γ → e⁻e⁺ loop → γ. */
    private val vacuumPolarization = Diagram(
        listOf(Point(1, 0f, 1f), Point(2, 1f, 1f), Point(3, 2f, 1f), Point(4, 3f, 1f)),
        listOf(Line(1, 1, 2, "A"), Line(2, 2, 3, "e", 0.4f), Line(3, 3, 2, "e", 0.4f), Line(4, 3, 4, "A")),
    )

    /** e⁻ → e⁻ γ loop → e⁻. */
    private val selfEnergy = Diagram(
        listOf(Point(1, 0f, 1f), Point(2, 1f, 1f), Point(3, 2f, 1f), Point(4, 3f, 1f)),
        listOf(Line(1, 1, 2, "e"), Line(2, 2, 3, "e"), Line(3, 2, 3, "A", 0.6f), Line(4, 3, 4, "e")),
    )

    private fun result(d: Diagram): LoopResult {
        val c = ctx()
        val amp = Amplitude.build(d, c)
        assertEquals(emptyList<String>(), amp.issues.map { it.message })
        return Loop.evaluate(amp, Kinematics(amp.externals, c.massOf))!!
    }

    @Test fun vacuumPolarizationIsTransverse() {
        val r = result(vacuumPolarization)
        println("VP pole: " + Tex.of(r.pole))
        println("VP finite: " + Tex.of(r.finiteIntegrand))
        // Pole: −(4/3) e² (p² ε₁·ε₂* − p·ε₁ p·ε₂*)
        val e2 = sym(Couplings.e, 2)
        val p2 = sym(Mandelstam.p2)
        val expected = e2 * Rational.of(-4, 3) * (p2 * atom(Dot.of("eps1", "eps2*")) - atom(Dot.of("p1", "eps1")) * atom(Dot.of("p1", "eps2*")))
        assertEquals(Tex.of(expected), Tex.of(r.pole))
    }

    @Test fun electronSelfEnergyPole() {
        val r = result(selfEnergy)
        println("SE pole: " + Tex.of(r.pole, chainOpen = { "\\bar u(p_2)" }, chainClose = { "u(p_1)" }))
        println("SE finite: " + Tex.of(r.finiteIntegrand))
        // −iΣ₂ with Σ₂ pole = (e²/16π²)(4m − p̸)/ε: iM pole coefficient = −e²(4m − p̸) → e²p̸ − 4e²m.
        val e2 = sym(Couplings.e, 2)
        val m = sym(SM.electron.mass!!)
        val expected = e2 * gammas(G.S("p1")) - e2 * m * diracOne * CQ.of(4)
        assertEquals(Tex.of(expected), Tex.of(r.pole))
    }
}
