package com.example.feynman.physics

/*
 * The Feynman rules of Romão & Silva, Int. J. Mod. Phys. A 27 (2012) 1230025, Secs. 4 and 5:
 * QCD, eqs. (45)–(50), and the electroweak theory in an Rξ gauge, eqs. (51)–(119), with the
 * signs η chosen by [Conventions]. All momenta are incoming, except in the ghost vertices,
 * where p is the momentum of the outgoing ghost. Calculations use the Feynman–'t Hooft
 * gauge, ξ = 1.
 */

/** A color index: a quark's (1…3) or a gluon's (1…8). */
data class CIdx(val id: Int, val adjoint: Boolean, val hint: String) {
    companion object {
        private val next = java.util.concurrent.atomic.AtomicInteger(-1)
        /** A summed index made inside a rule (the e of the four-gluon vertex). */
        fun internal(hint: String) = CIdx(next.getAndDecrement(), true, hint)
    }
}

sealed class ColorFactor {
    /** (T^a)_{ij} = λ^a_{ij}/2. */
    data class T(val a: CIdx, val i: CIdx, val j: CIdx) : ColorFactor()
    /** f^{abc}. */
    data class F(val a: CIdx, val b: CIdx, val c: CIdx) : ColorFactor()
    /** δ_{ij}: a quark keeps its color through a colorless vertex. */
    data class D(val i: CIdx, val j: CIdx) : ColorFactor()

    fun tex(): String = when (this) {
        is T -> "T^{${a.hint}}_{${i.hint}${j.hint}}"
        is F -> "f^{${a.hint}${b.hint}${c.hint}}"
        is D -> "\\delta_{${i.hint}${j.hint}}"
    }
}

/** One color structure times its Lorentz and Dirac part. */
class VertexTerm(val color: List<ColorFactor>, val expr: Expr)

/**
 * A leg of a vertex, seen from the vertex: [anti] is true when an oriented particle flows out
 * of the vertex (so an antiparticle comes in). [k] is the incoming momentum.
 */
class Leg(val particle: Particle, val anti: Boolean, val index: Idx, val k: Mom, val color: CIdx?) {
    val kind: String get() = particle.id + if (particle.oriented && anti) "~" else ""
    val momTex: String get() = MomNames.tex(k)
}

/** A rule found for a vertex or line: the paper's equation number, the factor and how it's written. */
class RuleUse(val eq: Int, val terms: List<VertexTerm>, val tex: String, /** For rules not from the paper (φ⁴, 2HDM, Z′). */ val label: String? = null) {
    /** "(67)" for the paper's rules, or the model's name for others. */
    val tag: String get() = label ?: "($eq)"
}

/** What the rules need to know besides the fields: the signs, the masses, the theory and the gauge. */
class RuleContext(
    val conv: Conventions,
    /** A fermion's mass, or null when it's massless (or the setting neglects it). */
    val massOf: (Particle) -> Sym? = { it.mass },
    /** V = 1: quarks of different generations don't couple to the W. */
    val ckmIdentity: Boolean = false,
    val names: IndexNames = IndexNames(),
    val theory: Theory = Theory.SM,
    val gauge: Gauge = Gauge.Feynman,
) {
    fun copy(massOf: (Particle) -> Sym? = this.massOf) = RuleContext(conv, massOf, ckmIdentity, names, theory, gauge)

    /** ξ as an expression: 1, 0, the symbol ξ (or 1 in the unitary gauge, where Goldstones and ghosts are absent). */
    val xi: Expr get() = when (gauge) {
        Gauge.Feynman, Gauge.Unitary -> Expr.ONE
        Gauge.Landau -> Expr.ZERO
        Gauge.General -> sym(Rules.xiSym)
    }
}

object Rules {
    val sqrt2 = Sym("sqrt2", "\\sqrt{2}", order = 5)
    /** The gauge parameter of a general Rξ gauge (one ξ for every gauge boson). */
    val xiSym = Sym("xi", "\\xi", order = 13)

    private val e get() = sym(Couplings.e)
    private val g get() = sym(Couplings.g)
    private val gs get() = sym(Couplings.gs)
    private val cW get() = sym(Couplings.cW)
    private val sW get() = sym(Couplings.sW)
    private val mW get() = sym(Couplings.mW)
    private val mZ get() = sym(Couplings.mZ)
    private val mh get() = sym(Couplings.mh)
    private val i get() = Expr.I
    private fun n(a: Long, b: Long = 1) = Expr.const(a, b)
    private fun inv(s: Sym, p: Int = 1) = sym(s, -p)
    /** cos 2θ_W = c_W² − s_W². */
    private val cos2 get() = cW * cW - sW * sW

