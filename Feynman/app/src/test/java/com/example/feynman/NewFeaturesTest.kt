package com.example.feynman

import com.example.feynman.physics.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class GaugeTest {
    private val eeMuMu = Templates.all.first { it.name == "e⁻e⁺ → μ⁻μ⁺" }.diagram

    @Test fun xiDropsOutOfEeToMuMu() {
        fun result(g: Gauge): String {
            val s = Solver.solve(eeMuMu, SolveOptions(gauge = g))
            assertTrue(s.issues.map { it.message }.toString(), s.issues.isEmpty())
            return Tex.of(s.squared!!.result.mapAtoms { a, n -> if (a is Den) sym(Mandelstam.s, n) else null })
        }
        val feynman = result(Gauge.Feynman)
        assertEquals(feynman, result(Gauge.General))
        assertEquals(feynman, result(Gauge.Landau))
    }

    @Test fun electronSelfEnergyInGeneralGauge() {
        // Pole of iℳ: e² ξ p̸ − (3 + ξ) e² m (Feynman gauge: e² p̸ − 4e² m).
        val d = Templates.all.first { it.name == "Electron self-energy" }.diagram
        val peskin = ConventionPresets.all.first { it.name.startsWith("Peskin") }.conv
        val s = Solver.solve(d, SolveOptions(conv = peskin, gauge = Gauge.General))
        val e2 = sym(Couplings.e, 2); val m = sym(SM.electron.mass!!); val xi = sym(Rules.xiSym)
        val expected = e2 * xi * gammas(G.S("p1")) - e2 * m * (xi + Expr.const(3)) * diracOne
        assertEquals(Tex.of(expected), Tex.of(s.loop!!.pole))
        assertTrue(s.loop!!.pieces.size > 1)
    }
}

class ObservablesTest {
    private val v = Evaluate.defaults

    @Test fun muonPairCrossSection() {
        // σ = 4πα²/(3s) for massless fermions through a photon.
        val d = Templates.all.first { it.name == "e⁻e⁺ → μ⁻μ⁺" }.diagram
        val s = Solver.solve(d, SolveOptions(masslessFermions = true))
        val kin = Kinematics(s.amplitude!!.externals, Solver.context(SolveOptions(masslessFermions = true)).massOf)
        val sigma = Observables.sigma(s.squared!!, kin, v, 10.0)!!
        val alpha = v["e"]!! * v["e"]!! / (4 * PI)
        val expected = 4 * PI * alpha * alpha / (3 * 100.0) * Observables.PB
        assertTrue("$sigma vs $expected", abs(sigma / expected - 1) < 1e-6)
    }

    @Test fun zToElectronsWidth() {
        val d = Templates.all.first { it.name == "Z → e⁻e⁺" }.diagram
        val o = SolveOptions(masslessFermions = true)
        val s = Solver.solve(d, o)
        val gamma = Observables.width(s.squared!!, Kinematics(s.amplitude!!.externals, Solver.context(o).massOf), v)!!
        val g = v["g"]!!; val cW = v["cW"]!!; val sW2 = v["sW"]!! * v["sW"]!!; val mZ = v["mZ"]!!
        val gv = -0.25 + sW2; val ga = -0.25
        val expected = g * g / (cW * cW) * mZ / (12 * PI) * (gv * gv + ga * ga)
        assertTrue("$gamma vs $expected", abs(gamma / expected - 1) < 1e-9)
    }

    @Test fun higgsToBottomWidth() {
        val d = Templates.all.first { it.name == "h → b b̄" }.diagram
        val s = Solver.solve(d, SolveOptions())
        val gamma = Observables.width(s.squared!!, Kinematics(s.amplitude!!.externals, Solver.context(SolveOptions()).massOf), v)!!
        val g = v["g"]!!; val mb = v["m_b"]!!; val mh = v["mh"]!!; val mW = v["mW"]!!
        val beta = sqrt(1 - 4 * mb * mb / (mh * mh))
        val expected = 3 * g * g * mb * mb * mh / (32 * PI * mW * mW) * beta * beta * beta
        assertTrue("$gamma vs $expected", abs(gamma / expected - 1) < 1e-9)
    }

    @Test fun muonLifetime() {
        // Γ = G_F² m_μ⁵/(192π³), G_F/√2 = g²/(8 m_W²), up to (m_e/m_μ)² and (m_μ/m_W)² corrections.
        val d = Templates.all.first { it.name == "Muon decay" }.diagram
        val s = Solver.solve(d, SolveOptions())
        val gamma = Observables.width(s.squared!!, Kinematics(s.amplitude!!.externals, Solver.context(SolveOptions()).massOf), v)!!
        val gf = sqrt(2.0) * v["g"]!! * v["g"]!! / (8 * v["mW"]!! * v["mW"]!!)
        val mmu = v["m_mu"]!!
        val expected = gf * gf * Math.pow(mmu, 5.0) / (192 * PI * PI * PI)
        assertTrue("$gamma vs $expected", abs(gamma / expected - 1) < 2e-3)
    }
}

