package com.example.feynman.physics

/*
 * What a long-press explains: a particle (from the palette) or a drawn line (with its role in
 * the diagram), like the key cards of CAS Calculator.
 */

class HelpCard(
    val title: String,
    val symbolTex: String,
    /** Short facts: label and LaTeX value. */
    val facts: List<Pair<String, String>>,
    /** Formulas with a caption (the propagator, the external factor…). */
    val formulas: List<Pair<String, String>>,
    val theory: String,
    val usage: String,
    /** The paper's rules this field takes part in. */
    val vertices: List<RuleCatalog.Entry>,
)

object Help {
    private fun spin(p: Particle) = when (p.spin) {
        Spin.Fermion -> "\\frac{1}{2}"
        Spin.Vector -> "1"
        Spin.Scalar -> "0"
        Spin.Ghost -> "0\\ \\text{(anticommuting)}"
    }

    private fun drawnAs(p: Particle) = when (p.style) {
        LineStyle.Fermion -> if (p.oriented) "A solid line with an arrow for the flow of fermion number (against it for the antiparticle)." else "A solid line with no arrow: a Majorana fermion is its own antiparticle."
        LineStyle.Gaugino -> "A solid line with a ${if (p.color == ColorRep.Octet) "gluon's tight wave" else "wave"} on it: the gauge boson's Majorana partner."
        LineStyle.Boson -> if (p.oriented) "A wave, with a small arrow for the flow of W⁺ (W⁻ flows the other way)." else "A wave."
        LineStyle.Gluon -> "A tight wave, as in the paper (or coils, in Settings)."
        LineStyle.Scalar -> if (p.oriented) "Dashed, with an arrow for the flow of φ⁺." else "Dashed."
        LineStyle.Ghost -> "Dotted, with an arrow for the flow of ghost number."
    }

    private val theory = mapOf(
        "e" to "The electron: a charged lepton, the lightest of three generations. It couples to the photon and Z through its charge and weak isospin, to the W with its neutrino, and to the Higgs in proportion to its mass.",
        "mu" to "The muon: the second-generation charged lepton, about 207 times the electron's mass.",
        "tau" to "The tau: the third-generation charged lepton, heavy enough to decay to hadrons.",
        "nue" to "The electron neutrino: neutral and left-handed, it only feels the weak force (Z and W). Massless in the Standard Model.",
        "numu" to "The muon neutrino: neutral and left-handed, massless in the Standard Model.",
        "nutau" to "The tau neutrino: neutral and left-handed, massless in the Standard Model.",
        "u" to "The up quark: charge +2/3, in three colors. Up-type quarks couple to down-type ones through the W with the CKM matrix V.",
        "d" to "The down quark: charge −1/3, in three colors.",
        "c" to "The charm quark: the second-generation up-type quark.",
        "s" to "The strange quark: the second-generation down-type quark.",
        "t" to "The top quark: the heaviest particle known, with a Yukawa coupling close to 1; it decays almost only to b W⁺.",
        "b" to "The bottom quark: the third-generation down-type quark.",
        "A" to "The photon: the massless gauge boson of electromagnetism, the combination A = W³ sin θ_W + B cos θ_W that stays massless.",
        "Z" to "The Z boson: the massive neutral weak boson, Z = W³ cos θ_W − B sin θ_W; it couples to vector and axial currents with g_V and g_A.",
        "W" to "The W bosons: the charged weak bosons, W± = (W¹ ∓ iW²)/√2; they change flavour, with the CKM matrix for quarks.",
        "g" to "The gluon: the gauge boson of the strong force, in eight colors; gluons couple to each other (three- and four-gluon vertices).",
        "h" to "The Higgs boson: the physical scalar left after electroweak symmetry breaking; it couples to each particle in proportion to its mass.",
        "phiZ" to "The neutral would-be Goldstone boson: eaten by the Z in the unitary gauge; in an Rξ gauge it has mass √ξ m_Z and appears in loops.",
        "phi" to "The charged would-be Goldstone bosons: eaten by the W; in an Rξ gauge they have mass √ξ m_W and appear in loops.",
        "cA" to "The photon's Faddeev–Popov ghost: an anticommuting scalar that only appears in loops, where each closed loop gives −1.",
        "cZ" to "The Z's Faddeev–Popov ghost, with mass √ξ m_Z.",
        "cp" to "The W⁺'s Faddeev–Popov ghost, with mass √ξ m_W (c⁺ and c⁻ are different fields, not each other's antiparticles).",
        "cm" to "The W⁻'s Faddeev–Popov ghost, with mass √ξ m_W.",
        "om" to "The gluon's Faddeev–Popov ghost: needed in loops of gluons so that only physical polarizations contribute.",
        "phi4" to "A real scalar field with a quartic self-coupling λ (and optionally a cubic κ): the simplest interacting theory, used to learn renormalization.",
        "H" to "The heavier CP-even Higgs of a two-Higgs-doublet model; its couplings to W and Z are cos(β − α) times the Standard Model's.",
        "Ah" to "The CP-odd Higgs of a two-Higgs-doublet model: it has no tree-level coupling to WW or ZZ.",
        "Hp" to "The charged Higgs of a two-Higgs-doublet model: it couples to fermions like the charged Goldstone, times cot β (up) and tan β (down).",
        "Zp" to "A heavy neutral gauge boson with the Z's couplings to fermions, scaled by g_{Z′}/g (a sequential Z′).",
    )