    /** Reduces √2 powers: (√2)² = 2. */
    fun simplifyRoots(x: Expr): Expr = x.mapAtoms { a, p ->
        if (a == sqrt2 && (p >= 2 || p <= -2)) {
            val half = p / 2
            val rest = p - 2 * half
            Expr.const(Rational.of(2).pow(half)) * (if (rest == 0) Expr.ONE else sym(sqrt2, rest))
        } else null
    }

    // --- Lorentz structures ---------------------------------------------------------------

    /** g_{σρ}(p_- − p_+)_μ + g_{ρμ}(p_+ − q)_σ + g_{μσ}(q − p_-)_ρ for legs (σ, p_-), (ρ, p_+), (μ, q). */
    private fun triple(a: Leg, b: Leg, c: Leg): Expr =
        met(a.index, b.index) * vec(a.k - b.k, c.index) +
            met(b.index, c.index) * vec(b.k - c.k, a.index) +
            met(c.index, a.index) * vec(c.k - a.k, b.index)

    private fun tripleTex(a: Leg, b: Leg, c: Leg, x: IndexNames): String {
        fun m(p: Mom, l: Leg) = "\\left(${MomNames.tex(p)}\\right)_{${x.name(l.index)}}"
        return "\\left[g_{${x.name(a.index)}${x.name(b.index)}}${m(a.k - b.k, c)} + g_{${x.name(b.index)}${x.name(c.index)}}${m(b.k - c.k, a)} + g_{${x.name(c.index)}${x.name(a.index)}}${m(c.k - a.k, b)}\\right]"
    }

    /** 2g_{σρ}g_{μν} − g_{σμ}g_{ρν} − g_{σν}g_{ρμ}. */
    private fun quartic(s: Leg, r: Leg, m: Leg, v: Leg): Expr =
        met(s.index, r.index) * met(m.index, v.index) * CQ.of(2) -
            met(s.index, m.index) * met(r.index, v.index) - met(s.index, v.index) * met(r.index, m.index)

    private fun quarticTex(s: Leg, r: Leg, m: Leg, v: Leg, x: IndexNames): String {
        val (S, R, M, V) = listOf(s, r, m, v).map { x.name(it.index) }
        return "\\left[2g_{$S$R}g_{$M$V} - g_{$S$M}g_{$R$V} - g_{$S$V}g_{$R$M}\\right]"
    }

    private fun metric(a: Leg, b: Leg) = met(a.index, b.index)
    private fun metricTex(a: Leg, b: Leg, x: IndexNames) = "g_{${x.name(a.index)}${x.name(b.index)}}"

    private fun momDiff(a: Mom, b: Mom, l: Leg) = vec(a - b, l.index)
    private fun momDiffTex(a: Mom, b: Mom, l: Leg, x: IndexNames) = "\\left(${MomNames.tex(a - b)}\\right)_{${x.name(l.index)}}"

    private fun single(eq: Int, coef: Expr, structure: Expr, structureTex: String, color: List<ColorFactor> = emptyList()): RuleUse {
        val c = simplifyRoots(coef)
        val tex = combineTex(c, structureTex, color)
        return RuleUse(eq, listOf(VertexTerm(color, simplifyRoots(c * structure))), tex)
    }

    /** "−i e (…)" from a coefficient and the rest. */
    fun combineTex(coef: Expr, structureTex: String, color: List<ColorFactor> = emptyList()): String {
        val ct = Tex.of(coef)
        val colorTex = color.joinToString(" ") { it.tex() }
        val rest = listOf(colorTex, structureTex).filter { it.isNotEmpty() }.joinToString("\\,")
        return when {
            rest.isEmpty() -> ct
            ct == "1" -> rest
            ct == "-1" -> "-$rest"
            Tex.needsParen(ct) -> "\\left($ct\\right) $rest"
            else -> "$ct\\,$rest"
        }
    }

    // --- Vertices ---------------------------------------------------------------------------

    /** The rule for a vertex with these legs, or null if the Standard Model has none. */
    fun vertex(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        if (legs.any { !ctx.theory.allows(it.particle) }) return null
        return when (ctx.theory) {
            Theory.SM -> smVertex(legs, ctx)
            Theory.QED -> smVertex(legs, ctx)?.takeIf { it.eq == 67 }
            Theory.QCD -> smVertex(legs, ctx)?.takeIf { it.eq in 47..50 }
            Theory.Phi4 -> Models.phi4(legs)
            Theory.TwoHDM -> Models.twoHdm(legs, ctx)
            Theory.ZPrime -> if (legs.any { it.particle === BSM.zPrime }) Models.zPrime(legs, ctx) else smVertex(legs, ctx)
        }
    }

    /** The paper's rule for a vertex of Standard Model fields. */
    fun smVertex(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        val fermions = legs.filter { it.particle.isFermion }
        return when (fermions.size) {
            0 -> bosonic(legs, ctx)
            2 -> fermionic(legs, ctx)
            else -> null
        }
    }