class GenerateTest {
    private fun count(legs: List<Generate.Leg>, theory: Theory, loops: Int = 0): Int {
        val ctx = Solver.context(SolveOptions(theory = theory))
        val r = Generate.generate(legs, ctx, Generate.Options(loops = loops))
        // Every generated diagram is valid.
        for (d in r.diagrams) {
            val a = Amplitude.build(d, ctx)
            assertTrue(a.issues.map { it.message }.toString(), a.ok)
        }
        return r.diagrams.size
    }

    private val eIn = Generate.Leg(SM.electron, true, false)
    private val ePlusIn = Generate.Leg(SM.electron, true, true)
    private val eOut = Generate.Leg(SM.electron, false, false)
    private val ePlusOut = Generate.Leg(SM.electron, false, true)
    private val muOut = Generate.Leg(SM.muon, false, false)
    private val muPlusOut = Generate.Leg(SM.muon, false, true)
    private val gammaOut = Generate.Leg(SM.photon, false, false)
    private val gammaIn = Generate.Leg(SM.photon, true, false)

    @Test fun qedTrees() {
        assertEquals(1, count(listOf(eIn, ePlusIn, muOut, muPlusOut), Theory.QED))
        assertEquals(2, count(listOf(eIn, ePlusIn, eOut, ePlusOut), Theory.QED)) // Bhabha: s and t
        assertEquals(2, count(listOf(eIn, gammaIn, eOut, gammaOut), Theory.QED)) // Compton
        assertEquals(2, count(listOf(eIn, ePlusIn, gammaOut, Generate.Leg(SM.photon, false, false)), Theory.QED)) // annihilation: t and u
    }

    @Test fun standardModelTrees() {
        // γ, Z, h and φ_Z in the s-channel (the last two need fermion masses).
        assertEquals(4, count(listOf(eIn, ePlusIn, muOut, muPlusOut), Theory.SM))
    }

    @Test fun qedPhotonSelfEnergy() {
        // Loops of e, μ and τ.
        assertEquals(3, count(listOf(gammaIn, gammaOut), Theory.QED, loops = 1))
    }

    @Test fun qedElectronSelfEnergyAndVertex() {
        assertEquals(1, count(listOf(eIn, eOut), Theory.QED, loops = 1))
        assertEquals(1, count(listOf(eIn, ePlusIn, gammaOut), Theory.QED, loops = 1))
    }
}

class PassarinoTest {
    @Test fun b0ClosedFormMatchesQuadrature() {
        for ((p2, m1, m2) in listOf(Triple(50.0, 1.0, 4.0), Triple(500.0, 3.0, 3.0), Triple(-20.0, 0.0, 2.0), Triple(10.0, 0.5, 0.0))) {
            val closed = Passarino.b0(p2, m1, m2, 1.0)
            val n = 200000
            var re = 0.0; var im = 0.0
            for (i in 0 until n) {
                val x = (i + 0.5) / n
                val d = x * m1 + (1 - x) * m2 - x * (1 - x) * p2
                re += -kotlin.math.ln(abs(d)) / n
                if (d < 0) im += PI / n
            }
            assertTrue("B0($p2,$m1,$m2) = $closed vs $re + ${im}i", abs(closed.re - re) < 1e-4 && abs(closed.im - im) < 1e-4)
        }
    }

    @Test fun xkLnQExactForPolynomials() {
        // ∫₀¹ x² ln(x² + 1) dx = (ln 2)/3 − 2/9 + π/6 − 1/3·... checked against quadrature.
        val (gx, gw) = Quadrature.gauss(64)
        var q = 0.0
        for (i in gx.indices) q += gw[i] * gx[i] * gx[i] * kotlin.math.ln(gx[i] * gx[i] + 1)
        assertTrue(abs(Passarino.intXkLnQ(2, 1.0, 0.0, 1.0).re - q) < 1e-12)
    }

    @Test fun c0InnerAnalyticMatchesQuadrature() {
        // A triangle below threshold: Δ = x₁m₁² + x₂m₂² + x₃m₃² − x₁x₂p² with x₃ = 1 − x₁ − x₂.
        val delta = { x1: Double, x2: Double -> x1 * 4.0 + x2 * 9.0 + (1 - x1 - x2) * 1.0 - x1 * x2 * 3.0 }
        val c0 = Passarino.c0(delta)
        val num = Quadrature.simplex(2, 32, 4) { x -> Complex(-1.0 / delta(x[0], x[1])) }
        assertTrue("$c0 vs $num", abs(c0.re - num.re) < 1e-9 && abs(c0.im) < 1e-9)
    }
}

