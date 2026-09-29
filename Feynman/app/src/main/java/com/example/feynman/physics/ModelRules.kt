package com.example.feynman.physics

/*
 * The rules of the theories beyond the paper, written out for the Rules panel as the paper
 * writes its own (legs as drawn: three legs upper left, lower left, right; momenta incoming).
 * Each entry's label is the name its vertex carries in a solution.
 */
object ModelRules {
    private fun l(p: String, label: String, into: Boolean = true) = RuleCatalog.RLeg(p, into, label)
    private fun e(label: String, section: String, legs: List<RuleCatalog.RLeg>, tex: String, involves: ((Particle) -> Boolean)? = null) =
        RuleCatalog.Entry(0, section, legs, tex, label, involves)
    private fun prop(label: String, section: String, p: String, tex: String, involves: ((Particle) -> Boolean)? = null) =
        RuleCatalog.Entry(0, section, listOf(l(p, ""), l(p, "")), tex, label, involves)

    private val isSf = { p: Particle -> SUSY.sf(p) != null }
    private val isSfL = { p: Particle -> SUSY.sf(p)?.left == true }
    private val isSquark = { p: Particle -> SUSY.sf(p) != null && p.color == ColorRep.Triplet }
    private val isNeutralino = { p: Particle -> p in SUSY.neutralinos }
    private val isChargino = { p: Particle -> p in SUSY.charginos }
    private fun any(vararg f: (Particle) -> Boolean) = { p: Particle -> f.any { it(p) } }
    private fun only(vararg ps: Particle) = { p: Particle -> ps.any { it === p } }

    // --- φ⁴ ------------------------------------------------------------------------------------

    private const val PHI = "Scalar φ⁴ theory"
    val phi4 = listOf(
        prop("φ propagator", PHI, "phi4", "\\frac{i}{p^{2} - m^{2} + i\\epsilon}", only(BSM.phi4)),
        e("φ³ vertex", PHI, listOf(l("phi4", "\\phi"), l("phi4", "\\phi"), l("phi4", "\\phi")), "-i\\kappa", only(BSM.phi4)),
        e("φ⁴ vertex", PHI, listOf(l("phi4", "\\phi"), l("phi4", "\\phi"), l("phi4", "\\phi"), l("phi4", "\\phi")), "-i\\lambda", only(BSM.phi4)),
    )

    // --- Two Higgs doublets ------------------------------------------------------------------