    private fun fermionic(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        val r = fermionicRule(legs, ctx) ?: return null
        val out = legs.firstOrNull { it.particle.isFermion && it.anti } ?: return r
        val inn = legs.firstOrNull { it.particle.isFermion && !it.anti && it !== out } ?: return r
        // A colorless boson leaves the quark's color alone: δ_{ij}.
        if (out.color == null || inn.color == null || r.terms.any { t -> t.color.isNotEmpty() }) return r
        val delta = ColorFactor.D(out.color, inn.color)
        return RuleUse(r.eq, r.terms.map { VertexTerm(listOf(delta), it.expr) }, r.tex, r.label)
    }

    private fun fermionicRule(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        if (legs.size != 3) return null
        val out = legs.firstOrNull { it.particle.isFermion && it.anti } ?: return null
        val inn = legs.firstOrNull { it.particle.isFermion && !it.anti && it !== out } ?: return null
        val b = legs.first { it !== out && it !== inn }
        val c = ctx.conv
        val x = ctx.names
        val f = out.particle
        val fi = inn.particle
        val mu = b.index
        val mut = x.name(mu)
        if (f === fi) {
            val mf = ctx.massOf(f)
            return when (b.particle) {
                SM.photon -> {
                    if (f.charge.signum == 0) return null
                    single(67, i * e * Expr.const(f.charge) * CQ.of(-c.etaE.toLong()), gamma(mu), "\\gamma_{$mut}")
                }
                SM.z -> {
                    val gv = Expr.const(f.t3 * Rational.of(1, 2)) - Expr.const(f.charge) * sW * sW
                    val ga = Expr.const(f.t3 * Rational.of(1, 2))
                    val coef = i * g * inv(Couplings.cW) * CQ.of(-(c.eta * c.etaZ).toLong())
                    single(68, coef, gamma(mu).dirac(diracOne * gv - gamma5 * ga),
                        "\\gamma_{$mut}\\left(${Tex.of(gv)} - ${Tex.paren(Tex.of(ga))}\\gamma^{5}\\right)")
                }
                SM.higgs -> {
                    mf ?: return null
                    single(70, i * g * sym(mf) * inv(Couplings.mW) * Expr.const(-1, 2), diracOne, "")
                }
                SM.phiZ -> {
                    mf ?: return null
                    single(71, g * Expr.const(-f.t3) * sym(mf) * inv(Couplings.mW), gamma5, "\\gamma^{5}")
                }
                SM.gluon -> {
                    if (f.color != ColorRep.Triplet) return null
                    val t = ColorFactor.T(b.color!!, out.color!!, inn.color!!)
                    single(49, i * gs * CQ.of(-c.etaS.toLong()), gamma(mu), "\\gamma^{$mut}", listOf(t))
                }
                else -> null
            }
        }
        // Charged currents: the W or φ± carries the difference of the charges.
        val wIn = b.particle === SM.w
        val phiIn = b.particle === SM.phi
        if (!wIn && !phiIn) return null
        val plus = !b.anti // an incoming W⁺ or φ⁺
        if (f.charge - fi.charge != if (plus) Rational.ONE else Rational.of(-1)) return null
        val quarks = f.color == ColorRep.Triplet && fi.color == ColorRep.Triplet
        val leptons = f.color == ColorRep.None && fi.color == ColorRep.None
        if (!quarks && !leptons) return null
        val up = if (plus) f else fi // the up-type (or neutrino) end
        val down = if (plus) fi else f
        if (leptons && up.generation != down.generation) return null
        val ckm: Expr = if (!quarks) Expr.ONE
        else if (ctx.ckmIdentity) { if (up.generation != down.generation) return null; Expr.ONE }
        else sym(Couplings.ckm(up, down).let { if (plus) it else it.conj() })
        val ckmTex = if (ckm == Expr.ONE) "" else Tex.of(ckm)
        val invSqrt2 = sym(sqrt2, -1)
        if (wIn) {
            val coef = i * g * invSqrt2 * ckm * CQ.of(-c.eta.toLong())
            return single(if (!quarks) 66 else if (plus) 64 else 65, coef, gamma(mu).dirac(projL), "\\gamma_{$mut}P_L")
        }
        // Goldstone φ±: (72)–(74).
        val mu_ = ctx.massOf(up)?.let { sym(it) } ?: Expr.ZERO
        val md = ctx.massOf(down)?.let { sym(it) } ?: Expr.ZERO
        val left = if (plus) mu_ else md * CQ.of(-1) // coefficient of P_L
        val right = if (plus) md * CQ.of(-1) else mu_ // of P_R
        val dirac = (projL * left + projR * right) * inv(Couplings.mW)
        if (dirac.isZero) return null
        val coef = i * g * invSqrt2 * ckm
        val texBits = listOfNotNull(
            if (left.isZero) null else "${Tex.of(left)} P_L",
            if (right.isZero) null else "${Tex.of(right)} P_R",
        ).joinToString(" + ").replace("+ -", "- ")
        val eq = if (!quarks) 74 else if (plus) 72 else 73
        return single(eq, coef, dirac, "\\frac{1}{m_W}\\left($texBits\\right)")
    }

