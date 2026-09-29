package com.example.feynman.physics

import kotlin.math.abs
import kotlin.math.sqrt

/*
 * Supersymmetric theories: the Wess–Zumino model, supersymmetric QED and QCD, and the MSSM.
 *
 * Gauge interactions of the new fields come from the same covariant derivative as the paper's
 * Standard Model rules, D_μ = ∂_μ + igT^aW^a_μ + ig′YB_μ (with its η signs: η_e for A, ηη_Z
 * for Z, η for W and η_s for gluons, as in rules (58)–(95)). A scalar with the charges of a
 * fermion couples like it with γ^μ → (k_in − k_out)^μ, and the seagulls are ig^{μν}{C_a, C_b}.
 *
 * The gaugino and higgsino (Yukawa) couplings are those of S. P. Martin, "A Supersymmetry
 * Primer" (arXiv:hep-ph/9709356): −√2 g(φ*T^aψ)λ^a + h.c. and the superpotential
 * W = ū y_u Q H_u − d̄ y_d Q H_d − ē y_e L H_d + μ H_u H_d, with H_u = Φ₂ and H_d = iσ₂Φ₁*
 * for the paper's doublets (so the Higgs sector is the type-II two-doublet model of Models.kt).
 * Neutralinos χ̃⁰_i = N_{ij}(B̃, W̃⁰, H̃⁰_d, H̃⁰_u)_j and charginos χ̃⁺_i = V_{ij}(W̃⁺, H̃⁺_u)_j,
 * χ̃⁻_i = U_{ij}(W̃⁻, H̃⁻_d)_j with real N, U, V (neutralino masses may come out negative;
 * they're used with their sign). Majorana fermions and fermion-number-violating vertices use
 * the fermion-flow rules of Denner, Eck, Hahn and Küblbeck (Nucl. Phys. B 387 (1992) 467).
 *
 * Left out: left–right sfermion mixing and the trilinear A-terms, the four-scalar couplings of
 * the MSSM (D- and F-terms; they're kept in supersymmetric QED), and the Higgs self-couplings.
 */
object SUSY {
    // --- Parameters -----------------------------------------------------------------------------

    val mWZ = Sym("m_wz", "m", order = 24)
    val yWZ = Sym("y_wz", "y", order = 4)
    val M1 = Sym("M1", "M_1", order = 25)
    val M2 = Sym("M2", "M_2", order = 25)
    val mu = Sym("mu_susy", "\\mu", order = 25)
    val mPhotino = Sym("m_photino", "m_{\\tilde\\gamma}", order = 24)
    val mGluino = Sym("m_gluino", "m_{\\tilde g}", order = 24)
    fun nSym(i: Int, j: Int) = Sym("N$i$j", "N_{$i$j}", order = 14)
    fun uSym(i: Int, j: Int) = Sym("U$i$j", "U_{$i$j}", order = 14)
    fun vSym(i: Int, j: Int) = Sym("V$i$j", "V_{$i$j}", order = 14)
    val mN = (1..4).map { Sym("m_n$it", "m_{\\tilde\\chi^0_$it}", order = 24) }
    val mC = (1..2).map { Sym("m_ch$it", "m_{\\tilde\\chi^\\pm_$it}", order = 24) }

    /** Values that follow from M₁, M₂, μ and tan β (the mixing matrices and the masses). */
    val derived: Set<String> = ((1..4).flatMap { i -> (1..4).map { j -> "N$i$j" } } +
        (1..2).flatMap { i -> (1..2).flatMap { j -> listOf("U$i$j", "V$i$j") } } +
        mN.map { it.name } + mC.map { it.name }).toSet()

    /** What to ask for instead of a derived value. */
    val inputs = listOf(M1, M2, mu, BSM.cBeta, BSM.sBeta)

    // --- Fields ---------------------------------------------------------------------------------

    /** A sfermion: its fermion partner and whether it's the left-handed one's. */
    class Sf(val partner: Particle, val left: Boolean)

    private val sfInfo = HashMap<String, Sf>()

    private fun baseTex(f: Particle) = when (f.id) {
        "e" -> "e"; "mu" -> "\\mu"; "tau" -> "\\tau"; else -> f.id
    }

    private fun sfermion(f: Particle, left: Boolean, group: String, mass: Double): Particle {
        val lr = if (f.family == Family.Neutrino) "" else if (left) "L" else "R"
        val id = "s${f.id}$lr"
        val (tex, anti) = when (f.family) {
            Family.ChargedLepton -> "\\tilde{${baseTex(f)}}_$lr^-" to "\\tilde{${baseTex(f)}}_$lr^+"
            Family.Neutrino -> f.tex.replace("\\nu", "\\tilde\\nu") to f.tex.replace("\\nu", "\\tilde\\nu") + "^{*}"
            else -> "\\tilde{${f.id}}_$lr" to "\\tilde{${f.id}}_$lr^{*}"
        }
        val massSym = Sym("m_$id", "m_{${tex.removeSuffix("^-")}}", order = 23)
        defaultMasses[massSym.name] = mass
        val name = when (f.family) {
            Family.Neutrino -> "${f.name.removeSuffix(" neutrino")} sneutrino"
            Family.ChargedLepton -> "${if (left) "Left" else "Right"} s${f.name.lowercase()}"
            else -> "${if (left) "Left" else "Right"} ${f.name.removeSuffix(" quark").lowercase()} squark"
        }
        return Particle(
            id, name, tex, anti, Spin.Scalar, LineStyle.Scalar, true,
            charge = f.charge, t3 = if (left) f.t3 else Rational.ZERO, mass = massSym, color = f.color,
            family = f.family, generation = f.generation, group = group,
        ).also { sfInfo[id] = Sf(f, left) }
    }