    private val propagatorEq = mapOf("g" to 45, "om" to 46, "A" to 51, "W" to 52, "Z" to 53, "h" to 55, "phiZ" to 56, "phi" to 57, "cA" to 105, "cp" to 106, "cm" to 106, "cZ" to 107)

    /** Which of the theory's vertices include [p] (the paper writes fermion rules for a generic f). */
    fun verticesOf(p: Particle, theory: Theory = Theory.SM): List<RuleCatalog.Entry> {
        val list = if (theory.allows(p)) RuleCatalog.forTheory(theory) else RuleCatalog.all + ModelRules.all
        return list.filter { e ->
            if (e.isPropagator) return@filter false
            e.involves?.let { return@filter it(p) }
            e.legs.any { leg ->
                val q = SM.byId(leg.particle) ?: return@any false
                when {
                    p.isFermion && q.isFermion -> p.family != null && when (e.eq) {
                        67 -> p.charge.signum != 0
                        68 -> true
                        70, 71 -> p.mass != null
                        49 -> p.color == ColorRep.Triplet
                        64, 65 -> p.color == ColorRep.Triplet
                        72, 73 -> p.color == ColorRep.Triplet
                        66, 74 -> p.color == ColorRep.None
                        else -> q.family == p.family
                    }
                    p === SM.ghostMinus -> q === SM.ghostPlus
                    else -> q === p
                }
            }
        }
    }

    /** Why a superpartner is there. */
    private fun susyTheory(p: Particle): String? {
        SUSY.sf(p)?.let { sf ->
            val f = sf.partner
            val which = if (f.family == Family.Neutrino) "" else if (sf.left) " left-handed" else " right-handed"
            return "The scalar partner of the$which ${f.name.lowercase()} in its chiral supermultiplet: the same charge${if (f.color == ColorRep.Triplet) ", color" else ""} and gauge couplings, spin 0. " +
                "It couples to its fermion and a neutralino${if (f.color == ColorRep.Triplet) " or gluino" else ""}, and to the other member of its doublet and a chargino" +
                (if (sf.left) "" else " (a right-handed one only through the Yukawa coupling)") + ". Left–right mixing is left out."
        }
        return when (p) {
            in SUSY.neutralinos -> "A neutralino: a Majorana mixture of the bino, the neutral wino and the two neutral higgsinos, χ̃⁰_i = N_{ij}(B̃, W̃⁰, H̃⁰_d, H̃⁰_u)_j, from diagonalizing the mass matrix with M₁, M₂, μ and tan β. The lightest is often the lightest superpartner (dark matter)."
            in SUSY.charginos -> "A chargino: a Dirac fermion made of the charged wino and higgsinos, χ̃⁺_i = V_{ij}(W̃⁺, H̃⁺_u)_j, χ̃⁻_i = U_{ij}(W̃⁻, H̃⁻_d)_j, with masses from M₂, μ and tan β."
            SUSY.gluino -> "The gluino: the Majorana partner of the gluon, a color octet with mass M₃. It couples to gluons like a quark in the adjoint and to quarks and squarks with strength √2 g_s."
            SUSY.photino -> "The photino: the Majorana partner of the photon in supersymmetric QED. It couples each lepton to its scalar partners with strength √2 eQ, with opposite chiralities for ẽ_L and ẽ_R."
            SUSY.phiWZ -> "The Wess–Zumino model's complex scalar: with the Majorana ψ it forms a chiral supermultiplet, and the superpotential W = mΦ²/2 + yΦ³/6 gives both the same mass."
            SUSY.psiWZ -> "The Wess–Zumino model's Majorana fermion: the scalar φ's superpartner, with the same mass m and the Yukawa coupling y. Its line has no arrow: it's its own antiparticle."
            else -> null
        }
    }