    private fun bosonic(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        val c = ctx.conv
        val x = ctx.names
        val by = legs.groupBy { it.kind }
        fun one(k: String) = by[k]!![0]
        fun two(k: String) = by[k]!![1]
        val sig = legs.map { it.kind }.sorted().joinToString(",")
        val eta = c.eta.toLong(); val etaZ = c.etaZ.toLong(); val etaE = c.etaE.toLong(); val etaS = c.etaS.toLong(); val etaG = c.etaG.toLong()
        return when (sig) {
            // QCD, (47) and (48).
            "g,g,g" -> {
                val (a, b, cc) = legs
                val color = listOf(ColorFactor.F(a.color!!, b.color!!, cc.color!!))
                val tex = "\\left[g^{${x.name(a.index)}${x.name(b.index)}}\\left(${MomNames.tex(a.k - b.k)}\\right)^{${x.name(cc.index)}} + g^{${x.name(b.index)}${x.name(cc.index)}}\\left(${MomNames.tex(b.k - cc.k)}\\right)^{${x.name(a.index)}} + g^{${x.name(cc.index)}${x.name(a.index)}}\\left(${MomNames.tex(cc.k - a.k)}\\right)^{${x.name(b.index)}}\\right]"
                single(47, gs * CQ.of(-etaS), triple(a, b, cc), tex, color)
            }
            "g,g,g,g" -> {
                val (a, b, cc, d) = legs
                val e = CIdx.internal("e")
                fun f(p: CIdx, q: CIdx, r: CIdx) = ColorFactor.F(p, q, r)
                fun g2(l1: Leg, l2: Leg, l3: Leg, l4: Leg) = metric(l1, l2) * metric(l3, l4)
                val coef = i * gs * gs * CQ.of(-1)
                // f_{eab}f_{ecd}(g_{μρ}g_{νσ} − g_{μσ}g_{νρ}) + f_{eac}f_{edb}(g_{μσ}g_{ρν} − g_{μν}g_{ρσ}) + f_{ead}f_{ebc}(g_{μν}g_{ρσ} − g_{μρ}g_{νσ})
                val terms = listOf(
                    VertexTerm(listOf(f(e, a.color!!, b.color!!), f(e, cc.color!!, d.color!!)), coef * (g2(a, cc, b, d) - g2(a, d, b, cc))),
                    VertexTerm(listOf(f(e, a.color, cc.color), f(e, d.color, b.color)), coef * (g2(a, d, cc, b) - g2(a, b, cc, d))),
                    VertexTerm(listOf(f(e, a.color, d.color), f(e, b.color, cc.color)), coef * (g2(a, b, cc, d) - g2(a, cc, b, d))),
                )
                val (M, N, R, S) = listOf(a, b, cc, d).map { x.name(it.index) }
                val (A, B, C, D) = listOf(a, b, cc, d).map { it.color!!.hint }
                val tex = "-i g_s^{2}\\left[f^{e$A$B}f^{e$C$D}\\left(g_{$M$R}g_{$N$S} - g_{$M$S}g_{$N$R}\\right) + f^{e$A$C}f^{e$D$B}\\left(g_{$M$S}g_{$R$N} - g_{$M$N}g_{$R$S}\\right) + f^{e$A$D}f^{e$B$C}\\left(g_{$M$N}g_{$R$S} - g_{$M$R}g_{$N$S}\\right)\\right]"
                RuleUse(48, terms, tex)
            }
            // Gluon ghosts, (50): p is the outgoing ghost's momentum.
            "g,om,om~" -> {
                val out = one("om~"); val inn = one("om"); val gl = one("g")
                val p = -out.k
                val color = listOf(ColorFactor.F(out.color!!, inn.color!!, gl.color!!))
                single(50, gs * CQ.of(-etaS * etaG), vec(p, gl.index), "${MomNames.tex(p).let { if (p.size > 1) "\\left($it\\right)" else it }}^{${x.name(gl.index)}}", color)
            }
            // Triple gauge, (58) and (59).
            "A,W,W~" -> single(58, i * e * CQ.of(-etaE), triple(one("W~"), one("W"), one("A")), tripleTex(one("W~"), one("W"), one("A"), x))
            "W,W~,Z" -> single(59, i * g * cW * CQ.of(-eta * etaZ), triple(one("W~"), one("W"), one("Z")), tripleTex(one("W~"), one("W"), one("Z"), x))
            // Quartic gauge, (60)–(63).
            "A,A,W,W~" -> single(60, i * e * e * CQ.of(-1), quartic(one("W"), one("W~"), one("A"), two("A")), quarticTex(one("W"), one("W~"), one("A"), two("A"), x))
            "W,W~,Z,Z" -> single(61, i * g * g * cW * cW * CQ.of(-1), quartic(one("W"), one("W~"), one("Z"), two("Z")), quarticTex(one("W"), one("W~"), one("Z"), two("Z"), x))
            "A,W,W~,Z" -> single(62, i * e * g * cW * CQ.of(-etaE * eta * etaZ), quartic(one("W"), one("W~"), one("A"), one("Z")), quarticTex(one("W"), one("W~"), one("A"), one("Z"), x))
            "W,W,W~,W~" -> {
                // i g²[2g_{σμ}g_{ρν} − g_{σρ}g_{μν} − g_{σν}g_{ρμ}] with W⁺_σ, W⁻_ρ, W⁺_μ, W⁻_ν.
                val s = one("W"); val r = one("W~"); val m = two("W"); val v = two("W~")
                val st = met(s.index, m.index) * met(r.index, v.index) * CQ.of(2) - met(s.index, r.index) * met(m.index, v.index) - met(s.index, v.index) * met(r.index, m.index)
                val (S, R, M, V) = listOf(s, r, m, v).map { x.name(it.index) }
                single(63, i * g * g, st, "\\left[2g_{$S$M}g_{$R$V} - g_{$S$R}g_{$M$V} - g_{$S$V}g_{$R$M}\\right]")
            }
            // Triple Higgs/Goldstone–gauge, (75)–(83).
            "A,phi,phi~" -> single(75, i * e * CQ.of(-etaE), momDiff(one("phi").k, one("phi~").k, one("A")), momDiffTex(one("phi").k, one("phi~").k, one("A"), x))
            "Z,phi,phi~" -> single(76, i * g * cos2 * inv(Couplings.cW) * CQ.of(-eta * etaZ) * Rational.of(1, 2), momDiff(one("phi").k, one("phi~").k, one("Z")), momDiffTex(one("phi").k, one("phi~").k, one("Z"), x))
            "W,h,phi~" -> single(77, i * g * n(eta, 2), momDiff(one("phi~").k, one("h").k, one("W")), momDiffTex(one("phi~").k, one("h").k, one("W"), x))
            "W~,h,phi" -> single(77, i * g * n(-eta, 2), momDiff(one("phi").k, one("h").k, one("W~")), momDiffTex(one("phi").k, one("h").k, one("W~"), x))
            "W,phi~,phiZ" -> single(78, g * n(-eta, 2), momDiff(one("phi~").k, one("phiZ").k, one("W")), momDiffTex(one("phi~").k, one("phiZ").k, one("W"), x))
            "W~,phi,phiZ" -> single(78, g * n(-eta, 2), momDiff(one("phi").k, one("phiZ").k, one("W~")), momDiffTex(one("phi").k, one("phiZ").k, one("W~"), x))
            "Z,h,phiZ" -> single(79, g * inv(Couplings.cW) * n(-eta * etaZ, 2), momDiff(one("phiZ").k, one("h").k, one("Z")), momDiffTex(one("phiZ").k, one("h").k, one("Z"), x))
            "A,W,phi~" -> single(80, i * e * mW * CQ.of(etaE * eta), metric(one("W"), one("A")), metricTex(one("A"), one("W"), x))
            "A,W~,phi" -> single(80, i * e * mW * CQ.of(etaE * eta), metric(one("W~"), one("A")), metricTex(one("A"), one("W~"), x))
            "W,Z,phi~" -> single(81, i * g * mZ * sW * sW * CQ.of(-etaZ), metric(one("W"), one("Z")), metricTex(one("Z"), one("W"), x))
            "W~,Z,phi" -> single(81, i * g * mZ * sW * sW * CQ.of(-etaZ), metric(one("W~"), one("Z")), metricTex(one("Z"), one("W~"), x))
            "W,W~,h" -> single(82, i * g * mW, metric(one("W"), one("W~")), metricTex(one("W"), one("W~"), x))
            "Z,Z,h" -> single(83, i * g * mZ * inv(Couplings.cW), metric(one("Z"), two("Z")), metricTex(one("Z"), two("Z"), x))
            // Quartic Higgs/Goldstone–gauge, (84)–(95).
            "W,W~,h,h" -> single(84, i * g * g * Rational.of(1, 2), metric(one("W"), one("W~")), metricTex(one("W"), one("W~"), x))
            "W,W~,phiZ,phiZ" -> single(85, i * g * g * Rational.of(1, 2), metric(one("W"), one("W~")), metricTex(one("W"), one("W~"), x))
            "Z,Z,h,h" -> single(86, i * g * g * inv(Couplings.cW, 2) * Rational.of(1, 2), metric(one("Z"), two("Z")), metricTex(one("Z"), two("Z"), x))
            "Z,Z,phiZ,phiZ" -> single(87, i * g * g * inv(Couplings.cW, 2) * Rational.of(1, 2), metric(one("Z"), two("Z")), metricTex(one("Z"), two("Z"), x))
            "A,A,phi,phi~" -> single(88, i * e * e * CQ.of(2), metric(one("A"), two("A")), metricTex(one("A"), two("A"), x))
            "Z,Z,phi,phi~" -> single(89, i * g * g * cos2 * cos2 * inv(Couplings.cW, 2) * Rational.of(1, 2), metric(one("Z"), two("Z")), metricTex(one("Z"), two("Z"), x))
            "W,W~,phi,phi~" -> single(90, i * g * g * Rational.of(1, 2), metric(one("W"), one("W~")), metricTex(one("W"), one("W~"), x))
            "W,Z,h,phi~" -> single(91, i * g * g * sW * sW * inv(Couplings.cW) * CQ.of(-etaZ) * Rational.of(1, 2), metric(one("W"), one("Z")), metricTex(one("W"), one("Z"), x))
            "W~,Z,h,phi" -> single(91, i * g * g * sW * sW * inv(Couplings.cW) * CQ.of(-etaZ) * Rational.of(1, 2), metric(one("W~"), one("Z")), metricTex(one("W~"), one("Z"), x))
            "W~,Z,phi,phiZ" -> single(92, g * g * sW * sW * inv(Couplings.cW) * CQ.of(-etaZ) * Rational.of(1, 2), metric(one("W~"), one("Z")), metricTex(one("W~"), one("Z"), x))
            "W,Z,phi~,phiZ" -> single(92, g * g * sW * sW * inv(Couplings.cW) * CQ.of(etaZ) * Rational.of(1, 2), metric(one("W"), one("Z")), metricTex(one("W"), one("Z"), x))
            "A,W~,h,phi" -> single(93, i * e * g * CQ.of(etaE * eta) * Rational.of(1, 2), metric(one("W~"), one("A")), metricTex(one("W~"), one("A"), x))
            "A,W,h,phi~" -> single(93, i * e * g * CQ.of(etaE * eta) * Rational.of(1, 2), metric(one("W"), one("A")), metricTex(one("W"), one("A"), x))
            "A,W,phi~,phiZ" -> single(94, e * g * CQ.of(-etaE * eta) * Rational.of(1, 2), metric(one("W"), one("A")), metricTex(one("W"), one("A"), x))
            "A,W~,phi,phiZ" -> single(94, e * g * CQ.of(etaE * eta) * Rational.of(1, 2), metric(one("W~"), one("A")), metricTex(one("W~"), one("A"), x))
            "A,Z,phi,phi~" -> single(95, i * e * g * cos2 * inv(Couplings.cW) * CQ.of(etaE * eta * etaZ), metric(one("Z"), one("A")), metricTex(one("Z"), one("A"), x))
            // Triple Higgs and Goldstone, (96)–(98).
            "h,phi,phi~" -> single(96, i * g * mh * mh * inv(Couplings.mW) * Rational.of(-1, 2), Expr.ONE, "")
            "h,h,h" -> single(97, i * g * mh * mh * inv(Couplings.mW) * Rational.of(-3, 2), Expr.ONE, "")
            "h,phiZ,phiZ" -> single(98, i * g * mh * mh * inv(Couplings.mW) * Rational.of(-1, 2), Expr.ONE, "")
            // Quartic Higgs and Goldstone, (99)–(104).
            "phi,phi,phi~,phi~" -> single(99, i * g * g * mh * mh * inv(Couplings.mW, 2) * Rational.of(-1, 2), Expr.ONE, "")
            "h,h,phi,phi~" -> single(100, i * g * g * mh * mh * inv(Couplings.mW, 2) * Rational.of(-1, 4), Expr.ONE, "")
            "phi,phi~,phiZ,phiZ" -> single(101, i * g * g * mh * mh * inv(Couplings.mW, 2) * Rational.of(-1, 4), Expr.ONE, "")
            "h,h,h,h" -> single(102, i * g * g * mh * mh * inv(Couplings.mW, 2) * Rational.of(-3, 4), Expr.ONE, "")
            "h,h,phiZ,phiZ" -> single(103, i * g * g * mh * mh * inv(Couplings.mW, 2) * Rational.of(-1, 4), Expr.ONE, "")
            "phiZ,phiZ,phiZ,phiZ" -> single(104, i * g * g * mh * mh * inv(Couplings.mW, 2) * Rational.of(-3, 4), Expr.ONE, "")
            // Ghost–gauge, (108)–(113): p is the outgoing ghost's momentum.
            "A,cp,cp~" -> ghost(108, one("cp~"), one("A"), i * e * CQ.of(-etaG * etaE), x)
            "A,cm,cm~" -> ghost(108, one("cm~"), one("A"), i * e * CQ.of(etaG * etaE), x)
            "Z,cp,cp~" -> ghost(109, one("cp~"), one("Z"), i * g * cW * CQ.of(-etaG * eta * etaZ), x)
            "Z,cm,cm~" -> ghost(109, one("cm~"), one("Z"), i * g * cW * CQ.of(etaG * eta * etaZ), x)
            "W,cZ,cp~" -> ghost(110, one("cp~"), one("W"), i * g * cW * CQ.of(etaG * eta * etaZ), x)
            "W~,cZ,cm~" -> ghost(110, one("cm~"), one("W~"), i * g * cW * CQ.of(-etaG * eta * etaZ), x)
            "W,cA,cp~" -> ghost(111, one("cp~"), one("W"), i * e * CQ.of(etaG * etaE), x)
            "W~,cA,cm~" -> ghost(111, one("cm~"), one("W~"), i * e * CQ.of(-etaG * etaE), x)
            "W~,cZ~,cp" -> ghost(112, one("cZ~"), one("W~"), i * g * cW * CQ.of(etaG * eta), x)
            "W,cZ~,cm" -> ghost(112, one("cZ~"), one("W"), i * g * cW * CQ.of(-etaG * eta), x)
            "W~,cA~,cp" -> ghost(113, one("cA~"), one("W~"), i * e * CQ.of(etaG * etaE), x)
            "W,cA~,cm" -> ghost(113, one("cA~"), one("W"), i * e * CQ.of(-etaG * etaE), x)
            // Ghost–Higgs and ghost–Goldstone, (114)–(119), with ξ = 1.
            "cp,cp~,phiZ" -> single(114, g * mW * CQ.of(etaG) * Rational.of(1, 2) * ctx.xi, Expr.ONE, "")
            "cm,cm~,phiZ" -> single(114, g * mW * CQ.of(-etaG) * Rational.of(1, 2) * ctx.xi, Expr.ONE, "")
            "cp,cp~,h", "cm,cm~,h" -> single(115, i * g * mW * CQ.of(-etaG) * Rational.of(1, 2) * ctx.xi, Expr.ONE, "")
            "cZ,cZ~,h" -> single(116, i * g * mZ * inv(Couplings.cW) * CQ.of(-etaG) * Rational.of(1, 2) * ctx.xi, Expr.ONE, "")
            "cZ~,cp,phi~", "cZ~,cm,phi" -> single(117, i * g * mZ * CQ.of(etaG * etaZ) * Rational.of(1, 2) * ctx.xi, Expr.ONE, "")
            "cZ,cp~,phi", "cZ,cm~,phi~" -> single(118, i * g * cos2 * mW * inv(Couplings.cW) * CQ.of(-etaG * etaZ) * Rational.of(1, 2) * ctx.xi, Expr.ONE, "")
            "cA,cp~,phi", "cA,cm~,phi~" -> single(119, i * e * mW * CQ.of(-etaG * etaE * eta) * ctx.xi, Expr.ONE, "")
            else -> null
        }
    }