    /** Default sfermion masses (GeV), set as the fields are made. */
    private val defaultMasses = LinkedHashMap<String, Double>()

    private val lep = listOf(SM.electron, SM.muon, SM.tau)
    val sleptons: List<Particle> = lep.flatMap { f ->
        val heavy = f === SM.tau
        listOf(sfermion(f, true, "Sleptons", if (heavy) 190.0 else 200.0), sfermion(f, false, "Sleptons", if (heavy) 130.0 else 145.0))
    }
    val sneutrinos: List<Particle> = listOf(SM.nue, SM.numu, SM.nutau).map { sfermion(it, true, "Sneutrinos", 185.0) }
    private val qs = listOf(SM.up, SM.down, SM.charm, SM.strange, SM.top, SM.bottom)
    val squarksL: List<Particle> = qs.map { sfermion(it, true, "L squarks", if (it === SM.top || it === SM.bottom) 900.0 else 1000.0) }
    val squarksR: List<Particle> = qs.map { sfermion(it, false, "R squarks", if (it === SM.top) 700.0 else if (it === SM.bottom) 950.0 else 980.0) }
    val sfermions get() = sleptons + sneutrinos + squarksL + squarksR

    val neutralinos: List<Particle> = (1..4).map {
        Particle("n$it", "Neutralino $it", "\\tilde\\chi^0_$it", "\\tilde\\chi^0_$it", Spin.Fermion, LineStyle.Fermion, false,
            mass = mN[it - 1], group = "Charginos and neutralinos")
    }
    val charginos: List<Particle> = (1..2).map {
        Particle("ch$it", "Chargino $it", "\\tilde\\chi^+_$it", "\\tilde\\chi^-_$it", Spin.Fermion, LineStyle.Fermion, true,
            charge = Rational.ONE, mass = mC[it - 1], group = "Charginos and neutralinos")
    }
    val photino = Particle("photino", "Photino", "\\tilde\\gamma", "\\tilde\\gamma", Spin.Fermion, LineStyle.Gaugino, false, mass = mPhotino, group = "Gauginos")
    val gluino = Particle("gluino", "Gluino", "\\tilde g", "\\tilde g", Spin.Fermion, LineStyle.Gaugino, false, mass = mGluino, color = ColorRep.Octet, group = "Gauginos")
    val phiWZ = Particle("phiwz", "Complex scalar φ", "\\phi", "\\phi^{*}", Spin.Scalar, LineStyle.Scalar, true, mass = mWZ, group = "Scalars")
    val psiWZ = Particle("psiwz", "Majorana fermion ψ", "\\psi", "\\psi", Spin.Fermion, LineStyle.Fermion, false, mass = mWZ, group = "Fermions")

    val all: List<Particle> = sfermions + neutralinos + charginos + photino + gluino + phiWZ + psiWZ

    fun sf(p: Particle): Sf? = sfInfo[p.id]
    fun isSusy(p: Particle) = p in all
    private fun neutralinoIndex(p: Particle) = neutralinos.indexOf(p) + 1
    private fun charginoIndex(p: Particle) = charginos.indexOf(p) + 1
    private fun upType(f: Particle) = f.family == Family.UpQuark || f.family == Family.Neutrino

    // --- Numbers --------------------------------------------------------------------------------

    val defaults: Map<String, Double> by lazy {
        defaultMasses + mapOf(
            mWZ.name to 100.0, yWZ.name to 0.5, M1.name to 100.0, M2.name to 200.0, mu.name to 400.0,
            mPhotino.name to 100.0, mGluino.name to 1200.0,
        )
    }

    /**
     * N, U, V and the neutralino and chargino masses from M₁, M₂, μ, tan β, m_Z and θ_W
     * (Martin's (8.2.2) and (8.2.5)): N M N^T = diag(m_χ⁰), U X V^T = diag(m_χ±).
     */
    fun derive(v: Map<String, Double>): Map<String, Double> {
        val m1 = v[M1.name] ?: return emptyMap()
        val m2 = v[M2.name] ?: return emptyMap()
        val muv = v[mu.name] ?: return emptyMap()
        val cb = v[BSM.cBeta.name] ?: return emptyMap()
        val sb = v[BSM.sBeta.name] ?: return emptyMap()
        val mZ = v["mZ"] ?: return emptyMap()
        val sw = v["sW"] ?: return emptyMap()
        val cw = v["cW"] ?: return emptyMap()
        val mW = v["mW"] ?: (mZ * cw)
        val out = HashMap<String, Double>()
        val mn = arrayOf(
            doubleArrayOf(m1, 0.0, -cb * sw * mZ, sb * sw * mZ),
            doubleArrayOf(0.0, m2, cb * cw * mZ, -sb * cw * mZ),
            doubleArrayOf(-cb * sw * mZ, cb * cw * mZ, 0.0, -muv),
            doubleArrayOf(sb * sw * mZ, -sb * cw * mZ, -muv, 0.0),
        )
        val (vals, vecs) = Linear.symmetricEigen(mn)
        val order = vals.indices.sortedBy { abs(vals[it]) }
        order.forEachIndexed { row, k ->
            out[mN[row].name] = vals[k]
            // Rows of N are the eigenvectors; fix the sign so the largest entry is positive.
            val vec = DoubleArray(4) { vecs[it][k] }
            val big = vec.indices.maxBy { abs(vec[it]) }
            val s = if (vec[big] < 0) -1.0 else 1.0
            for (j in 0 until 4) out[nSym(row + 1, j + 1).name] = s * vec[j]
        }
        val x = arrayOf(doubleArrayOf(m2, sqrt(2.0) * sb * mW), doubleArrayOf(sqrt(2.0) * cb * mW, muv))
        val (uu, masses, vv) = Linear.svd2(x)
        for (i in 0 until 2) {
            out[mC[i].name] = masses[i]
            for (j in 0 until 2) { out[uSym(i + 1, j + 1).name] = uu[i][j]; out[vSym(i + 1, j + 1).name] = vv[i][j] }
        }
        return out
    }