    fun particle(p: Particle, th: Theory = Theory.SM): HelpCard {
        val facts = ArrayList<Pair<String, String>>()
        facts.add("Spin" to spin(p))
        if (p.spin != Spin.Ghost) facts.add("Charge" to "Q = ${Tex.rational(p.charge)}")
        if (p.isFermion) facts.add("Weak isospin" to "T^3 = ${Tex.rational(p.t3)}\\ \\text{(left-handed)}")
        facts.add("Color" to when (p.color) { ColorRep.None -> "\\text{none}"; ColorRep.Triplet -> "3\\ \\text{(triplet)}"; ColorRep.Octet -> "8\\ \\text{(octet)}" })
        val m = p.mass
        val value = m?.let { Evaluate.defaults[it.name] }
        facts.add("Mass" to when {
            m == null -> "0"
            value != null && p.spin != Spin.Ghost && p !== SM.phiZ && p !== SM.phi -> "${m.tex} = ${Numbers.texOf(value)}\\ \\text{GeV}"
            else -> "${m.tex}\\ (\\xi = 1)"
        })
        if (p.oriented && p.tex != p.antiTex) facts.add("Antiparticle" to p.antiTex)
        val formulas = ArrayList<Pair<String, String>>()
        val own = ModelRules.all.firstOrNull { it.isPropagator && it.involves?.invoke(p) == true }
        val eq = if (p.isFermion && p.family != null) 54 else propagatorEq[p.id]
        if (own != null) formulas.add("Propagator" to own.tex)
        else eq?.let { n -> RuleCatalog.byEq(n)?.let { formulas.add("Propagator, eq. ($n)" to it.tex) } }
        return HelpCard(
            p.name, if (p.tex != p.antiTex) "${p.tex},\\ ${p.antiTex}" else p.tex, facts, formulas,
            theory[p.id] ?: susyTheory(p) ?: "", drawnAs(p) + " Pick the key, then drag between two points to draw it" + (if (p.oriented) "; reverse a drawn line to get the antiparticle." else "."),
            verticesOf(p, th),
        )
    }