    /** A ghost–gauge vertex: coefficient × p_μ, p the outgoing ghost's momentum. */
    private fun ghost(eq: Int, out: Leg, boson: Leg, coef: Expr, x: IndexNames): RuleUse {
        val p = -out.k
        val pt = MomNames.tex(p).let { if (p.size > 1) "\\left($it\\right)" else it }
        return single(eq, coef, vec(p, boson.index), "${pt}_{${x.name(boson.index)}}")
    }

    // --- Propagators -----------------------------------------------------------------------

    /**
     * The propagator of an internal line carrying momentum [q] from its start to its end, with
     * Lorentz indices [from] and [to] at the two ends (Feynman–'t Hooft gauge, ξ = 1).
     */
    fun propagator(p: Particle, q: Mom, from: Idx, to: Idx, ctx: RuleContext): RuleUse {
        val x = ctx.names
        val c = ctx.conv
        val etaG = c.etaG.toLong()
        return when (p.spin) {
            Spin.Fermion -> {
                val m = ctx.massOf(p)
                val den = propagatorDen(q, m)
                val num = slash(q) + (if (m == null) Expr.ZERO else sym(m) * diracOne)
                // k̸ + p̸₁ − …, each momentum slashed on its own.
                val numTex = q.entries.sortedBy { MomNames.order(it.key) }.withIndex().joinToString(" ") { (i, e) ->
                    val c = e.value
                    val sign = if (c.signum < 0) "-" else if (i > 0) "+" else ""
                    val a = c.abs()
                    "$sign ${if (a == Rational.ONE) "" else Tex.rational(a)}${Tex.slashTex(e.key)}".trim()
                } + (if (m == null) "" else " + ${m.tex}")
                RuleUse(54, listOf(VertexTerm(emptyList(), num * den * Expr.I)),
                    "\\frac{i\\left($numTex\\right)}{${denTex(den)}}")
            }
            Spin.Vector -> {
                val mass = if (p === SM.photon || p === SM.gluon) null else p.mass
                val eq = when (p) { SM.gluon -> 45; SM.photon -> 51; SM.w -> 52; SM.z -> 53; else -> 53 }
                val label = if (p === BSM.zPrime) "Z′ propagator" else null
                val delta = if (p === SM.gluon) "\\delta_{ab}" else ""
                val (M, N) = x.name(from) to x.name(to)
                val den = propagatorDen(q, mass)
                val metric = met(from, to) * den * CQ.imag(-1)
                val kk = vec(q, from) * vec(q, to)
                val qt = MomNames.tex(q).let { if (q.size > 1) "\\left($it\\right)" else it }
                when {
                    ctx.gauge == Gauge.Feynman || (ctx.gauge == Gauge.Unitary && mass == null) ->
                        RuleUse(eq, listOf(VertexTerm(emptyList(), metric)), "\\frac{-i $delta g_{$M$N}}{${denTex(den)}}", label)
                    ctx.gauge == Gauge.Unitary -> {
                        // −i(g − kk/M²)/(k² − M²)
                        val t = metric - kk * den * sym(mass!!, -2) * CQ.imag(-1)
                        RuleUse(eq, listOf(VertexTerm(emptyList(), t)),
                            "\\frac{-i $delta}{${denTex(den)}}\\left[g_{$M$N} - \\frac{${qt}_{$M}${qt}_{$N}}{${mass.tex}^{2}}\\right]", label)
                    }
                    else -> {
                        // −i/(k² − M²) [g − (1 − ξ) kk/(k² − ξM²)]
                        val xi = ctx.xi
                        val m2 = if (mass == null) Expr.ZERO else sym(mass, 2)
                        val xiTex = if (ctx.gauge == Gauge.Landau) "" else "\\xi "
                        val den2 = propagatorDen(q, xi * m2, if (mass == null || ctx.gauge == Gauge.Landau) null else "\\xi ${mass.tex}^{2}")
                        val t = metric + kk * den * den2 * (Expr.ONE - xi) * CQ.I
                        val oneMinus = if (ctx.gauge == Gauge.Landau) "" else "(1 - \\xi)"
                        RuleUse(eq, listOf(VertexTerm(emptyList(), Rules.simplifyRoots(t))),
                            "\\frac{-i $delta}{${denTex(den)}}\\left[g_{$M$N} - $oneMinus\\frac{${qt}_{$M}${qt}_{$N}}{${denTex(den2)}}\\right]".replace("$xiTex$xiTex", xiTex), label)
                    }
                }
            }
            Spin.Scalar -> {
                // Goldstones (and ghosts) have mass² ξM² in an Rξ gauge.
                val goldstone = p === SM.phiZ || p === SM.phi
                val m2 = p.mass?.let { sym(it, 2) * (if (goldstone) ctx.xi else Expr.ONE) } ?: Expr.ZERO
                val m2Tex = p.mass?.let { if (goldstone && ctx.gauge == Gauge.General) "\\xi ${it.tex}^{2}" else "${it.tex}^{2}" }
                val den = propagatorDen(q, m2, m2Tex)
                val eq = when (p) { SM.higgs -> 55; SM.phiZ -> 56; SM.phi -> 57; else -> 55 }
                val label = when (p) { BSM.phi4 -> "φ propagator"; BSM.heavyH, BSM.pseudoA, BSM.chargedH -> "2HDM scalar"; else -> null }
                RuleUse(eq, listOf(VertexTerm(emptyList(), den * Expr.I)), "\\frac{i}{${denTex(den)}}", label)
            }
            Spin.Ghost -> {
                val m2 = p.mass?.let { sym(it, 2) * ctx.xi } ?: Expr.ZERO
                val m2Tex = p.mass?.let { if (ctx.gauge == Gauge.General) "\\xi ${it.tex}^{2}" else "${it.tex}^{2}" }
                val den = propagatorDen(q, m2, m2Tex)
                val eq = when (p) { SM.ghostG -> 46; SM.ghostA -> 105; SM.ghostZ -> 107; else -> 106 }
                val delta = if (p === SM.ghostG) "\\delta_{ab}" else ""
                RuleUse(eq, listOf(VertexTerm(emptyList(), den * CQ.imag(etaG))),
                    "\\frac{${if (etaG < 0) "-" else ""}i $delta}{${denTex(den)}}")
            }
        }
    }

    private fun denTex(den: Expr): String = (den.terms.keys.first().mono.factors.first().first as Den).let { it.display ?: Tex.of(it.content) }
}