    // --- Rules ----------------------------------------------------------------------------------

    private val i get() = Expr.I
    private val g get() = sym(Couplings.g)
    private val e get() = sym(Couplings.e)
    private val gs get() = sym(Couplings.gs)
    private val sW get() = sym(Couplings.sW)
    private val cWinv get() = sym(Couplings.cW, -1)
    private val mW get() = sym(Couplings.mW)
    private val mZ get() = sym(Couplings.mZ)
    private val sqrt2 get() = sym(Rules.sqrt2)
    private val invSqrt2 get() = sym(Rules.sqrt2, -1)
    private val gp get() = g * sW * cWinv
    private val cb get() = sym(BSM.cBeta)
    private val sb get() = sym(BSM.sBeta)
    private val ca get() = sym(BSM.cAlpha)
    private val sa get() = sym(BSM.sAlpha)
    private fun n(a: Long, b: Long = 1) = Expr.const(a, b)
    /** The rule context being used (for the mixing matrices as numbers). */
    private val current = ThreadLocal<RuleContext>()
    private fun mix(s: Sym) = current.get()?.value(s) ?: sym(s)
    private fun N(a: Int, b: Int) = mix(nSym(a, b))
    private fun Ue(a: Int, b: Int) = mix(uSym(a, b))
    private fun Ve(a: Int, b: Int) = mix(vSym(a, b))

    private fun rule(eq: String, terms: List<VertexTerm>, tex: String, left: Int? = null, right: Int? = null) =
        RuleUse(0, terms.map { VertexTerm(it.color, Rules.simplifyRoots(it.expr)) }, tex, eq, left, right)

    private fun one(expr: Expr, tex: String, label: String, color: List<ColorFactor> = emptyList(), left: Int? = null, right: Int? = null) =
        rule(label, listOf(VertexTerm(color, expr)), tex, left, right)

    /** a P_L + b P_R. */
    private fun chiral(a: Expr, b: Expr) = projL * a + projR * b

    private fun chiralTex(a: Expr, b: Expr): String {
        val parts = listOfNotNull(
            if (a.isZero) null else "${Tex.paren(Tex.of(Rules.simplifyRoots(a)))} P_L",
            if (b.isZero) null else "${Tex.paren(Tex.of(Rules.simplifyRoots(b)))} P_R",
        )
        return if (parts.isEmpty()) "0" else parts.joinToString(" + ").replace("+ -", "- ")
    }

    /** y_f = g m_f/(√2 m_W sin β) for up-type fermions, cos β for down-type. */
    private fun yukawa(f: Particle, ctx: RuleContext): Expr {
        val m = ctx.massOf(f) ?: return Expr.ZERO
        return g * sym(m) * invSqrt2 * sym(Couplings.mW, -1) * sym(if (upType(f)) BSM.sBeta else BSM.cBeta, -1)
    }

    /** The η sign of a gauge boson's couplings (its field's sign in the paper's conventions). */
    private fun eta(b: Particle, c: Conventions): Long = when (b) {
        SM.photon -> c.etaE.toLong()
        SM.z -> (c.eta * c.etaZ).toLong()
        SM.w -> c.eta.toLong()
        SM.gluon -> c.etaS.toLong()
        else -> 1L
    }

    fun vertex(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        if (legs.none { isSusy(it.particle) }) return null
        current.set(ctx)
        try { return vertexIn(legs, ctx) } finally { current.remove() }
    }

    private fun vertexIn(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        if (ctx.theory == Theory.WZ) return wz(legs)
        val fermions = legs.filter { it.particle.isFermion }
        val sfs = legs.filter { sf(it.particle) != null }
        return when {
            fermions.size == 2 && sfs.size == 1 && legs.size == 3 -> fermionSfermion(legs, ctx)
            fermions.size == 2 && legs.size == 3 -> inoBoson(legs, ctx)
            sfs.size == 2 && legs.size == 3 -> sfermionBoson(legs, ctx)
            sfs.size == 2 && legs.size == 4 -> sfermionTwoBosons(legs, ctx)
            sfs.size == 4 && legs.size == 4 && ctx.theory == Theory.SQED -> sqedQuartic(legs)
            else -> null
        }
    }

