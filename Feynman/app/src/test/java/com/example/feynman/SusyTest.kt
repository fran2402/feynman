package com.example.feynman

import com.example.feynman.physics.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Supersymmetric theories: Majorana fermions, fermion flow, and the MSSM's couplings. */
class SusyTest {
    private val base = Evaluate.defaults

    private fun leg(p: Particle, incoming: Boolean, anti: Boolean = false) = Generate.Leg(p, incoming, anti)

    /** Every tree diagram of the process, solved together. */
    private fun squared(legs: List<Generate.Leg>, o: SolveOptions, keep: (Diagram) -> Boolean = { true }): Pair<SquaredResult, Kinematics> {
        val ctx = Solver.context(o)
        val ds = Generate.generate(legs, ctx, Generate.Options(loops = 0, limit = 100)).diagrams.filter(keep)
        assertTrue("no diagrams", ds.isNotEmpty())
        val s = Solver.solve(ds.first(), o, ds.drop(1))
        assertTrue(s.issues.map { it.message }.toString(), s.issues.isEmpty())
        return s.squared!! to Kinematics(s.amplitude!!.externals, ctx.massOf)
    }

    /** |ℳ|² for 2 → 2 at √s and cos θ (centre-of-mass frame). */
    private fun msq(sq: SquaredResult, kin: Kinematics, v: Map<String, Double>, rootS: Double, cosT: Double): Double {
        fun m(x: External) = kin.mass(x)?.let { abs(v[it.name]!!) } ?: 0.0
        val (a, b) = kin.incoming; val (c, d) = kin.outgoing
        val s = rootS * rootS
        fun p(m1: Double, m2: Double) = sqrt(Observables.kallen(s, m1 * m1, m2 * m2)) / (2 * rootS)
        val pi = p(m(a), m(b)); val pf = p(m(c), m(d))
        val sin = sqrt(1 - cosT * cosT)
        val mom = mapOf(
            a.momentum to doubleArrayOf(sqrt(pi * pi + m(a) * m(a)), 0.0, 0.0, pi),
            b.momentum to doubleArrayOf(sqrt(pi * pi + m(b) * m(b)), 0.0, 0.0, -pi),
            c.momentum to doubleArrayOf(sqrt(pf * pf + m(c) * m(c)), pf * sin, 0.0, pf * cosT),
            d.momentum to doubleArrayOf(sqrt(pf * pf + m(d) * m(d)), -pf * sin, 0.0, -pf * cosT),
        )
        fun dot(x: DoubleArray, y: DoubleArray) = x[0] * y[0] - x[1] * y[1] - x[2] * y[2] - x[3] * y[3]
        return Evaluate.scalar(sq.dots, v, dots = { dd -> dot(mom[dd.a]!!, mom[dd.b]!!) })!!.re
    }

    private fun tu(kin: Kinematics, v: Map<String, Double>, rootS: Double, cosT: Double, mOut: Double): Pair<Double, Double> {
        val s = rootS * rootS
        val p = sqrt(s / 4 - mOut * mOut)
        val e = rootS / 2
        // Massless incoming along z, outgoing (mass mOut) at θ.
        val t = mOut * mOut - 2 * e * (e - p * cosT)
        val u = mOut * mOut - 2 * e * (e + p * cosT)
        return t to u
    }

