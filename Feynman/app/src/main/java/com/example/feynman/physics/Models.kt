package com.example.feynman.physics

/*
 * Theories besides the Standard Model's rules:
 *
 *  - φ⁴ (and φ³): one real scalar, −iλ at four lines and −iκ at three.
 *  - The type-II two-Higgs-doublet model: h, H, A and H± with couplings as in Gunion, Haber,
 *    Kane & Dawson, "The Higgs Hunter's Guide": each is a Standard Model rule of Romão & Silva
 *    (H read as h, A as φ_Z, H± as φ±) times a factor of α and β. hVV ∝ sin(β−α), HVV ∝ cos(β−α);
 *    Yukawa couplings h: cos α/sin β (up), −sin α/cos β (down, leptons); H: sin α/sin β, cos α/cos β;
 *    A and H±: the Goldstone's times cot β (up) and −tan β (down). The extra scalars'
 *    self-couplings depend on the potential and aren't included.
 *  - A sequential Z′: the Z's couplings to fermions, rule (68), times g_{Z′}/g.
 */
object Models {
    private val ca get() = sym(BSM.cAlpha)
    private val sa get() = sym(BSM.sAlpha)
    private val cb get() = sym(BSM.cBeta)
    private val sb get() = sym(BSM.sBeta)
    /** cos(β − α) and sin(β − α). */
    val cba get() = cb * ca + sb * sa
    val sba get() = sb * ca - cb * sa

    fun phi4(legs: List<Leg>): RuleUse? {
        if (legs.any { it.particle !== BSM.phi4 }) return null
        return when (legs.size) {
            4 -> RuleUse(0, listOf(VertexTerm(emptyList(), sym(BSM.lambda) * CQ.imag(-1))), "-i\\lambda", "φ⁴ vertex")
            3 -> RuleUse(0, listOf(VertexTerm(emptyList(), sym(BSM.kappa) * CQ.imag(-1))), "-i\\kappa", "φ³ vertex")
            else -> null
        }
    }

    private fun scaled(r: RuleUse, factor: Expr, label: String): RuleUse {
        if (factor == Expr.ONE) return RuleUse(r.eq, r.terms, r.tex, label)
        val ft = Tex.paren(Tex.of(factor))
        return RuleUse(r.eq, r.terms.map { VertexTerm(it.color, Rules.simplifyRoots(it.expr * factor)) }, "$ft\\times\\left(${r.tex}\\right)", label)
    }

    fun zPrime(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        val mapped = legs.map { if (it.particle === BSM.zPrime) Leg(SM.z, it.anti, it.index, it.k, it.color) else it }
        val r = Rules.smVertex(mapped, ctx) ?: return null
        if (r.eq != 68) return null
        return scaled(r, sym(BSM.gZp) * sym(Couplings.g, -1), "Z′ (68)")
    }

    fun twoHdm(legs: List<Leg>, ctx: RuleContext): RuleUse? {
        fun map(p: Particle) = when (p) { BSM.heavyH -> SM.higgs; BSM.pseudoA -> SM.phiZ; BSM.chargedH -> SM.phi; else -> p }
        val mapped = legs.map { Leg(map(it.particle), it.anti, it.index, it.k, it.color) }
        val hasH = legs.any { it.particle === BSM.heavyH }
        val hasA = legs.any { it.particle === BSM.pseudoA }
        val hasHp = legs.any { it.particle === BSM.chargedH }
        val newScalars = legs.count { it.particle in BSM.all }
        val fermion = legs.firstOrNull { it.particle.isFermion }?.particle
        val up = fermion?.family == Family.UpQuark || fermion?.family == Family.Neutrino
        val r = Rules.smVertex(mapped, ctx) ?: return null
        val tag = "2HDM (${r.eq})"
        // The scalars in the vertex (after mapping, in pairs): h-like and Goldstone-like.
        val hLike = legs.firstOrNull { it.particle === SM.higgs || it.particle === BSM.heavyH }?.particle
        val gLike = legs.firstOrNull { it.particle === SM.phiZ || it.particle === SM.phi || it.particle === BSM.pseudoA || it.particle === BSM.chargedH }?.particle
        val gNew = gLike === BSM.pseudoA || gLike === BSM.chargedH
        val factor: Expr? = when (r.eq) {
            70 -> if (up) (if (hasH) sa / sb else ca / sb) else (if (hasH) ca / cb else sa / cb * CQ.of(-1))
            71 -> if (hasA) (if (up) cb / sb else sb / cb * CQ.of(-1)) else Expr.ONE
            72, 73, 74 -> if (!hasHp) Expr.ONE else return chargedYukawa(legs, mapped, ctx, r)
            82, 83, 115, 116 -> if (hasH) cba else sba
            77, 79, 91, 93 -> when {
                hLike === SM.higgs && !gNew -> sba
                hLike === BSM.heavyH && !gNew -> cba
                hLike === SM.higgs && gNew -> cba
                else -> sba * CQ.of(-1)
            }
            78, 92, 94 -> if ((hasA && hasHp) || (!hasA && !hasHp)) Expr.ONE else null
            75, 76, 88, 89, 90, 95 -> if (newScalars == 0 || legs.count { it.particle === BSM.chargedH } == 2) Expr.ONE else null
            80, 81, 114, 117, 118, 119 -> if (newScalars == 0) Expr.ONE else null
            84, 86 -> if (legs.count { it.particle === BSM.heavyH } != 1) Expr.ONE else null
            85, 87 -> if (legs.count { it.particle === BSM.pseudoA } != 1) Expr.ONE else null
            in 96..104 -> if (newScalars == 0) Expr.ONE else null
            else -> Expr.ONE
        }
        factor ?: return null
        return scaled(r, factor, tag)
    }

    /** H±: the up-mass part of (72)–(74) times cot β and the down-mass part times −tan β. */
    private fun chargedYukawa(legs: List<Leg>, mapped: List<Leg>, ctx: RuleContext, r: RuleUse): RuleUse? {
        fun upLike(p: Particle) = p.family == Family.UpQuark || p.family == Family.Neutrino
        val upOnly = Rules.smVertex(mapped, ctx.copy(massOf = { p -> if (upLike(p)) ctx.massOf(p) else null }))
        val downOnly = Rules.smVertex(mapped, ctx.copy(massOf = { p -> if (!upLike(p)) ctx.massOf(p) else null }))
        val cot = sym(BSM.cBeta) / sym(BSM.sBeta)
        val tan = sym(BSM.sBeta) / sym(BSM.cBeta) * CQ.of(-1)
        val terms = (upOnly?.terms.orEmpty().map { VertexTerm(it.color, it.expr * cot) } + downOnly?.terms.orEmpty().map { VertexTerm(it.color, it.expr * tan) })
        if (terms.isEmpty()) return null
        val parts = listOfNotNull(upOnly?.let { "\\cot\\beta\\left(${it.tex}\\right)" }, downOnly?.let { "\\tan\\beta\\left(${it.tex}\\right)" })
        return RuleUse(r.eq, terms, parts.joinToString(" - "), "2HDM H± (${r.eq})")
    }

    private operator fun Expr.div(o: Expr): Expr {
        // Only single symbols are divided by here.
        val (k, c) = o.terms.entries.single()
        return this * Expr(mapOf(TermKey(k.mono.inverse(), emptyList()) to c.inverse()))
    }
}