    // Wess–Zumino: W = mΦ²/2 + yΦ³/6.
    private fun wz(legs: List<Leg>): RuleUse? {
        if (legs.any { it.particle !== phiWZ && it.particle !== psiWZ }) return null
        val y = sym(yWZ); val m = sym(mWZ)
        val psi = legs.indices.filter { legs[it].particle === psiWZ }
        val phis = legs.filter { it.particle === phiWZ }
        val ins = phis.count { !it.anti }
        val outs = phis.size - ins
        return when {
            psi.size == 2 && phis.size == 1 && ins == 1 -> one(projL * y * CQ.imag(-1), "-i y P_L", "WZ Yukawa", left = psi[0], right = psi[1])
            psi.size == 2 && phis.size == 1 -> one(projR * y * CQ.imag(-1), "-i y P_R", "WZ Yukawa", left = psi[0], right = psi[1])
            psi.isEmpty() && phis.size == 3 && (ins == 2 || outs == 2) -> one(m * y * CQ.imag(-1), "-i m y", "WZ cubic")
            psi.isEmpty() && phis.size == 4 && ins == 2 -> one(y * y * CQ.imag(-1), "-i y^{2}", "WZ quartic")
            else -> null
        }
    }

    /** The CKM factor between an up- and a down-type (s)quark, or 1; null if they don't couple. */
    private fun ckm(up: Particle, down: Particle, raising: Boolean, ctx: RuleContext): Expr? {
        if (up.color != down.color) return null
        if (up.color == ColorRep.None) return if (up.generation == down.generation) Expr.ONE else null
        if (ctx.ckmIdentity) return if (up.generation == down.generation) Expr.ONE else null
        return sym(Couplings.ckm(up, down).let { if (raising) it else it.conj() })
    }

    /**
     * −i C (k_in − k_out)^μ: C for a gauge boson leg between sfermions [out] ← [inn] (null when
     * they don't couple), with its color factor.
     */
    private fun gaugeC(b: Leg, out: Particle, inn: Particle, outC: CIdx?, inC: CIdx?, ctx: RuleContext): Pair<Expr, List<ColorFactor>>? {
        val so = sf(out) ?: return null
        val si = sf(inn) ?: return null
        val c = ctx.conv
        val sign = CQ.of(eta(b.particle, c))
        val delta = if (outC != null && inC != null) listOf(ColorFactor.D(outC, inC)) else emptyList()
        return when (b.particle) {
            SM.photon -> if (out !== inn || out.charge.signum == 0 || ctx.theory == Theory.SQCD) null else (e * Expr.const(out.charge) * sign) to delta
            SM.z -> {
                if (out !== inn || ctx.theory == Theory.SQED || ctx.theory == Theory.SQCD) return null
                val t3 = if (so.left) so.partner.t3 else Rational.ZERO
                val x = Expr.const(t3) - Expr.const(out.charge) * sW * sW
                if (x.isZero) null else (g * cWinv * x * sign) to delta
            }
            SM.gluon -> if (out !== inn || out.color != ColorRep.Triplet) null else (gs * sign) to listOf(ColorFactor.T(b.color!!, outC!!, inC!!))
            SM.w -> {
                if (!so.left || !si.left || ctx.theory != Theory.MSSM) return null
                val raising = !b.anti
                if (out.charge - inn.charge != if (raising) Rational.ONE else Rational.of(-1)) return null
                val (up, down) = if (raising) so.partner to si.partner else si.partner to so.partner
                if (!upType(up) || upType(down)) return null
                val v = ckm(up, down, raising, ctx) ?: return null
                (g * invSqrt2 * v * sign) to delta
            }
            else -> null
        }
    }

    private fun sfermionBoson(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        val out = legs.firstOrNull { sf(it.particle) != null && it.anti } ?: return null
        val inn = legs.firstOrNull { sf(it.particle) != null && !it.anti } ?: return null
        val b = legs.first { it !== out && it !== inn }
        if (b.particle.isVector) {
            val (c, color) = gaugeC(b, out.particle, inn.particle, out.color, inn.color, ctx) ?: return null
            val mu = ctx.names.name(b.index)
            val diff = inn.k - out.k
            return one(c * vec(diff, b.index) * CQ.imag(-1), Rules.combineTex(c * CQ.imag(-1), "\\left(${MomNames.tex(diff)}\\right)_{$mu}", color), "sfermion–gauge", color)
        }
        if (ctx.theory != Theory.MSSM) return null
        return sfermionHiggs(b, out, inn, ctx)
    }