    private const val THDM = "Two Higgs doublets (type II)"
    private val heavy = only(BSM.heavyH, BSM.pseudoA, BSM.chargedH)
    val twoHdm = listOf(
        prop("2HDM scalar", THDM, "H", "\\frac{i}{p^{2} - m_{H,A,H^\\pm}^{2} + i\\epsilon}", heavy),
        e("2HDM (70)", THDM, listOf(l("e", "f", false), l("e", "f"), l("H", "h, H")),
            "-i\\frac{g}{2}\\frac{m_f}{m_W}\\times\\begin{cases}\\cos\\alpha/\\sin\\beta,\\ \\sin\\alpha/\\sin\\beta & (u)\\\\ -\\sin\\alpha/\\cos\\beta,\\ \\cos\\alpha/\\cos\\beta & (d, \\ell)\\end{cases}",
            any(only(BSM.heavyH, SM.higgs), { it.isFermion })),
        e("2HDM (71)", THDM, listOf(l("e", "f", false), l("e", "f"), l("Ah", "A")),
            "-gT^3_f\\frac{m_f}{m_W}\\gamma_5\\times\\begin{cases}\\cot\\beta & (u)\\\\ -\\tan\\beta & (d, \\ell)\\end{cases}", any(only(BSM.pseudoA), { it.isFermion })),
        e("2HDM H± (72)", THDM, listOf(l("u", "u_\\alpha", false), l("d", "d_\\beta"), l("Hp", "H^+")),
            "i\\frac{g}{\\sqrt{2}m_W}\\left(m_{u\\alpha}\\cot\\beta\\, P_L + m_{d\\beta}\\tan\\beta\\, P_R\\right)V_{\\alpha\\beta}", any(only(BSM.chargedH), { it.isFermion })),
        e("2HDM (82)", THDM, listOf(l("H", "h, H"), l("W", "V_\\nu"), l("W", "V_\\mu")),
            "(82),\\ (83)\\times\\begin{cases}\\sin(\\beta - \\alpha) & (h)\\\\ \\cos(\\beta - \\alpha) & (H)\\end{cases}", only(BSM.heavyH, SM.higgs, SM.w, SM.z)),
        e("2HDM (79)", THDM, listOf(l("H", "h, H"), l("Ah", "A"), l("Z", "Z_\\mu")),
            "(79)\\times\\begin{cases}\\cos(\\beta - \\alpha) & (h)\\\\ -\\sin(\\beta - \\alpha) & (H)\\end{cases}", only(BSM.heavyH, BSM.pseudoA, SM.higgs, SM.z)),
        e("2HDM (77)", THDM, listOf(l("H", "h, H"), l("Hp", "H^\\mp", false), l("W", "W^\\pm_\\mu")),
            "(77)\\times\\begin{cases}\\cos(\\beta - \\alpha) & (h)\\\\ -\\sin(\\beta - \\alpha) & (H)\\end{cases}", only(BSM.heavyH, BSM.chargedH, SM.higgs, SM.w)),
        e("2HDM (78)", THDM, listOf(l("Ah", "A"), l("Hp", "H^\\mp", false), l("W", "W^\\pm_\\mu")), "(78)\\ \\text{with } \\varphi_Z \\to A,\\ \\varphi^\\pm \\to H^\\pm", only(BSM.pseudoA, BSM.chargedH, SM.w)),
        e("2HDM (75)", THDM, listOf(l("Hp", "H^+"), l("Hp", "H^-", false), l("A", "\\gamma, Z")), "(75),\\ (76),\\ (88)\\text{–}(90),\\ (95)\\ \\text{with } \\varphi^\\pm \\to H^\\pm", only(BSM.chargedH)),
    )

    // --- Z′ ------------------------------------------------------------------------------------

    private const val ZP = "Sequential Z′"
    val zPrime = listOf(
        prop("Z′ propagator", ZP, "Zp", "\\frac{-ig_{\\mu\\nu}}{k^{2} - m_{Z'}^{2} + i\\epsilon}\\ \\text{(and its } \\xi \\text{ terms, as (53))}", only(BSM.zPrime)),
        e("Z′ (68)", ZP, listOf(l("e", "\\psi_f", false), l("e", "\\psi_f"), l("Zp", "Z'_\\mu")), "-i\\eta\\eta_Z\\frac{g_{Z'}}{\\cos\\theta_W}\\gamma_\\mu\\left(g_V^f - g_A^f\\gamma_5\\right)", any(only(BSM.zPrime), { it.isFermion })),
    )

    // --- Wess–Zumino --------------------------------------------------------------------------

    private const val WZS = "Wess–Zumino model: W = mΦ²/2 + yΦ³/6"
    private val wzFields = only(SUSY.phiWZ, SUSY.psiWZ)
    val wz = listOf(
        prop("φ propagator", WZS, "phiwz", "\\frac{i}{p^{2} - m^{2} + i\\epsilon}", only(SUSY.phiWZ)),
        prop("Majorana propagator", WZS, "psiwz", "\\frac{i(\\slashed{p} + m)}{p^{2} - m^{2} + i\\epsilon},\\ p \\text{ along the fermion flow}", only(SUSY.psiWZ)),
        e("WZ Yukawa", WZS, listOf(l("psiwz", "\\psi"), l("psiwz", "\\psi"), l("phiwz", "\\phi")), "-iy P_L\\quad (\\phi^{*}:\\ -iy P_R)", wzFields),
        e("WZ cubic", WZS, listOf(l("phiwz", "\\phi"), l("phiwz", "\\phi"), l("phiwz", "\\phi^{*}", false)), "-imy\\quad (\\text{also } \\phi\\phi^{*}\\phi^{*})", wzFields),
        e("WZ quartic", WZS, listOf(l("phiwz", "\\phi"), l("phiwz", "\\phi^{*}", false), l("phiwz", "\\phi"), l("phiwz", "\\phi^{*}", false)), "-iy^{2}", wzFields),
    )

    // --- Supersymmetric QED and QCD --------------------------------------------------------------