    @Test fun photinoPairsArePWave() {
        // e⁺e⁻ → γ̃γ̃ through ẽ_L and ẽ_R in the t and u channels: the Majorana pair is made
        // in a P wave, so |ℳ|² vanishes at threshold; and it has the known shape
        // Σ_{L,R} [(m²−t)²/(t−m̃²)² + (m²−u)²/(u−m̃²)² − 2m²s/((t−m̃²)(u−m̃²))].
        val o = SolveOptions(theory = Theory.SQED, masslessFermions = true)
        val (sq, kin) = squared(listOf(leg(SM.electron, true), leg(SM.electron, true, true), leg(SUSY.photino, false), leg(SUSY.photino, false)), o)
        val m = base["m_photino"]!!
        val near = msq(sq, kin, base, 2 * m * (1 + 1e-7), 0.3)
        val far = msq(sq, kin, base, 3 * m, 0.3)
        assertTrue("$near vs $far", abs(near / far) < 1e-5)
        val mL = base["m_seL"]!!; val mR = base["m_seR"]!!
        fun shape(rootS: Double, c: Double): Double {
            val (t, u) = tu(kin, base, rootS, c, m)
            val s = rootS * rootS
            fun f(ms: Double) = (m * m - t) * (m * m - t) / ((t - ms * ms) * (t - ms * ms)) + (m * m - u) * (m * m - u) / ((u - ms * ms) * (u - ms * ms)) -
                2 * m * m * s / ((t - ms * ms) * (u - ms * ms))
            return f(mL) + f(mR)
        }
        val points = listOf(300.0 to 0.2, 400.0 to -0.7, 900.0 to 0.9)
        val ratios = points.map { (e, c) -> msq(sq, kin, base, e, c) / shape(e, c) }
        // Σ|ℳ|² = g⁴ F for each chirality with g = √2 e, averaged over 4 spins: e⁴ F.
        val e4 = Math.pow(base["e"]!!, 4.0)
        for (r in ratios) assertEquals(e4, r, e4 * 1e-8)
    }

    @Test fun likeSignSelectronsNeedTheMajoranaMass() {
        // e⁻e⁻ → ẽ_R⁻ẽ_R⁻ by photino exchange in t and u: ℳ ∝ m_γ̃ [1/(t − m²) + 1/(u − m²)],
        // with the two channels adding (a clash of fermion flow in each).
        val o = SolveOptions(theory = Theory.SQED, masslessFermions = true)
        val seR = SUSY.sleptons.first { it.id == "seR" }
        val (sq, kin) = squared(listOf(leg(SM.electron, true), leg(SM.electron, true), leg(seR, false), leg(seR, false)), o)
        val m = base["m_photino"]!!
        val ms = base["m_seR"]!!
        fun shape(rootS: Double, c: Double): Double {
            val s = rootS * rootS
            val p = sqrt(s / 4 - ms * ms); val e = rootS / 2
            val t = ms * ms - 2 * e * (e - p * c)
            val u = ms * ms - 2 * e * (e + p * c)
            val a = 1 / (t - m * m) + 1 / (u - m * m)
            return m * m * s * a * a
        }
        val r = listOf(400.0 to 0.1, 700.0 to -0.5, 2000.0 to 0.8).map { (e, c) -> msq(sq, kin, base, e, c) / shape(e, c) }
        // Σ|ℳ|² = g⁴ m² s A² (Tr p̸₁P_L p̸₂P_R = s) with g = √2 e, over 4 spins: e⁴.
        val e4 = Math.pow(base["e"]!!, 4.0)
        for (x in r) assertEquals(e4, x, e4 * 1e-8)
    }

    @Test fun gluinoPairsFromQuarks() {
        // q q̄ → g̃ g̃ through a gluon is q q̄ → Q Q̄ with the color factor 12 instead of 2.
        val o = SolveOptions(masslessFermions = true)
        val gluonOnly = { d: Diagram -> d.lines.none { SUSY.sf(SM.byId(it.particle)!!) != null } }
        val (gg, kinG) = squared(listOf(leg(SM.up, true), leg(SM.up, true, true), leg(SUSY.gluino, false), leg(SUSY.gluino, false)), o.copy(theory = Theory.SQCD), gluonOnly)
        val (tt, kinT) = squared(listOf(leg(SM.up, true), leg(SM.up, true, true), leg(SM.top, false), leg(SM.top, false, true)), o.copy(theory = Theory.QCD))
        val v = base + mapOf("m_t" to base["m_gluino"]!!)
        for ((e, c) in listOf(3000.0 to 0.3, 5000.0 to -0.8)) {
            val a = msq(gg, kinG, v, e, c); val b = msq(tt, kinT, v, e, c)
            assertEquals(6.0, a / b, 1e-9)
        }
    }