    /** D- and F-term couplings of h, H, H± and φ± to sfermions (no left–right mixing). */
    private fun sfermionHiggs(b: Leg, out: Leg, inn: Leg, ctx: RuleContext): RuleUse? {
        val so = sf(out.particle)!!; val si = sf(inn.particle)!!
        val color = if (out.color != null && inn.color != null) listOf(ColorFactor.D(out.color, inn.color)) else emptyList()
        when (b.particle) {
            SM.higgs, BSM.heavyH -> {
                if (out.particle !== inn.particle) return null
                val f = so.partner
                val x = if (so.left) Expr.const(f.t3) - Expr.const(f.charge) * sW * sW else Expr.const(f.charge) * sW * sW
                val heavy = b.particle === BSM.heavyH
                val sab = sa * cb + ca * sb
                val cab = ca * cb - sa * sb
                val d = g * mZ * cWinv * x * (if (heavy) cab else sab * CQ.of(-1))
                val m2 = ctx.massOf(f)?.let { sym(it, 2) } ?: Expr.ZERO
                val fterm = g * m2 * sym(Couplings.mW, -1) * when {
                    upType(f) && !heavy -> ca * sym(BSM.sBeta, -1)
                    upType(f) -> sa * sym(BSM.sBeta, -1)
                    !heavy -> sa * sym(BSM.cBeta, -1) * CQ.of(-1)
                    else -> ca * sym(BSM.cBeta, -1)
                }
                val coef = (d + fterm) * CQ.imag(-1)
                return one(coef, Rules.combineTex(Rules.simplifyRoots(coef), "", color), "sfermion–Higgs", color)
            }
            SM.phi, BSM.chargedH -> {
                if (!so.left || !si.left) return null
                // φ⁻ (or H⁻) coming in turns the up-type sfermion into the down-type one.
                val minusIn = b.anti
                val (up, down) = if (minusIn) si.partner to so.partner else so.partner to si.partner
                if (!upType(up) || upType(down)) return null
                if (inn.particle.charge - out.particle.charge != if (minusIn) Rational.ONE else Rational.of(-1)) return null
                val v = ckm(up, down, !minusIn, ctx) ?: return null
                val mu2 = ctx.massOf(up)?.let { sym(it, 2) } ?: Expr.ZERO
                val md2 = ctx.massOf(down)?.let { sym(it, 2) } ?: Expr.ZERO
                val mw2 = sym(Couplings.mW, 2)
                val c2b = cb * cb - sb * sb
                val s2b = sb * cb * CQ.of(2)
                val inner = if (b.particle === SM.phi) md2 - mu2 - mw2 * c2b
                else mw2 * s2b - mu2 * cb * sym(BSM.sBeta, -1) - md2 * sb * sym(BSM.cBeta, -1)
                val coef = g * invSqrt2 * sym(Couplings.mW, -1) * inner * v * CQ.imag(-1)
                return one(coef, Rules.combineTex(Rules.simplifyRoots(coef), "", color), "sfermion–Higgs ±", color)
            }
            else -> return null
        }
    }

    private fun sfermionTwoBosons(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        val out = legs.firstOrNull { sf(it.particle) != null && it.anti } ?: return null
        val inn = legs.firstOrNull { sf(it.particle) != null && !it.anti } ?: return null
        val (b1, b2) = legs.filter { it !== out && it !== inn }
        if (!b1.particle.isVector || !b2.particle.isVector) return null
        val terms = ArrayList<VertexTerm>()
        // i g^{μν} Σ_mid [C₁(out←mid)C₂(mid←in) + C₂(out←mid)C₁(mid←in)].
        for ((x, y) in listOf(b1 to b2, b2 to b1)) {
            for (mid in ctx.theory.particles.filter { sf(it) != null }) {
                val midC = if (mid.color == ColorRep.Triplet) CIdx.internalTriplet("k") else null
                val first = gaugeC(x, out.particle, mid, out.color, midC, ctx) ?: continue
                val second = gaugeC(y, mid, inn.particle, midC, inn.color, ctx) ?: continue
                terms.add(VertexTerm(first.second + second.second, first.first * second.first * met(b1.index, b2.index) * CQ.I))
            }
        }
        if (terms.isEmpty()) return null
        // Colorless pairs: add the two orderings into one term.
        val merged = if (terms.all { t -> t.color.all { it is ColorFactor.D } }) {
            val c = terms.first().color
            listOf(VertexTerm(c, sum(terms.map { it.expr })))
        } else terms
        val gt = "g_{${ctx.names.name(b1.index)}${ctx.names.name(b2.index)}}"
        val tex = merged.joinToString(" + ") { Rules.combineTex(Rules.simplifyRoots(it.expr.substitute(Met.of(b1.index, b2.index), Expr.ONE)), gt, it.color) }
        return rule("sfermion seagull", merged, tex)
    }

    /** Supersymmetric QED's D-term: V = (e²/2)(−|ẽ_L|² + |ẽ_R|² − |μ̃_L|² + |μ̃_R|²)². */
    private fun sqedQuartic(legs: List<Leg>): RuleUse? {
        val ins = legs.filter { !it.anti }.map { it.particle.id }.sorted()
        val outs = legs.filter { it.anti }.map { it.particle.id }.sorted()
        if (ins.size != 2 || outs != ins) return null
        fun q(id: String) = if (sfInfo[id]!!.left) -1L else 1L
        val (a, b) = ins
        val coef = sym(Couplings.e, 2) * CQ.imag(-(if (a == b) 2L else q(a) * q(b)))
        return one(coef, Tex.of(coef), "D-term")
    }