class RenormTest {
    @Test fun qedWardIdentity() {
        val peskin = ConventionPresets.all.first { it.name.startsWith("Peskin") }.conv
        val vertex = Templates.all.first { it.name == "QED vertex correction" }.diagram
        val self = Templates.all.first { it.name == "Electron self-energy" }.diagram.copy(name = "SE")
        val s = Solver.solve(vertex, SolveOptions(conv = peskin), listOf(self))
        val renorm = s.steps.first { it.title == "Renormalization" }
        val texts = renorm.blocks.filterIsInstance<Block.Text>().map { it.text }
        assertTrue(texts.toString(), texts.any { it.contains("Ward identity holds") })
        val maths = renorm.blocks.filterIsInstance<Block.Math>().map { it.tex }
        assertTrue(maths.toString(), maths.any { it.startsWith("\\delta_1 = -e^{2}\\,\\frac{1}{16\\pi^{2}\\bar\\epsilon}") })
    }

    @Test fun photonBetaFunction() {
        val s = Solver.solve(Templates.all.first { it.name == "Vacuum polarization" }.diagram, SolveOptions())
        val maths = s.steps.first { it.title == "Renormalization" }.blocks.filterIsInstance<Block.Math>().map { it.tex }
        // δZ₃ = −(4/3)e²/K and β(e) ⊃ (4/3) e³/(16π²) = e³/(12π²).
        assertTrue(maths.toString(), maths.any { it.contains("\\beta(e) \\supset \\frac{4}{3}\\,\\frac{e^{3}}{16\\pi^{2}}") })
    }
}

class ModelsTest {
    @Test fun twoHdmScalesHiggsToBottom() {
        val d = Templates.all.first { it.name == "h → b b̄" }.diagram
        val v = Evaluate.defaults
        fun width(t: Theory): Double {
            val s = Solver.solve(d, SolveOptions(theory = t))
            return Observables.width(s.squared!!, Kinematics(s.amplitude!!.externals, Solver.context(SolveOptions(theory = t)).massOf), v)!!
        }
        val ratio = width(Theory.TwoHDM) / width(Theory.SM)
        val xi = -v["salpha"]!! / v["cbeta"]!!
        assertTrue("$ratio vs ${xi * xi}", abs(ratio - xi * xi) < 1e-9)
    }

    @Test fun phi4Bubble() {
        // φφ → φφ at one loop through two quartic vertices: pole (i/16π²ε̄)·(λ²/2)·(−i)²… = 3 channels; one here.
        val d = Diagram(
            listOf(Point(1, 0f, 0f), Point(2, 0f, 100f), Point(3, 100f, 50f), Point(4, 200f, 50f), Point(5, 300f, 0f), Point(6, 300f, 100f)),
            listOf(Line(1, 1, 3, "phi4"), Line(2, 2, 3, "phi4"), Line(3, 3, 4, "phi4", 0.45f), Line(4, 3, 4, "phi4", -0.45f), Line(5, 4, 5, "phi4"), Line(6, 4, 6, "phi4")),
        )
        val s = Solver.solve(d, SolveOptions(theory = Theory.Phi4))
        assertTrue(s.issues.map { it.message }.toString(), s.issues.isEmpty())
        // (−iλ)² × ½ × i² × (−1)^2 i … the pole is λ²/2.
        assertEquals(Tex.of(sym(BSM.lambda, 2) * Rational.of(1, 2)), Tex.of(s.loop!!.pole))
    }
}

class ColorRegressionTest {
    @Test fun drellYanHasOneOverNc() {
        // u ū → μ⁻μ⁺ through a photon: σ = 4πα² Q_u² / (9s) (the 1/N_c from averaging colors).
        val d = Diagram(
            listOf(Point(1, 0f, 0f), Point(2, 0f, 180f), Point(3, 90f, 90f), Point(4, 230f, 90f), Point(5, 320f, 0f), Point(6, 320f, 180f)),
            listOf(Line(1, 1, 3, "u"), Line(2, 3, 2, "u"), Line(3, 3, 4, "A"), Line(4, 4, 5, "mu"), Line(5, 6, 4, "mu")),
        )
        val o = SolveOptions(masslessFermions = true)
        val s = Solver.solve(d, o)
        val v = Evaluate.defaults
        val sigma = Observables.sigma(s.squared!!, Kinematics(s.amplitude!!.externals, Solver.context(o).massOf), v, 10.0)!!
        val alpha = v["e"]!! * v["e"]!! / (4 * PI)
        val expected = 4 * PI * alpha * alpha * (4.0 / 9) / (9 * 100.0) * Observables.PB
        assertTrue("$sigma vs $expected", abs(sigma / expected - 1) < 1e-6)
    }
}
