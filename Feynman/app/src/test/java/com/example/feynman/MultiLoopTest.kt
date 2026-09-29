package com.example.feynman

import com.example.feynman.physics.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Two- and three-loop integrals by sector decomposition, against exact results. */
class MultiLoopTest {
    private fun gam(n: Int, c: Double) = Series.gamma(n, c, 8)
    private fun Series.x(o: Series) = times(o, 8)
    private fun Series.over(o: Series) = times(o.inverse(10), 8)

    private fun diagram(build: MutableList<Point>.(MutableList<Line>) -> Unit): Diagram {
        val pts = ArrayList<Point>(); val lines = ArrayList<Line>()
        pts.build(lines)
        return Diagram(pts, lines)
    }

    /** The coefficients c_k of (i/16π²)^L for a drawn diagram. */
    private fun coefficients(d: Diagram, o: SolveOptions, values: Map<String, Double>, acc: MultiLoop.Accuracy = MultiLoop.Accuracy(8192, 6)): List<MultiLoop.Row> {
        val ctx = Solver.context(o)
        val amp = Amplitude.build(d, ctx)
        assertTrue(amp.issues.map { it.message }.toString(), amp.ok)
        val kin = Kinematics(amp.externals, ctx.massOf)
        val r = MultiLoop.prepare(amp, kin)!!
        return MultiLoop.evaluate(r, values, 1.0, { "" }, { "" }, acc)
    }

    private fun check(row: MultiLoop.Row, expected: Series, tol: Double) {
        for ((k, c) in row.coefficients) {
            val want = expected[k]
            val err = (c - want).abs
            assertTrue("ε^$k: $c vs $want (±${row.errors})", err < tol * maxOf(1.0, want.abs))
        }
    }

    private val base = Evaluate.defaults + mapOf("m_phi" to 0.0, "lambda" to 1.0)

    /** φ⁴ two-point sunset: in → v1, three lines v1–v2, v2 → out. */
    private val sunset = diagram { lines ->
        add(Point(1, 0f, 100f, Io.In)); add(Point(2, 100f, 100f)); add(Point(3, 220f, 100f)); add(Point(4, 320f, 100f, Io.Out))
        lines.add(Line(10, 1, 2, "phi4")); lines.add(Line(11, 2, 3, "phi4", 0.5f)); lines.add(Line(12, 2, 3, "phi4")); lines.add(Line(13, 2, 3, "phi4", -0.5f)); lines.add(Line(14, 3, 4, "phi4"))
    }

    @Test fun masslessSunset() {
        // iℳ = (i/16π²)² c: c = −(iλ²/6) e^{2γε} J, J = Γ(−1+2ε)Γ(1−ε)³/Γ(3−3ε) (−p²)^{1−2ε}.
        val o = SolveOptions(theory = Theory.Phi4)
        val j = gam(-1, 2.0).x(gam(1, -1.0)).x(gam(1, -1.0)).x(gam(1, -1.0)).over(gam(3, -3.0))
        val pre = Series.expOf(Complex(2 * 0.5772156649015329), 8).scale(Complex(0.0, -1.0 / 6))
        // Spacelike p² = −1: (−p²)^{1−2ε} = 1.
        val rows = coefficients(sunset, o, base + ("psq" to -1.0))
        check(rows.single(), j.x(pre), 3e-3)
        // Timelike p² = 1: (−p² − i0)^{1−2ε} = −e^{2πiε}.
        val rows2 = coefficients(sunset, o, base + ("psq" to 1.0))
        val phase = Series.expOf(Complex(0.0, 2 * Math.PI), 8).scale(-1.0)
        check(rows2.single(), j.x(pre).x(phase), 3e-3)
    }