    /** A fermion, its superpartner and a neutralino, photino, gluino or chargino. */
    private fun fermionSfermion(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        val s = legs.first { sf(it.particle) != null }
        val info = sf(s.particle)!!
        val ino = legs.firstOrNull { it.particle in neutralinos || it.particle in charginos || it.particle === photino || it.particle === gluino } ?: return null
        val fl = legs.first { it !== s && it !== ino }
        if (!fl.particle.isFermion || fl.particle.family == null) return null
        val iS = legs.indexOf(s); val iI = legs.indexOf(ino); val iF = legs.indexOf(fl)
        val f = fl.particle
        val sOut = s.anti
        val fIn = !fl.anti
        if (ino.particle in charginos) return charginoVertex(legs, s, info, ino, fl, ctx)
        // Neutral inos: the sfermion's partner is the fermion.
        if (info.partner !== f) return null
        if (sOut != fIn) return null
        val (lft, rgt) = if (sOut) iI to iF else iF to iI
        if (ino.particle === gluino) {
            if (f.color != ColorRep.Triplet) return null
            val color = listOf(if (sOut) ColorFactor.T(ino.color!!, s.color!!, fl.color!!) else ColorFactor.T(ino.color!!, fl.color!!, s.color!!))
            val c = gs * sqrt2 * (if (info.left) CQ.imag(-1) else CQ.I)
            // q̃_L*: P_L; q̃_R*: P_R (and the other way round for the conjugate vertex).
            val proj = if (info.left == sOut) projL else projR
            val pt = if (info.left == sOut) "P_L" else "P_R"
            return one(c * proj, Rules.combineTex(c, pt, color), "q q̃ g̃", color, lft, rgt)
        }
        val q = Expr.const(f.charge)
        val gaugino: Expr; val higgsino: Expr; val bino: Expr
        if (ino.particle === photino) {
            if (f.charge.signum == 0) return null
            // The photino is a bino with g′Y → eQ.
            gaugino = e * q * sqrt2 * CQ.of(-1); higgsino = Expr.ZERO; bino = e * q * sqrt2
        } else {
            if (ctx.theory != Theory.MSSM) return null
            val k = neutralinoIndex(ino.particle)
            val t3 = f.t3
            val h = if (upType(f)) 4 else 3
            gaugino = (gp * Expr.const(f.charge - t3) * N(k, 1) + g * Expr.const(t3) * N(k, 2)) * sqrt2 * CQ.of(-1)
            higgsino = yukawa(f, ctx) * N(k, h) * CQ.of(-1)
            bino = gp * q * N(k, 1) * sqrt2
        }
        // (a P_L + b P_R) for the sfermion created (Ñ̄ Γ F) or annihilated (F̄ Γ Ñ).
        val (a, b) = when {
            info.left && sOut -> gaugino to higgsino
            info.left -> higgsino to gaugino
            sOut -> higgsino to bino
            else -> bino to higgsino
        }
        if (a.isZero && b.isZero) return null
        return one(chiral(a, b) * CQ.I, "i\\left(${chiralTex(a, b)}\\right)", if (ino.particle === photino) "f f̃ γ̃" else "f f̃ χ̃⁰", left = lft, right = rgt)
    }

    /** Chargino, fermion and sfermion of the other member of the doublet. */
    private fun charginoVertex(legs: List<Leg>, s: Leg, info: Sf, c: Leg, fl: Leg, ctx: RuleContext): RuleUse? {
        if (ctx.theory != Theory.MSSM) return null
        val f = fl.particle
        val sp = info.partner
        if (f.color != sp.color || upType(f) == upType(sp)) return null
        val iS = legs.indexOf(s); val iC = legs.indexOf(c); val iF = legs.indexOf(fl)
        val k = charginoIndex(c.particle)
        val sOut = s.anti; val fIn = !fl.anti; val cIn = !c.anti
        // Charge: the sfermion made from the fermion and the chargino (or the reverse).
        val upS = upType(sp)
        val raising = upS // ũ from d and χ̃⁺ (a, b), or d̃ from u giving χ̃⁺ (c, d)
        val (up, down) = if (upS) sp to f else f to sp
        val v = ckm(up, down, if (sOut) raising else !raising, ctx) ?: return null
        val yu = yukawa(up, ctx); val yd = yukawa(down, ctx)
        val a: Expr; val b: Expr
        val left: Int; val right: Int
        when {
            // (a), (b): f̃_u made from f_d and χ̃⁺ — both fermion arrows in.
            upS && sOut && fIn && cIn -> {
                if (info.left) { a = g * Ve(k, 1) * CQ.of(-1); b = yd * Ue(k, 2) } else { a = yu * Ve(k, 2); b = Expr.ZERO }
                left = iC; right = iF
            }
            upS && !sOut && !fIn && !cIn -> {
                if (info.left) { a = yd * Ue(k, 2); b = g * Ve(k, 1) * CQ.of(-1) } else { a = Expr.ZERO; b = yu * Ve(k, 2) }
                left = iF; right = iC
            }
            // (c), (d): f̃_d made from f_u, with χ̃⁺ going out.
            !upS && sOut && fIn && !cIn -> {
                if (info.left) { a = g * Ue(k, 1) * CQ.of(-1); b = yu * Ve(k, 2) } else { a = yd * Ue(k, 2); b = Expr.ZERO }
                left = iC; right = iF
            }
            !upS && !sOut && !fIn && cIn -> {
                if (info.left) { a = yu * Ve(k, 2); b = g * Ue(k, 1) * CQ.of(-1) } else { a = Expr.ZERO; b = yd * Ue(k, 2) }
                left = iF; right = iC
            }
            else -> return null
        }
        if (a.isZero && b.isZero) return null
        val color = if (f.color == ColorRep.Triplet) listOf(ColorFactor.D(s.color!!, fl.color!!)) else emptyList()
        val vt = if (v == Expr.ONE) "" else "${Tex.of(v)}\\,"
        return one(chiral(a, b) * v * CQ.I, "i\\,$vt\\left(${chiralTex(a, b)}\\right)", "f f̃′ χ̃±", color, left, right)
    }