    @Test fun wessZuminoMassIsNotRenormalized() {
        // The UV pole of the scalar self-energy is ∝ (p² + m²): only wave-function renormalization
        // (the superpotential isn't renormalized, and m₀² = Z⁻¹m² in the Lagrangian).
        val o = SolveOptions(theory = Theory.WZ)
        val ctx = Solver.context(o)
        val ds = Generate.generate(listOf(leg(SUSY.phiWZ, true), leg(SUSY.phiWZ, false)), ctx, Generate.Options(loops = 1)).diagrams
        assertTrue(ds.size >= 3)
        var pole = Expr.ZERO
        for (d in ds) pole += Solver.solve(d, o).loop!!.pole
        val m2 = sym(SUSY.mWZ, 2)
        val atMinus = pole.substitute(Mandelstam.p2, m2 * CQ.of(-1))
        assertTrue(Tex.of(pole), !pole.isZero)
        assertTrue(Tex.of(pole), Rules.simplifyRoots(atMinus).isZero)
    }

    /** The numbers multiplying each Dirac/Lorentz structure of a rule, by structure (γ's only). */
    private fun coefficients(r: RuleUse, v: Map<String, Double>): Map<String, Double> {
        val out = HashMap<String, Double>()
        for (t in r.terms) for ((k, c) in t.expr.terms) {
            val key = (k.chains.firstOrNull() ?: emptyList()).joinToString(",") { g -> when (g) { is G.I -> "mu"; G.Five -> "5"; is G.S -> g.p } }
            val num = Evaluate.scalar(Expr(mapOf(TermKey(Mono(k.mono.factors.filter { it.first is Sym }), emptyList()) to c)), v)!!
            // Rules are i × real or real: keep the non-zero part.
            out[key] = (out[key] ?: 0.0) + (if (abs(num.re) > abs(num.im)) num.re else num.im)
        }
        return out
    }

    private fun rule(ps: List<Pair<Particle, Boolean>>): RuleUse? {
        val pool = IndexPool()
        val legs = ps.map { (p, anti) -> Leg(p, anti, pool.fresh(), emptyMap(), null) }
        return Rules.vertex(legs, Solver.context(SolveOptions(theory = Theory.MSSM)))
    }

    @Test fun neutralinoGoldstoneWardIdentity() {
        // φ_Z χ̃⁰_iχ̃⁰_j ∝ (m_i + m_j) × the Z's axial coupling, one constant for all i, j.
        val v = base
        val ratios = ArrayList<Double>()
        for (i in 1..4) for (j in i..4) {
            val ni = SUSY.neutralinos[i - 1]; val nj = SUSY.neutralinos[j - 1]
            val z = coefficients(rule(listOf(ni to false, nj to false, SM.z to false))!!, v)["mu,5"] ?: 0.0
            val g = coefficients(rule(listOf(ni to false, nj to false, SM.phiZ to false))!!, v)["5"] ?: 0.0
            val mi = v["m_n$i"]!!; val mj = v["m_n$j"]!!
            if (abs(z) < 1e-6) { assertEquals(0.0, g, 1e-9); continue }
            ratios.add(g / ((mi + mj) * z))
        }
        assertTrue(ratios.size >= 4)
        for (r in ratios) assertEquals(ratios[0], r, 1e-9 * abs(ratios[0]))
        // And it's 1/m_Z.
        assertEquals(1 / v["mZ"]!!, abs(ratios[0]), 1e-9 / v["mZ"]!!)
    }

    @Test fun charginoAndWGoldstoneWardIdentities() {
        val v = base
        fun lr(c: Map<String, Double>, vec: Boolean): Pair<Double, Double> {
            val one = c[if (vec) "mu" else ""] ?: 0.0
            val five = c[if (vec) "mu,5" else "5"] ?: 0.0
            return (one - five) to (one + five) // P_L and P_R coefficients
        }
        // Z and φ_Z between charginos i (out) and j (in).
        val zRatios = ArrayList<Double>()
        for (i in 1..2) for (j in 1..2) {
            val ci = SUSY.charginos[i - 1]; val cj = SUSY.charginos[j - 1]
            val (a, b) = lr(coefficients(rule(listOf(ci to true, cj to false, SM.z to false))!!, v), true)
            val (gl, gr) = lr(coefficients(rule(listOf(ci to true, cj to false, SM.phiZ to false))!!, v), false)
            val mi = v["m_ch$i"]!!; val mj = v["m_ch$j"]!!
            val wl = a * mi - b * mj; val wr = b * mi - a * mj
            if (abs(wl) > 1e-6) zRatios.add(gl / wl)
            if (abs(wr) > 1e-6) zRatios.add(gr / wr)
        }
        for (r in zRatios) assertEquals(zRatios[0], r, 1e-9 * abs(zRatios[0]))
        assertEquals(1 / v["mZ"]!!, abs(zRatios[0]), 1e-9)
        // W⁺ and φ⁺ making chargino i from neutralino j.
        val wRatios = ArrayList<Double>()
        for (i in 1..2) for (j in 1..4) {
            val ci = SUSY.charginos[i - 1]; val nj = SUSY.neutralinos[j - 1]
            val (a, b) = lr(coefficients(rule(listOf(ci to true, nj to false, SM.w to false))!!, v), true)
            val (gl, gr) = lr(coefficients(rule(listOf(ci to true, nj to false, SM.phi to false))!!, v), false)
            val mi = v["m_ch$i"]!!; val mj = v["m_n$j"]!!
            val wl = a * mi - b * mj; val wr = b * mi - a * mj
            if (abs(wl) > 1e-6) wRatios.add(gl / wl)
            if (abs(wr) > 1e-6) wRatios.add(gr / wr)
        }
        assertTrue(wRatios.size > 8)
        for (r in wRatios) assertEquals(wRatios[0], r, 1e-9 * abs(wRatios[0]))
        assertEquals(1 / v["mW"]!!, abs(wRatios[0]), 1e-9)
    }

