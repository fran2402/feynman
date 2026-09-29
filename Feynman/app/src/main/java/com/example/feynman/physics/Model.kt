package com.example.feynman.physics

/*
 * The Standard Model's fields, as in Romão & Silva, "A resource for signs and Feynman
 * diagrams of the Standard Model", Int. J. Mod. Phys. A 27 (2012) 1230025: fermions,
 * gauge bosons, the Higgs and would-be Goldstone bosons, and the Faddeev–Popov ghosts.
 */

/** How a line is drawn, following the paper's figures. */
enum class LineStyle {
    /** Solid with an arrow for the fermion flow. */
    Fermion,
    /** A wave: γ, Z, W. */
    Boson,
    /** A tighter wave, as the paper draws gluons (or a coil, in settings). */
    Gluon,
    /** Dashed: h, φ_Z, φ±. */
    Scalar,
    /** Dotted with an arrow: ghosts. */
    Ghost,
}

enum class Spin { Fermion, Vector, Scalar, Ghost }

enum class ColorRep { None, Triplet, Octet }

/** Kinds of fermion, for the charged-current and Yukawa rules. */
enum class Family { ChargedLepton, Neutrino, UpQuark, DownQuark }

/**
 * A kind of line. Oriented lines have a direction: the flow of the particle (fermion
 * number, W⁺ and φ⁺ charge, ghost number) from the line's start to its end.
 */
class Particle(
    val id: String,
    val name: String,
    /** The particle flowing along the line, as LaTeX (e^-, W^+, c^+). */
    val tex: String,
    /** Its antiparticle (the same for self-conjugate lines). */
    val antiTex: String,
    val spin: Spin,
    val style: LineStyle,
    val oriented: Boolean,
    /** Electric charge Q of the particle, in units of e. */
    val charge: Rational = Rational.ZERO,
    /** T³ of its left-handed part (fermions). */
    val t3: Rational = Rational.ZERO,
    val mass: Sym? = null,
    val color: ColorRep = ColorRep.None,
    val family: Family? = null,
    /** 1, 2 or 3 for fermions (the CKM indices α and β). */
    val generation: Int = 0,
    /** Group on the palette. */
    val group: String,
) {
    override fun toString() = id
    val isFermion get() = spin == Spin.Fermion
    val isGhost get() = spin == Spin.Ghost
    val isVector get() = spin == Spin.Vector
}

object Couplings {
    val e = Sym("e", "e", order = 1)
    val g = Sym("g", "g", order = 2)
    val gs = Sym("gs", "g_s", order = 3)
    val cW = Sym("cW", "c_W", order = 10)
    val sW = Sym("sW", "s_W", order = 11)
    val mW = Sym("mW", "m_W", order = 20)
    val mZ = Sym("mZ", "m_Z", order = 21)
    val mh = Sym("mh", "m_h", order = 22)
    fun mass(name: String, tex: String) = Sym("m_$name", "m_{$tex}", order = 23)

    /** V_{αβ}, the CKM matrix element between up quark α and down quark β. */
    fun ckm(up: Particle, down: Particle) = Sym("V_${up.id}${down.id}", "V_{${up.id}${down.id}}", complex = true, order = 30)

    /** Couplings a person can set numerically, in the order shown. */
    val all get() = listOf(e, g, gs, cW, sW, mW, mZ, mh)
}

object SM {
    private fun lepton(id: String, name: String, tex: String, gen: Int) = Particle(
        id, name, "$tex^-", "$tex^+", Spin.Fermion, LineStyle.Fermion, true,
        charge = Rational.of(-1), t3 = Rational.of(-1, 2), mass = Couplings.mass(id, tex),
        family = Family.ChargedLepton, generation = gen, group = "Leptons",
    )
    private fun neutrino(id: String, name: String, flavour: String, gen: Int) = Particle(
        id, name, "\\nu_{$flavour}", "\\bar\\nu_{$flavour}", Spin.Fermion, LineStyle.Fermion, true,
        charge = Rational.ZERO, t3 = Rational.of(1, 2), mass = null,
        family = Family.Neutrino, generation = gen, group = "Leptons",
    )
    private fun quark(id: String, name: String, up: Boolean, gen: Int) = Particle(
        id, name, id, "\\bar $id", Spin.Fermion, LineStyle.Fermion, true,
        charge = if (up) Rational.of(2, 3) else Rational.of(-1, 3), t3 = if (up) Rational.of(1, 2) else Rational.of(-1, 2),
        mass = Couplings.mass(id, id), color = ColorRep.Triplet,
        family = if (up) Family.UpQuark else Family.DownQuark, generation = gen, group = "Quarks",
    )

    val electron = lepton("e", "Electron", "e", 1)
    val muon = lepton("mu", "Muon", "\\mu", 2)
    val tau = lepton("tau", "Tau", "\\tau", 3)
    val nue = neutrino("nue", "Electron neutrino", "e", 1)
    val numu = neutrino("numu", "Muon neutrino", "\\mu", 2)
    val nutau = neutrino("nutau", "Tau neutrino", "\\tau", 3)
    val up = quark("u", "Up quark", true, 1)
    val down = quark("d", "Down quark", false, 1)
    val charm = quark("c", "Charm quark", true, 2)
    val strange = quark("s", "Strange quark", false, 2)
    val top = quark("t", "Top quark", true, 3)
    val bottom = quark("b", "Bottom quark", false, 3)