    /** Two inos and a gauge boson or a Higgs boson. */
    private fun inoBoson(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        val fs = legs.filter { it.particle.isFermion }
        val b = legs.first { !it.particle.isFermion }
        val x = ctx.names
        val c = ctx.conv
        val nIdx = fs.map { legs.indexOf(it) }
        val neutral = fs.filter { it.particle in neutralinos }
        val charged = fs.filter { it.particle in charginos }
        // Gluinos and gluons.
        if (fs.all { it.particle === gluino }) {
            if (b.particle !== SM.gluon) return null
            val (l, r) = fs
            val color = listOf(ColorFactor.F(b.color!!, l.color!!, r.color!!))
            val coef = gs * CQ.of(-eta(SM.gluon, c))
            return one(coef * gamma(b.index), Rules.combineTex(coef, "\\gamma^{${x.name(b.index)}}", color), "g̃ g̃ g", color, nIdx[0], nIdx[1])
        }
        if (ctx.theory != Theory.MSSM) return null
        val mu = b.index
        val mut = x.name(mu)
        if (neutral.size == 2) {
            val (l, r) = neutral
            val p = neutralinoIndex(l.particle); val q = neutralinoIndex(r.particle)
            val au = (N(p, 4) * (g * N(q, 2) - gp * N(q, 1)) + N(q, 4) * (g * N(p, 2) - gp * N(p, 1))) * Rational.of(1, 2)
            val ad = (N(p, 3) * (gp * N(q, 1) - g * N(q, 2)) + N(q, 3) * (gp * N(p, 1) - g * N(p, 2))) * Rational.of(1, 2)
            return when (b.particle) {
                SM.z -> {
                    val o = (N(p, 3) * N(q, 3) - N(p, 4) * N(q, 4)) * Rational.of(1, 2)
                    val coef = g * cWinv * o * CQ.imag(eta(SM.z, c))
                    one(coef * gamma(mu).dirac(gamma5), Rules.combineTex(Rules.simplifyRoots(coef), "\\gamma^{$mut}\\gamma^{5}"), "χ̃⁰χ̃⁰Z", left = nIdx[0], right = nIdx[1])
                }
                SM.higgs -> scalarIno(ca * au - sa * ad, nIdx, "χ̃⁰χ̃⁰h")
                BSM.heavyH -> scalarIno(sa * au + ca * ad, nIdx, "χ̃⁰χ̃⁰H")
                BSM.pseudoA -> pseudoIno((cb * au + sb * ad) * CQ.of(-1), nIdx, "χ̃⁰χ̃⁰A")
                SM.phiZ -> pseudoIno((sb * au - cb * ad) * CQ.of(-1), nIdx, "χ̃⁰χ̃⁰φ_Z")
                else -> null
            }
        }
        if (charged.size == 2) {
            val out = charged.firstOrNull { it.anti } ?: return null
            val inn = charged.firstOrNull { !it.anti && it !== out } ?: return null
            val p = charginoIndex(out.particle); val q = charginoIndex(inn.particle)
            val lft = legs.indexOf(out); val rgt = legs.indexOf(inn)
            val d = if (p == q) Expr.ONE else Expr.ZERO
            val s2 = Ue(p, 1) * Ve(q, 2); val s1 = Ue(p, 2) * Ve(q, 1)
            val s2t = Ue(q, 1) * Ve(p, 2); val s1t = Ue(q, 2) * Ve(p, 1)
            val gh = g * invSqrt2
            return when (b.particle) {
                SM.photon -> if (p != q) null else {
                    val coef = e * CQ.imag(-eta(SM.photon, c))
                    one(coef * gamma(mu), Rules.combineTex(coef, "\\gamma^{$mut}"), "χ̃⁺χ̃⁻γ", left = lft, right = rgt)
                }
                SM.z -> {
                    val aL = Ve(p, 1) * Ve(q, 1) + Ve(p, 2) * Ve(q, 2) * Rational.of(1, 2) - d * sW * sW
                    val aR = Ue(p, 1) * Ue(q, 1) + Ue(p, 2) * Ue(q, 2) * Rational.of(1, 2) - d * sW * sW
                    val coef = g * cWinv * CQ.imag(-eta(SM.z, c))
                    one(coef * gamma(mu).dirac(chiral(aL, aR)), Rules.combineTex(coef, "\\gamma^{$mut}\\left(${chiralTex(aL, aR)}\\right)"), "χ̃⁺χ̃⁻Z", left = lft, right = rgt)
                }
                SM.higgs -> chiralIno(gh * CQ.imag(-1), ca * s2 - sa * s1, ca * s2t - sa * s1t, lft, rgt, "χ̃⁺χ̃⁻h")
                BSM.heavyH -> chiralIno(gh * CQ.imag(-1), sa * s2 + ca * s1, sa * s2t + ca * s1t, lft, rgt, "χ̃⁺χ̃⁻H")
                BSM.pseudoA -> chiralIno(gh, (cb * s2 + sb * s1) * CQ.of(-1), cb * s2t + sb * s1t, lft, rgt, "χ̃⁺χ̃⁻A")
                SM.phiZ -> chiralIno(gh, (sb * s2 - cb * s1) * CQ.of(-1), sb * s2t - cb * s1t, lft, rgt, "χ̃⁺χ̃⁻φ_Z")
                else -> null
            }
        }
        if (neutral.size == 1 && charged.size == 1) {
            val nl = neutral[0]; val cl = charged[0]
            val j = neutralinoIndex(nl.particle); val k = charginoIndex(cl.particle)
            val iN = legs.indexOf(nl); val iC = legs.indexOf(cl)
            // Incoming W⁺ / H⁺ / φ⁺ makes a χ̃⁺; W⁻ … absorbs one.
            val plusIn = !b.anti
            if (plusIn != cl.anti) return null
            val (lft, rgt) = if (plusIn) iC to iN else iN to iC
            return when (b.particle) {
                SM.w -> {
                    val oL = Ve(k, 1) * N(j, 2) - Ve(k, 2) * N(j, 4) * invSqrt2
                    val oR = Ue(k, 1) * N(j, 2) + Ue(k, 2) * N(j, 3) * invSqrt2
                    val coef = g * CQ.imag(eta(SM.w, c))
                    one(coef * gamma(mu).dirac(chiral(oL, oR)), Rules.combineTex(coef, "\\gamma^{$mut}\\left(${chiralTex(oL, oR)}\\right)"), "χ̃⁰χ̃±W", left = lft, right = rgt)
                }
                SM.phi, BSM.chargedH -> {
                    val qL = g * N(j, 4) * Ve(k, 1) + Ve(k, 2) * (gp * N(j, 1) + g * N(j, 2)) * invSqrt2
                    val qR = g * N(j, 3) * Ue(k, 1) - Ue(k, 2) * (gp * N(j, 1) + g * N(j, 2)) * invSqrt2
                    val heavy = b.particle === BSM.chargedH
                    val cq = if (heavy) cb * qL * CQ.of(-1) else sb * qL * CQ.of(-1)
                    val dq = if (heavy) sb * qR * CQ.of(-1) else cb * qR
                    // H⁺ in: C̄[cq P_R + dq P_L]Ñ; H⁻ in: Ñ̄[cq P_L + dq P_R]C.
                    val (aL, aR) = if (plusIn) dq to cq else cq to dq
                    chiralIno(Expr.I, aL, aR, lft, rgt, if (heavy) "χ̃⁰χ̃±H∓" else "χ̃⁰χ̃±φ∓")
                }
                else -> null
            }
        }
        return null
    }