    @Test fun mixingMatricesDiagonalize() {
        val v = base
        val n = Array(4) { i -> DoubleArray(4) { j -> v["N${i + 1}${j + 1}"]!! } }
        for (i in 0 until 4) for (j in 0 until 4) {
            val dotp = (0 until 4).sumOf { n[i][it] * n[j][it] }
            assertEquals(if (i == j) 1.0 else 0.0, dotp, 1e-10)
        }
        val masses = (1..4).map { abs(v["m_n$it"]!!) }
        assertEquals(masses.sorted(), masses)
        val c = (1..2).map { v["m_ch$it"]!! }
        assertTrue(c[0] > 0 && c[1] > c[0])
    }

    private fun growth(legs: List<Generate.Leg>, o: SolveOptions): Pair<Double, Double> {
        val (sq, kin) = squared(legs, o)
        val a = msq(sq, kin, base, 2000.0, 0.4)
        val b = msq(sq, kin, base, 20000.0, 0.4)
        val c = msq(sq, kin, base, 200000.0, 0.4)
        return b / a to c / b
    }

    @Test fun wPairsToCharginosStayUnitary() {
        // W⁺W⁻ → χ̃⁺₁χ̃⁻₁ in the unitary gauge: γ, Z, h, H and four neutralinos. With wrong
        // relative signs the longitudinal W's make |ℳ|² grow like s.
        val o = SolveOptions(theory = Theory.MSSM, gauge = Gauge.Unitary, masslessFermions = true)
        val c1 = SUSY.charginos[0]
        val (r1, r2) = growth(listOf(leg(SM.w, true), leg(SM.w, true, true), leg(c1, false), leg(c1, false, true)), o)
        assertTrue("$r1 $r2", r2 < 1.1 && r1 < 3)
    }

    @Test fun zPairsToNeutralinosStayUnitary() {
        // With the mixing matrices put in as numbers (symbolically it's a very long polynomial).
        val o = SolveOptions(theory = Theory.MSSM, gauge = Gauge.Unitary, masslessFermions = true, numbers = SUSY.derive(base))
        val n1 = SUSY.neutralinos[0]; val n2 = SUSY.neutralinos[1]
        val (r1, r2) = growth(listOf(leg(SM.z, true), leg(SM.z, true), leg(n1, false), leg(n2, false)), o)
        assertTrue("$r1 $r2", r2 < 1.1 && r1 < 3)
    }

    @Test fun electronZToSelectronNeutralino() {
        // e⁻Z → ẽ_L χ̃⁰₁: s-channel e, t-channel ẽ_L and u-channel χ̃⁰_j, j = 1…4.
        val o = SolveOptions(theory = Theory.MSSM, gauge = Gauge.Unitary, masslessFermions = true)
        val seL = SUSY.sleptons.first { it.id == "seL" }
        val (r1, r2) = growth(listOf(leg(SM.electron, true), leg(SM.z, true), leg(seL, false), leg(SUSY.neutralinos[0], false)), o)
        assertTrue("$r1 $r2", r2 < 1.1 && r1 < 3)
    }
}