    val photon = Particle("A", "Photon", "\\gamma", "\\gamma", Spin.Vector, LineStyle.Boson, false, group = "Gauge bosons")
    val z = Particle("Z", "Z boson", "Z", "Z", Spin.Vector, LineStyle.Boson, false, mass = Couplings.mZ, group = "Gauge bosons")
    val w = Particle("W", "W boson", "W^+", "W^-", Spin.Vector, LineStyle.Boson, true, charge = Rational.ONE, mass = Couplings.mW, group = "Gauge bosons")
    val gluon = Particle("g", "Gluon", "g", "g", Spin.Vector, LineStyle.Gluon, false, color = ColorRep.Octet, group = "Gauge bosons")

    val higgs = Particle("h", "Higgs boson", "h", "h", Spin.Scalar, LineStyle.Scalar, false, mass = Couplings.mh, group = "Scalars")
    val phiZ = Particle("phiZ", "Neutral Goldstone boson", "\\varphi_Z", "\\varphi_Z", Spin.Scalar, LineStyle.Scalar, false, mass = Couplings.mZ, group = "Scalars")
    val phi = Particle("phi", "Charged Goldstone boson", "\\varphi^+", "\\varphi^-", Spin.Scalar, LineStyle.Scalar, true, charge = Rational.ONE, mass = Couplings.mW, group = "Scalars")

    val ghostA = Particle("cA", "Photon ghost", "c_A", "\\bar c_A", Spin.Ghost, LineStyle.Ghost, true, group = "Ghosts")
    val ghostZ = Particle("cZ", "Z ghost", "c_Z", "\\bar c_Z", Spin.Ghost, LineStyle.Ghost, true, mass = Couplings.mZ, group = "Ghosts")
    val ghostPlus = Particle("cp", "W⁺ ghost", "c^+", "\\bar c^+", Spin.Ghost, LineStyle.Ghost, true, charge = Rational.ONE, mass = Couplings.mW, group = "Ghosts")
    val ghostMinus = Particle("cm", "W⁻ ghost", "c^-", "\\bar c^-", Spin.Ghost, LineStyle.Ghost, true, charge = Rational.of(-1), mass = Couplings.mW, group = "Ghosts")
    val ghostG = Particle("om", "Gluon ghost", "\\omega", "\\bar\\omega", Spin.Ghost, LineStyle.Ghost, true, color = ColorRep.Octet, group = "Ghosts")

    val all: List<Particle> = listOf(
        electron, muon, tau, nue, numu, nutau,
        up, down, charm, strange, top, bottom,
        photon, z, w, gluon,
        higgs, phiZ, phi,
        ghostA, ghostZ, ghostPlus, ghostMinus, ghostG,
    )

    val groups: List<String> = all.map { it.group }.distinct()

    fun byId(id: String): Particle? = all.firstOrNull { it.id == id }

    val upQuarks get() = listOf(up, charm, top)
    val downQuarks get() = listOf(down, strange, bottom)
}

/**
 * The signs η of the paper's Sec. 2, which differ from book to book (its Tables 2 and 3).
 * Only ηs, η, ηe, ηZ and ηG appear in Feynman rules; η′, ηθ and ηY are kept to show.
 */
data class Conventions(
    val eta: Int = 1,
    val etaPrime: Int = 1,
    val etaZ: Int = 1,
    val etaTheta: Int = 1,
    val etaY: Int = 1,
    val etaE: Int = 1,
    val etaS: Int = 1,
    val etaG: Int = 1,
) {
    fun encode() = listOf(eta, etaPrime, etaZ, etaTheta, etaY, etaE, etaS, etaG).joinToString(",")

    companion object {
        fun decode(s: String?): Conventions? {
            val v = s?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: return null
            if (v.size != 8 || v.any { it != 1 && it != -1 }) return null
            return Conventions(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7])
        }
    }
}

/** A book's or review's conventions, from the paper's Tables 2 and 3 (ηs = η, as the paper notes is usual). */
data class ConventionPreset(val name: String, val refs: String, val conv: Conventions)

object ConventionPresets {
    private fun c(eta: Int, etaP: Int, etaZ: Int, etaT: Int, etaY: Int, etaE: Int, etaG: Int) =
        Conventions(eta, etaP, etaZ, etaT, etaY, etaE, eta, etaG)

    val all = listOf(
        ConventionPreset("Bailin & Love; Mandl & Shaw; Langacker", "Refs. 2, 4–6, 47", c(1, 1, 1, 1, 1, 1, 1)),
        ConventionPreset("Pokorski", "Ref. 3", c(1, 1, 1, 1, 1, 1, -1)),
        ConventionPreset("Quigg; Halzen & Martin; Aitchison & Hey; Griffiths", "Refs. 7–17", c(1, 1, 1, 1, 1, 1, 1)),
        ConventionPreset("Peskin & Schroeder", "Ref. 18 (and 19)", c(-1, -1, 1, 1, 1, -1, 1)),
        ConventionPreset("Cheng & Li", "Ref. 23", c(-1, -1, 1, 1, 1, -1, -1)),
        ConventionPreset("Ryder; Okun; Nair; Zee; Djouadi", "Refs. 20–22, 24–31", c(-1, -1, 1, 1, 1, -1, 1)),
        ConventionPreset("Branco, Lavoura & Silva", "Refs. 32, 33", c(-1, -1, 1, -1, 1, 1, 1)),
        ConventionPreset("Itzykson & Zuber", "Ref. 34", c(-1, -1, -1, 1, 1, -1, 1)),
        ConventionPreset("Barroso, Pulido & Romão; Nogueira", "Refs. 36, 37", c(-1, 1, 1, -1, -1, 1, 1)),
        ConventionPreset("Romão, Advanced QFT", "Ref. 38", c(-1, 1, 1, -1, 1, 1, 1)),
        ConventionPreset("Das", "Ref. 39", c(1, -1, 1, -1, 1, -1, 1)),
    )

    fun nameOf(c: Conventions) = all.firstOrNull { it.conv == c }?.name ?: "Custom"
}