    private fun scalarIno(c: Expr, idx: List<Int>, label: String): RuleUse {
        val coef = c * CQ.I
        return one(coef * diracOne, Rules.combineTex(Rules.simplifyRoots(coef), ""), label, left = idx[0], right = idx[1])
    }

    private fun pseudoIno(c: Expr, idx: List<Int>, label: String): RuleUse =
        one(c * gamma5, Rules.combineTex(Rules.simplifyRoots(c), "\\gamma^{5}"), label, left = idx[0], right = idx[1])

    private fun chiralIno(pre: Expr, a: Expr, b: Expr, l: Int, r: Int, label: String): RuleUse? {
        if (a.isZero && b.isZero) return null
        val pt = Tex.of(pre)
        return one(chiral(a, b) * pre, "${if (pt == "1") "" else pt}\\left(${chiralTex(a, b)}\\right)", label, left = l, right = r)
    }

    // --- Palette ------------------------------------------------------------------------------

    /** Short names for the palette's group buttons. */
    fun groupLabel(g: String) = when (g) {
        "Gauge bosons" -> "Bosons"
        "Charginos and neutralinos" -> "Inos"
        "L squarks" -> "q̃ L"
        "R squarks" -> "q̃ R"
        else -> g.substringBefore(' ')
    }
}

/** Small dense linear algebra for the mixing matrices. */
object Linear {
    /** Eigenvalues and eigenvectors (as columns) of a real symmetric matrix, by Jacobi rotations. */
    fun symmetricEigen(m: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val n = m.size
        val a = Array(n) { m[it].copyOf() }
        val v = Array(n) { r -> DoubleArray(n) { c -> if (r == c) 1.0 else 0.0 } }
        repeat(100) {
            var off = 0.0
            for (p in 0 until n) for (q in p + 1 until n) off += a[p][q] * a[p][q]
            if (off < 1e-24) return@repeat
            for (p in 0 until n) for (q in p + 1 until n) {
                if (abs(a[p][q]) < 1e-300) continue
                val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
                val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
                val c = 1 / sqrt(t * t + 1); val s = t * c
                for (k in 0 until n) {
                    val akp = a[k][p]; val akq = a[k][q]
                    a[k][p] = c * akp - s * akq; a[k][q] = s * akp + c * akq
                }
                for (k in 0 until n) {
                    val apk = a[p][k]; val aqk = a[q][k]
                    a[p][k] = c * apk - s * aqk; a[q][k] = s * apk + c * aqk
                }
                for (k in 0 until n) {
                    val vkp = v[k][p]; val vkq = v[k][q]
                    v[k][p] = c * vkp - s * vkq; v[k][q] = s * vkp + c * vkq
                }
            }
        }
        return DoubleArray(n) { a[it][it] } to v
    }

    /** U X V^T = diag(m₁ ≤ m₂), m ≥ 0, for a real 2 × 2 matrix X. */
    fun svd2(x: Array<DoubleArray>): Triple<Array<DoubleArray>, DoubleArray, Array<DoubleArray>> {
        // V from X^T X.
        val xtx = Array(2) { r -> DoubleArray(2) { c -> x[0][r] * x[0][c] + x[1][r] * x[1][c] } }
        val (ev, vecs) = symmetricEigen(xtx)
        val order = ev.indices.sortedBy { ev[it] }
        val vv = Array(2) { row -> DoubleArray(2) { vecs[it][order[row]] } }
        val masses = DoubleArray(2)
        val uu = Array(2) { DoubleArray(2) }
        for (i in 0 until 2) {
            // u_i = X v_i / m_i.
            val xv = DoubleArray(2) { r -> x[r][0] * vv[i][0] + x[r][1] * vv[i][1] }
            val m = sqrt(xv[0] * xv[0] + xv[1] * xv[1])
            masses[i] = m
            if (m > 1e-12) for (r in 0 until 2) uu[i][r] = xv[r] / m
        }
        if (masses[0] <= 1e-12) { uu[0][0] = -uu[1][1]; uu[0][1] = uu[1][0] }
        return Triple(uu, masses, vv)
    }
}