    @Test fun threeBubbleChain() {
        // φ⁴ s-channel chain of three bubbles: c = −(λ⁴/8) e^{3γε} G(1,1)³ (−s)^{−3ε}.
        val d = diagram { lines ->
            add(Point(1, 0f, 0f, Io.In)); add(Point(2, 0f, 200f, Io.In))
            add(Point(3, 60f, 100f)); add(Point(4, 140f, 100f)); add(Point(5, 220f, 100f)); add(Point(6, 300f, 100f))
            add(Point(7, 360f, 0f, Io.Out)); add(Point(8, 360f, 200f, Io.Out))
            lines.add(Line(10, 1, 3, "phi4")); lines.add(Line(11, 2, 3, "phi4"))
            lines.add(Line(12, 3, 4, "phi4", 0.5f)); lines.add(Line(13, 3, 4, "phi4", -0.5f))
            lines.add(Line(14, 4, 5, "phi4", 0.5f)); lines.add(Line(15, 4, 5, "phi4", -0.5f))
            lines.add(Line(16, 5, 6, "phi4", 0.5f)); lines.add(Line(17, 5, 6, "phi4", -0.5f))
            lines.add(Line(18, 6, 7, "phi4")); lines.add(Line(19, 6, 8, "phi4"))
        }
        val g11 = gam(0, 1.0).x(gam(1, -1.0)).x(gam(1, -1.0)).over(gam(2, -2.0))
        val s = 2.0
        val pre = Series.expOf(Complex(3 * 0.5772156649015329), 8).x(Sectors.powSeries(Sectors.logOf(Complex(-s)), 0.0, -3.0, 8)).scale(-1.0 / 8)
        val rows = coefficients(d, SolveOptions(theory = Theory.Phi4), base + ("s" to s), MultiLoop.Accuracy(4096, 4))
        check(rows.single(), g11.x(g11).x(g11).x(pre), 3e-3)
    }

    @Test fun sunsetWithNumerator() {
        // ∫∫ (k₁·k₂)/((−k₁²)(−k₂²)(−(p−k₁−k₂)²)) at p² = −1: ½G₁(1,1)[−G(1,ε) + G(1,ε−1)],
        // and the engine's (−1)³ from Π1/D = −Π1/(−D), and e^{2γε}.
        val amp = Amplitude.build(sunset, Solver.context(SolveOptions(theory = Theory.Phi4)))
        val kin = Kinematics(amp.externals, { null })
        fun den(q: Mom) = (propagatorDen(q, null).terms.keys.single().mono.factors.single().first as Den)
        val p = amp.externals[0].momentum
        val dens = listOf(den(mom("k1")), den(mom("k2")), den(mom(p) - mom("k1") - mom("k2")))
        val piece = MultiLoop.piece(dens, atom(Dot.of("k1", "k2")), listOf("k1", "k2"), kin)!!
        val r = MultiLoop.Result(listOf(piece), kin, emptyList(), emptyList(), 2)
        val row = MultiLoop.evaluate(r, base + ("psq" to -1.0), 1.0, { "" }, { "" }, MultiLoop.Accuracy(8192, 6)).single()
        val g1 = gam(0, 1.0).x(gam(2, -1.0)).x(gam(1, -1.0)).over(gam(3, -2.0))
        val gA = gam(-1, 2.0).x(gam(1, -1.0)).x(gam(2, -2.0)).over(gam(0, 1.0).x(gam(3, -3.0)))
        val gB = gam(-2, 2.0).x(gam(1, -1.0)).x(gam(3, -2.0)).over(gam(-1, 1.0).x(gam(4, -3.0)))
        val j = g1.x(gB + gA.scale(-1.0)).scale(0.5)
        val expected = j.x(Series.expOf(Complex(2 * 0.5772156649015329), 8)).scale(-1.0)
        check(row, expected, 3e-3)
    }

    @Test fun oneLoopAgreesWithTheOneLoopCode() {
        val v = Evaluate.defaults + ("psq" to -2500.0)
        for (name in listOf("Vacuum polarization", "Electron self-energy", "Higgs self-energy, top loop", "Higgs bubble")) {
            val d = Templates.all.first { it.name == name }.diagram
            val o = SolveOptions()
            val s = Solver.solve(d, o)
            val one = Evaluate.loop(s.loop!!, v, 91.0, { "" }, { "" })!!.associateBy { it.structureTex }
            val ctx = Solver.context(o)
            val r = MultiLoop.prepare(s.amplitude!!, Kinematics(s.amplitude!!.externals, ctx.massOf))!!
            val rows = MultiLoop.evaluate(r, v, 91.0, { "" }, { "" }, MultiLoop.Accuracy(8192, 6))
            for (row in rows) {
                val ref = one[row.structureTex]
                val pole = row.coefficients.first { it.first == -1 }.second
                val fin = row.coefficients.first { it.first == 0 }.second
                val refPole = ref?.pole ?: Complex.ZERO
                val refFin = ref?.finite ?: Complex.ZERO
                val scale = maxOf(1e-6, refFin.abs, refPole.abs)
                assertTrue("$name ${row.structureTex}: pole $pole vs $refPole", (pole - refPole).abs < 1e-4 * scale + 1e-9)
                assertTrue("$name ${row.structureTex}: finite $fin vs $refFin", (fin - refFin).abs < 2e-3 * scale)
            }
        }
    }