    private const val SQ = "Supersymmetric QED"
    private const val SC = "Squarks and gluinos"
    private val sqedFields = any(isSf, only(SUSY.photino, SM.photon))
    val sqedOwn = listOf(
        prop("sfermion propagator", SQ, "seL", "\\frac{i}{p^{2} - m_{\\tilde f}^{2} + i\\epsilon}", isSf),
        prop("Majorana propagator", SQ, "photino", "\\frac{i(\\slashed{p} + m_{\\tilde\\gamma})}{p^{2} - m_{\\tilde\\gamma}^{2} + i\\epsilon},\\ p \\text{ along the fermion flow}", only(SUSY.photino)),
        e("f f̃ γ̃", SQ, listOf(l("photino", "\\tilde\\gamma"), l("e", "e"), l("seL", "\\tilde e_{L,R}", false)),
            "\\tilde e_L \\text{ out: } -i\\sqrt{2}eQ P_L,\\ \\text{in: } -i\\sqrt{2}eQ P_R;\\quad \\tilde e_R \\text{ out: } i\\sqrt{2}eQ P_R,\\ \\text{in: } i\\sqrt{2}eQ P_L", only(SUSY.photino, SM.electron, SM.muon)),
        e("D-term", SQ, listOf(l("seL", "\\tilde f_a"), l("seL", "\\tilde f_a^{*}", false), l("seR", "\\tilde f_b"), l("seR", "\\tilde f_b^{*}", false)),
            "-i(1 + \\delta_{ab})e^{2}q_aq_b,\\quad q_{\\tilde f_L} = -1,\\ q_{\\tilde f_R} = +1", isSf),
    )
    private val gauge = listOf(
        e("sfermion–gauge", SQ, listOf(l("seL", "\\tilde f", false), l("seL", "\\tilde f"), l("A", "V_\\mu")),
            "-iC_V(p_{\\rm in} - p_{\\rm out})_\\mu:\\ C_\\gamma = \\eta_e eQ,\\ C_Z = \\eta\\eta_Z\\frac{g}{\\cos\\theta_W}\\left(T^3 - Q\\sin^{2}\\theta_W\\right),\\ C_g = \\eta_s g_s T^a,\\ C_{W^+} = \\eta\\frac{g}{\\sqrt{2}}V\\ (\\tilde f_L)",
            any(isSf, only(SM.photon, SM.z, SM.w, SM.gluon))),
        e("sfermion seagull", SQ, listOf(l("seL", "\\tilde f", false), l("seL", "\\tilde f"), l("A", "V_\\mu"), l("A", "V'_\\nu")),
            "ig_{\\mu\\nu}\\{C_V, C_{V'}\\}\\quad (\\gamma\\gamma:\\ 2ie^{2}Q^{2}g_{\\mu\\nu},\\ gg:\\ ig_s^{2}\\{T^a, T^b\\}g_{\\mu\\nu})", any(isSf, only(SM.photon, SM.z, SM.w, SM.gluon))),
    )
    val sqcdOwn = listOf(
        prop("sfermion propagator", SC, "suL", "\\frac{i\\delta_{ij}}{p^{2} - m_{\\tilde q}^{2} + i\\epsilon}", isSquark),
        prop("Majorana propagator", SC, "gluino", "\\frac{i\\delta_{ab}(\\slashed{p} + m_{\\tilde g})}{p^{2} - m_{\\tilde g}^{2} + i\\epsilon},\\ p \\text{ along the fermion flow}", only(SUSY.gluino)),
        e("q q̃ g̃", SC, listOf(l("gluino", "\\tilde g^a"), l("u", "q_j"), l("suL", "\\tilde q_{L,R\\,i}", false)),
            "\\tilde q_L \\text{ out: } -i\\sqrt{2}g_sT^a_{ij}P_L,\\ \\text{in: } -i\\sqrt{2}g_sT^a_{ji}P_R;\\quad \\tilde q_R \\text{ out: } i\\sqrt{2}g_sT^a_{ij}P_R,\\ \\text{in: } i\\sqrt{2}g_sT^a_{ji}P_L",
            any(isSquark, only(SUSY.gluino))),
        e("g̃ g̃ g", SC, listOf(l("gluino", "\\tilde g^a"), l("gluino", "\\tilde g^c"), l("g", "g^b_\\mu")), "-\\eta_s g_s f^{bac}\\gamma_\\mu", only(SUSY.gluino, SM.gluon)),
    )