    /** A drawn line: the particle, and what the line is in this diagram. */
    fun line(d: Diagram, lineId: Int, options: SolveOptions): HelpCard? {
        val l = d.lines.firstOrNull { it.id == lineId } ?: return null
        val p = SM.byId(l.particle) ?: return null
        val base = particle(p, options.theory)
        val topo = Topology.of(d)
        val q = topo.momenta[l.id].orEmpty()
        val formulas = ArrayList<Pair<String, String>>()
        val facts = ArrayList(base.facts)
        val ext = topo.external(l.id)
        val ctx = Solver.context(options)
        val title: String
        if (ext != null) {
            val pt = MomNames.tex(ext.momentum)
            title = "${if (ext.incoming) "Incoming" else "Outgoing"} ${p.name.replaceFirstChar { it.lowercase() }}${if (ext.anti) " (antiparticle)" else ""}"
            facts.add(0, "Momentum" to "$pt\\ \\text{(${if (ext.incoming) "flowing in" else "flowing out"})}")
            val factor = when (p.spin) {
                Spin.Fermion -> if (!p.oriented) {
                    (if (ext.incoming) "u($pt)\\ \\text{or}\\ \\bar{v}($pt)" else "\\bar{u}($pt)\\ \\text{or}\\ v($pt)") to
                        "A Majorana fermion: u or v̄ when it comes in, ū or v when it goes out, depending on which way its line is read (the fermion flow)."
                } else when {
                    ext.incoming && !ext.anti -> "u($pt)" to "Incoming fermion: a spinor u on the right of its line."
                    ext.incoming -> "\\bar{v}($pt)" to "Incoming antifermion: a spinor v̄ on the left of its line."
                    !ext.anti -> "\\bar{u}($pt)" to "Outgoing fermion: a spinor ū on the left of its line."
                    else -> "v($pt)" to "Outgoing antifermion: a spinor v on the right of its line."
                }
                Spin.Vector -> (if (ext.incoming) "\\varepsilon_\\mu($pt)" else "\\varepsilon^{*}_\\mu($pt)") to "A polarization vector, summed with −g_{μν} in |ℳ|²."
                else -> "1" to "External scalars add no factor."
            }
            formulas.add(factor.second to factor.first)
        } else {
            title = if (l.isSelfLoop) "${p.name} loop" else "Internal ${p.name.replaceFirstChar { it.lowercase() }}"
            if (q.isNotEmpty()) facts.add(0, "Momentum" to MomNames.tex(q))
            val pool = IndexPool()
            val r = Rules.propagator(p, q, pool.fresh("\\mu"), pool.fresh("\\nu"), ctx)
            formulas.add("This propagator, ${if (r.label == null) "eq. (${r.eq})" else r.tag}" to r.tex)
            if (q.keys.any { it.startsWith("k") }) formulas.add("It carries the loop momentum, so it's one of the loop's denominators" to Tex.of(dot(q, q)) + (ctx.massOf(p)?.let { " - ${it.tex}^{2}" } ?: ""))
        }
        return HelpCard(title, if (ext != null) ext.tex else p.tex, facts, formulas + base.formulas, base.theory,
            "Tap the line to change its particle, reverse it, straighten it or remove it; drag its middle with the bend tool.", base.vertices)
    }

    /** A vertex: the rule it matches, as printed and as used here. */
    fun vertex(d: Diagram, pointId: Int, options: SolveOptions): HelpCard? {
        if (d.degree(pointId) < 2) return null
        val amp = Amplitude.build(d, Solver.context(options))
        val legs = d.linesAt(pointId).flatMap { l ->
            val p = SM.byId(l.particle) ?: return@flatMap emptyList()
            listOfNotNull(if (l.to == pointId) p.tex else null, if (l.from == pointId) (if (p.oriented) p.antiTex else p.tex) else null)
        }
        val symbol = legs.joinToString("\\,")
        val r = amp.vertexRules[pointId]
        if (r == null) {
            val why = amp.issues.firstOrNull { it.point == pointId }?.message ?: "This point isn't a vertex of ${options.theory.label}."
            return HelpCard("No vertex", symbol, listOf("Lines" to "${d.degree(pointId)}"), emptyList(), why,
                "Check the particles and arrows: charge, color and fermion number must be conserved, and no theory here has a vertex with more than four lines.", emptyList())
        }
        val printed = if (r.label != null) RuleCatalog.byLabel(r.label) else RuleCatalog.byEq(r.eq)
        val formulas = ArrayList<Pair<String, String>>()
        formulas.add("Here, ${if (r.label == null) "eq. (${r.eq})" else r.tag} with your signs" to r.tex)
        printed?.let { formulas.add(if (r.label == null) "As printed in the paper" to it.tex else "As in the Rules panel" to it.tex) }
        return HelpCard("Vertex", symbol, listOf("Lines" to "${d.degree(pointId)}", "Rule" to "\\text{${r.tag}}"), formulas,
            "All momenta flow into the vertex (except the outgoing ghost's p in ghost vertices). Momentum is conserved here, which fixes the internal momenta.",
            "Drag the point with the move tool; erase it to remove every line at it.", listOfNotNull(printed))
    }
}