    @Test fun masslessBanana() {
        // Three loops, four propagators: G(1,1) G(1,ε) G(1,2ε−1) (−p²)^{2−3ε}, times (−1)⁴ e^{3γε}.
        val amp = Amplitude.build(sunset, Solver.context(SolveOptions(theory = Theory.Phi4)))
        val kin = Kinematics(amp.externals, { null })
        fun den(q: Mom) = (propagatorDen(q, null).terms.keys.single().mono.factors.single().first as Den)
        val p = amp.externals[0].momentum
        val dens = listOf(den(mom("k1")), den(mom("k2")), den(mom("k3")), den(mom(p) - mom("k1") - mom("k2") - mom("k3")))
        val piece = MultiLoop.piece(dens, Expr.ONE, listOf("k1", "k2", "k3"), kin)!!
        val row = MultiLoop.evaluate(MultiLoop.Result(listOf(piece), kin, emptyList(), emptyList(), 3), base + ("psq" to -1.0), 1.0, { "" }, { "" }, MultiLoop.Accuracy(8192, 5)).single()
        val g11 = gam(0, 1.0).x(gam(1, -1.0)).x(gam(1, -1.0)).over(gam(2, -2.0))
        val g1e = gam(-1, 2.0).x(gam(1, -1.0)).x(gam(2, -2.0)).over(gam(0, 1.0).x(gam(3, -3.0)))
        val g12 = gam(-2, 3.0).x(gam(1, -1.0)).x(gam(3, -3.0)).over(gam(-1, 2.0).x(gam(4, -4.0)))
        check(row, g11.x(g1e).x(g12).x(Series.expOf(Complex(3 * 0.5772156649015329), 8)), 3e-3)
    }

    private fun chain(k: Int) = diagram { lines ->
        add(Point(1, 0f, 0f, Io.In)); add(Point(2, 0f, 200f, Io.In))
        for (i in 0..k) add(Point(3 + i, 60f + 80f * i, 100f))
        add(Point(20, 400f, 0f, Io.Out)); add(Point(21, 400f, 200f, Io.Out))
        lines.add(Line(30, 1, 3, "phi4")); lines.add(Line(31, 2, 3, "phi4"))
        for (i in 0 until k) { lines.add(Line(40 + 2 * i, 3 + i, 4 + i, "phi4", 0.5f)); lines.add(Line(41 + 2 * i, 3 + i, 4 + i, "phi4", -0.5f)) }
        lines.add(Line(60, 3 + k, 20, "phi4")); lines.add(Line(61, 3 + k, 21, "phi4"))
    }

    @Test fun massiveDoubleBubble() {
        // With m = 1 and 0 < s < 4m²: c₂ = i c₁² (λ = 1), so the double bubble's poles follow
        // from the single bubble's pole a and finite part b: ia²/ε² + 2iab/ε.
        val v = base + mapOf("m_phi" to 1.0, "s" to 1.5)
        val o = SolveOptions(theory = Theory.Phi4)
        val one = coefficients(chain(1), o, v).single()
        val two = coefficients(chain(2), o, v).single()
        val a = one.coefficients.first { it.first == -1 }.second
        val b = one.coefficients.first { it.first == 0 }.second
        val i = Complex(0.0, 1.0)
        val want2 = i * a * a; val want1 = i * a * b * 2.0
        val got2 = two.coefficients.first { it.first == -2 }.second
        val got1 = two.coefficients.first { it.first == -1 }.second
        assertTrue("$got2 vs $want2", (got2 - want2).abs < 1e-3 * want2.abs)
        assertTrue("$got1 vs $want1", (got1 - want1).abs < 2e-3 * want1.abs)
        // Above threshold (s > 4m²) F changes sign: no numbers, and a note says why.
        val above = coefficients(chain(2), o, v + ("s" to 10.0)).single()
        assertTrue(above.note ?: "", above.note?.contains("threshold") == true)
    }
}