    // --- MSSM ------------------------------------------------------------------------------------

    private const val MP = "Superpartners: propagators and mixing"
    private const val INO = "Neutralinos and charginos with gauge bosons"
    private const val FSF = "Fermions, sfermions and inos"
    private const val HINO = "Neutralinos and charginos with Higgs bosons"
    private const val SFH = "Sfermions and Higgs bosons"
    private const val MIX = "N\\mathcal{M}_NN^{T} = \\mathrm{diag}(m_{\\tilde\\chi^0_i}),\\ UXV^{T} = \\mathrm{diag}(m_{\\tilde\\chi^\\pm_i}),\\quad \\mathcal{M}_N = \\begin{pmatrix}M_1 & 0 & -c_\\beta s_W m_Z & s_\\beta s_W m_Z\\\\ 0 & M_2 & c_\\beta c_W m_Z & -s_\\beta c_W m_Z\\\\ -c_\\beta s_W m_Z & c_\\beta c_W m_Z & 0 & -\\mu\\\\ s_\\beta s_W m_Z & -s_\\beta c_W m_Z & -\\mu & 0\\end{pmatrix},\\ X = \\begin{pmatrix}M_2 & \\sqrt{2}s_\\beta m_W\\\\ \\sqrt{2}c_\\beta m_W & \\mu\\end{pmatrix}"
    private const val YUK = "y_u = \\frac{gm_u}{\\sqrt{2}m_W\\sin\\beta},\\ y_d = \\frac{gm_d}{\\sqrt{2}m_W\\cos\\beta}"

    val mssmOwn = listOf(
        prop("sfermion propagator", MP, "seL", "\\frac{i}{p^{2} - m_{\\tilde f}^{2} + i\\epsilon}\\ (\\delta_{ij} \\text{ for squarks})", isSf),
        prop("Majorana propagator", MP, "n1", "\\frac{i(\\slashed{p} + m_{\\tilde\\chi^0_i})}{p^{2} - m_{\\tilde\\chi^0_i}^{2} + i\\epsilon},\\ p \\text{ along the fermion flow}; $MIX", any(isNeutralino, only(SUSY.gluino))),
        prop("chargino propagator", MP, "ch1", "\\frac{i(\\slashed{p} + m_{\\tilde\\chi^\\pm_i})}{p^{2} - m_{\\tilde\\chi^\\pm_i}^{2} + i\\epsilon}", isChargino),
        e("χ̃⁰χ̃⁰Z", INO, listOf(l("n1", "\\tilde\\chi^0_i"), l("n2", "\\tilde\\chi^0_j"), l("Z", "Z_\\mu")),
            "i\\eta\\eta_Z\\frac{g}{\\cos\\theta_W}O''_{ij}\\gamma_\\mu\\gamma_5,\\quad O''_{ij} = \\frac{1}{2}\\left(N_{i3}N_{j3} - N_{i4}N_{j4}\\right)", any(isNeutralino, only(SM.z))),
        e("χ̃⁺χ̃⁻γ", INO, listOf(l("ch1", "\\tilde\\chi^+_i", false), l("ch1", "\\tilde\\chi^+_i"), l("A", "A_\\mu")), "-i\\eta_e e\\gamma_\\mu", any(isChargino, only(SM.photon))),
        e("χ̃⁺χ̃⁻Z", INO, listOf(l("ch1", "\\tilde\\chi^+_i", false), l("ch2", "\\tilde\\chi^+_j"), l("Z", "Z_\\mu")),
            "-i\\eta\\eta_Z\\frac{g}{\\cos\\theta_W}\\gamma_\\mu\\left(O'^L_{ij}P_L + O'^R_{ij}P_R\\right),\\ O'^L_{ij} = V_{i1}V_{j1} + \\frac{1}{2}V_{i2}V_{j2} - \\delta_{ij}s_W^{2},\\ O'^R_{ij} = U_{i1}U_{j1} + \\frac{1}{2}U_{i2}U_{j2} - \\delta_{ij}s_W^{2}",
            any(isChargino, only(SM.z))),
        e("χ̃⁰χ̃±W", INO, listOf(l("ch1", "\\tilde\\chi^+_i", false), l("n1", "\\tilde\\chi^0_j"), l("W", "W^+_\\mu")),
            "i\\eta g\\gamma_\\mu\\left(O^L_{ij}P_L + O^R_{ij}P_R\\right),\\ O^L_{ij} = V_{i1}N_{j2} - \\frac{1}{\\sqrt{2}}V_{i2}N_{j4},\\ O^R_{ij} = U_{i1}N_{j2} + \\frac{1}{\\sqrt{2}}U_{i2}N_{j3}\\ (W^-: \\tilde\\chi^0_j \\text{ on the left})",
            any(isChargino, isNeutralino, only(SM.w))),
        e("f f̃ χ̃⁰", FSF, listOf(l("n1", "\\tilde\\chi^0_i"), l("e", "f"), l("seL", "\\tilde f_{L,R}", false)),
            "\\tilde f_L \\text{ out: } i\\left[-\\sqrt{2}\\left(g'Y_LN_{i1} + gT^3N_{i2}\\right)P_L - y_fN_{ih}P_R\\right],\\ \\tilde f_R \\text{ out: } i\\left[-y_fN_{ih}P_L + \\sqrt{2}g'QN_{i1}P_R\\right];\\ \\text{in: } P_L \\leftrightarrow P_R;\\ h = 4\\ (u),\\ 3\\ (d, \\ell);\\ $YUK",
            any(isNeutralino, isSf, { it.isFermion && it.family != null })),
        e("f f̃′ χ̃±", FSF, listOf(l("ch1", "\\tilde\\chi^+_i"), l("d", "d"), l("suL", "\\tilde u_{L,R}", false)),
            "\\tilde u_L:\\ i\\left(-gV_{i1}P_L + y_dU_{i2}P_R\\right),\\ \\tilde u_R:\\ iy_uV_{i2}P_L\\ (d \\text{ and } \\tilde\\chi^+ \\text{ in});\\quad \\tilde d_L:\\ i\\left(-gU_{i1}P_L + y_uV_{i2}P_R\\right),\\ \\tilde d_R:\\ iy_dU_{i2}P_L\\ (u \\text{ in}, \\tilde\\chi^+ \\text{ out});\\ \\times V_{\\rm CKM};\\ \\text{conjugates: } P_L \\leftrightarrow P_R",
            any(isChargino, isSf, { it.isFermion && it.family != null })),
        e("χ̃⁰χ̃⁰h", HINO, listOf(l("n1", "\\tilde\\chi^0_i"), l("n2", "\\tilde\\chi^0_j"), l("h", "h, H, A")),
            "h:\\ i(\\cos\\alpha A^u_{ij} - \\sin\\alpha A^d_{ij}),\\ H:\\ i(\\sin\\alpha A^u_{ij} + \\cos\\alpha A^d_{ij}),\\ A:\\ -(\\cos\\beta A^u_{ij} + \\sin\\beta A^d_{ij})\\gamma_5,\\ \\varphi_Z:\\ -(\\sin\\beta A^u_{ij} - \\cos\\beta A^d_{ij})\\gamma_5;\\quad A^u_{ij} = \\frac{1}{2}\\left[N_{i4}(gN_{j2} - g'N_{j1}) + (i \\leftrightarrow j)\\right],\\ A^d_{ij} = \\frac{1}{2}\\left[N_{i3}(g'N_{j1} - gN_{j2}) + (i \\leftrightarrow j)\\right]",
            any(isNeutralino, only(SM.higgs, BSM.heavyH, BSM.pseudoA, SM.phiZ))),
        e("χ̃⁺χ̃⁻h", HINO, listOf(l("ch1", "\\tilde\\chi^+_i", false), l("ch2", "\\tilde\\chi^+_j"), l("h", "h, H, A")),
            "-i\\frac{g}{\\sqrt{2}}\\left[c_\\alpha(S_{ij}P_L + S_{ji}'P_R) - s_\\alpha(S'_{ij}P_L + S_{ji}P_R)\\right] (h),\\ c_\\alpha \\to s_\\alpha, -s_\\alpha \\to c_\\alpha\\ (H);\\ S_{ij} = U_{i1}V_{j2},\\ S'_{ij} = U_{i2}V_{j1};\\ A, \\varphi_Z \\text{ with } \\gamma_5 \\text{ and } \\beta",
            any(isChargino, only(SM.higgs, BSM.heavyH, BSM.pseudoA, SM.phiZ))),
        e("χ̃⁰χ̃±H∓", HINO, listOf(l("ch1", "\\tilde\\chi^+_i", false), l("n1", "\\tilde\\chi^0_j"), l("Hp", "H^+")),
            "i\\left(-\\sin\\beta\\, Q^R_{ij}P_L - \\cos\\beta\\, Q^L_{ij}P_R\\right),\\ \\varphi^+:\\ i\\left(\\cos\\beta\\, Q^R_{ij}P_L - \\sin\\beta\\, Q^L_{ij}P_R\\right);\\ Q^L_{ij} = gN_{j4}V_{i1} + \\frac{V_{i2}}{\\sqrt{2}}(g'N_{j1} + gN_{j2}),\\ Q^R_{ij} = gN_{j3}U_{i1} - \\frac{U_{i2}}{\\sqrt{2}}(g'N_{j1} + gN_{j2})",
            any(isChargino, isNeutralino, only(BSM.chargedH, SM.phi))),
        e("sfermion–Higgs", SFH, listOf(l("seL", "\\tilde f", false), l("seL", "\\tilde f"), l("h", "h, H")),
            "h:\\ -i\\left[-\\frac{gm_Z}{\\cos\\theta_W}\\sin(\\alpha + \\beta)X_f + \\frac{gm_f^{2}}{m_W}\\left\\{\\frac{\\cos\\alpha}{\\sin\\beta}, -\\frac{\\sin\\alpha}{\\cos\\beta}\\right\\}\\right],\\ X_{f_L} = T^3 - Q s_W^{2},\\ X_{f_R} = Qs_W^{2};\\ H:\\ \\sin(\\alpha + \\beta) \\to -\\cos(\\alpha + \\beta),\\ \\left\\{\\frac{\\sin\\alpha}{\\sin\\beta}, \\frac{\\cos\\alpha}{\\cos\\beta}\\right\\}",
            any(isSf, only(SM.higgs, BSM.heavyH))),
        e("sfermion–Higgs ±", SFH, listOf(l("suL", "\\tilde u_L"), l("sdL", "\\tilde d_L", false), l("phi", "\\varphi^-, H^-")),
            "\\varphi^-:\\ -i\\frac{g}{\\sqrt{2}m_W}\\left(m_d^{2} - m_u^{2} - m_W^{2}\\cos 2\\beta\\right),\\ H^-:\\ -i\\frac{g}{\\sqrt{2}m_W}\\left(m_W^{2}\\sin 2\\beta - m_u^{2}\\cot\\beta - m_d^{2}\\tan\\beta\\right)",
            any(isSfL, only(SM.phi, BSM.chargedH))),
    )

    val sqed get() = sqedOwn + gauge.map { RuleCatalog.Entry(0, SQ, it.legs, it.tex, it.label, it.involves) }
    val sqcd get() = sqcdOwn + gauge.map { RuleCatalog.Entry(0, SC, it.legs, it.tex, it.label, it.involves) }
    val mssm get() = mssmOwn.take(3) + gauge.map { RuleCatalog.Entry(0, MP, it.legs, it.tex, it.label, it.involves) } + sqcdOwn.drop(2) + mssmOwn.drop(3)

    fun of(t: Theory): List<RuleCatalog.Entry> = when (t) {
        Theory.Phi4 -> phi4
        Theory.TwoHDM -> twoHdm
        Theory.ZPrime -> zPrime
        Theory.WZ -> wz
        Theory.SQED -> sqed
        Theory.SQCD -> sqcd
        Theory.MSSM -> twoHdm + mssm
        else -> emptyList()
    }

    /** Every model rule (for looking one up by name). */
    val all: List<RuleCatalog.Entry> by lazy { Theory.entries.flatMap { of(it) }.distinctBy { it.label } }
}
