package com.example.feynman.physics

/** Ready-made diagrams to start from: textbook processes at tree level and one loop. */
object Templates {
    class Template(val name: String, val group: String, val diagram: Diagram, val about: String)

    private class B(val name: String) {
        val points = ArrayList<Point>()
        val lines = ArrayList<Line>()
        private var id = 1
        fun p(x: Float, y: Float): Int { val i = id++; points.add(Point(i, x, y)); return i }
        fun l(from: Int, to: Int, particle: String, bend: Float = 0f) { lines.add(Line(id++, from, to, particle, bend)) }
        fun build() = Diagram(points, lines, name)
    }

    private fun d(name: String, f: B.() -> Unit) = B(name).apply(f).build()

    /** Incoming a(top) b(bottom) → s-channel → outgoing c(top) d(bottom), fermion lines. */
    private fun sChannel(name: String, inP: String, boson: String, outP: String) = d(name) {
        val a = p(0f, 0f); val b = p(0f, 180f); val v1 = p(90f, 90f); val v2 = p(230f, 90f); val c = p(320f, 0f); val e = p(320f, 180f)
        l(a, v1, inP); l(v1, b, inP); l(v1, v2, boson); l(v2, c, outP); l(e, v2, outP)
    }

    val all: List<Template> by lazy {
        listOf(
            Template("e⁻e⁺ → μ⁻μ⁺", "Tree level", sChannel("e⁻e⁺ → μ⁻μ⁺", "e", "A", "mu"),
                "Muon pair production through a photon: the textbook (1 + cos²θ)."),
            Template("e⁻e⁺ → μ⁻μ⁺ (Z)", "Tree level", sChannel("e⁻e⁺ → μ⁻μ⁺ via Z", "e", "Z", "mu"),
                "The same through a Z: vector and axial couplings."),
            Template("Bhabha, t-channel", "Tree level", d("Bhabha t-channel") {
                val a = p(0f, 0f); val b = p(0f, 180f); val v1 = p(160f, 30f); val v2 = p(160f, 150f); val c = p(320f, 0f); val e = p(320f, 180f)
                l(a, v1, "e"); l(v1, c, "e"); l(v1, v2, "A"); l(v2, b, "e"); l(e, v2, "e")
            }, "e⁻e⁺ → e⁻e⁺ by photon exchange; add the s-channel for the full Bhabha formula."),
            Template("Bhabha, s-channel", "Tree level", sChannel("Bhabha s-channel", "e", "A", "e"),
                "Annihilation part of Bhabha scattering (relative sign −1 against the t-channel)."),
            Template("e⁻μ⁻ → e⁻μ⁻", "Tree level", d("e⁻μ⁻ scattering") {
                val a = p(0f, 0f); val b = p(0f, 180f); val v1 = p(160f, 30f); val v2 = p(160f, 150f); val c = p(320f, 0f); val e = p(320f, 180f)
                l(a, v1, "e"); l(v1, c, "e"); l(v1, v2, "A"); l(b, v2, "mu"); l(v2, e, "mu")
            }, "Photon exchange between different fermions: (s² + u²)/t²."),
            Template("Compton, s-channel", "Tree level", d("Compton s-channel") {
                val a = p(0f, 0f); val g1 = p(0f, 180f); val v1 = p(100f, 90f); val v2 = p(220f, 90f); val c = p(320f, 0f); val g2 = p(320f, 180f)
                l(a, v1, "e"); l(g1, v1, "A"); l(v1, v2, "e"); l(v2, c, "e"); l(v2, g2, "A")
            }, "γe⁻ → γe⁻ with the electron absorbing the photon first."),
            Template("q q̄ → g g, s-channel", "Tree level", d("q q̄ → g g") {
                val a = p(0f, 0f); val b = p(0f, 180f); val v1 = p(90f, 90f); val v2 = p(230f, 90f); val c = p(320f, 0f); val e = p(320f, 180f)
                l(a, v1, "u"); l(v1, b, "u"); l(v1, v2, "g"); l(v2, c, "g"); l(v2, e, "g")
            }, "Through the three-gluon vertex: color factors from f^{abc}."),
            Template("Muon decay", "Tree level", d("Muon decay") {
                val mu = p(0f, 60f); val v1 = p(120f, 60f); val nm = p(320f, 0f); val v2 = p(200f, 170f); val e = p(320f, 130f); val ne = p(320f, 220f)
                l(mu, v1, "mu"); l(v1, nm, "numu"); l(v2, v1, "W"); l(v2, e, "e"); l(ne, v2, "nue")
            }, "μ⁻ → ν_μ e⁻ ν̄_e through a W⁻."),
            Template("h → b b̄", "Tree level", d("h → b b̄") {
                val h = p(0f, 90f); val v = p(140f, 90f); val b1 = p(300f, 0f); val b2 = p(300f, 180f)
                l(h, v, "h"); l(v, b1, "b"); l(b2, v, "b")
            }, "The Higgs decaying to bottom quarks: proportional to m_b²."),
            Template("h → W⁺W⁻", "Tree level", d("h → W⁺W⁻") {
                val h = p(0f, 90f); val v = p(140f, 90f); val w1 = p(300f, 0f); val w2 = p(300f, 180f)
                l(h, v, "h"); l(v, w1, "W"); l(w2, v, "W")
            }, "hWW coupling i g m_W g_{μν}, with massive polarization sums."),
            Template("Z → e⁻e⁺", "Tree level", d("Z → e⁻e⁺") {
                val z = p(0f, 90f); val v = p(140f, 90f); val e1 = p(300f, 0f); val e2 = p(300f, 180f)
                l(z, v, "Z"); l(v, e1, "e"); l(e2, v, "e")
            }, "The Z width to electrons: g_V² + g_A²."),
            Template("t → b W⁺", "Tree level", d("t → b W⁺") {
                val t = p(0f, 90f); val v = p(140f, 90f); val b = p(300f, 0f); val w = p(300f, 180f)
                l(t, v, "t"); l(v, b, "b"); l(v, w, "W")
            }, "Top decay with the CKM element V_tb."),
            Template("Vacuum polarization", "One loop", d("Vacuum polarization") {
                val a = p(0f, 90f); val v1 = p(90f, 90f); val v2 = p(230f, 90f); val b = p(320f, 90f)
                l(a, v1, "A"); l(v1, v2, "e", 0.45f); l(v2, v1, "e", 0.45f); l(v2, b, "A")
            }, "The electron loop in the photon propagator: transverse, with a 1/ε pole."),
            Template("Electron self-energy", "One loop", d("Electron self-energy") {
                val a = p(0f, 110f); val v1 = p(90f, 110f); val v2 = p(230f, 110f); val b = p(320f, 110f)
                l(a, v1, "e"); l(v1, v2, "e"); l(v1, v2, "A", 0.5f); l(v2, b, "e")
            }, "Σ(p̸): the photon loop on an electron line."),
            Template("QED vertex correction", "One loop", d("Vertex correction") {
                val a = p(0f, 0f); val b = p(0f, 200f); val v1 = p(90f, 40f); val v2 = p(90f, 160f); val v3 = p(200f, 100f); val g = p(320f, 100f)
                l(a, v1, "e"); l(v1, v3, "e"); l(v3, v2, "e"); l(v2, b, "e"); l(v1, v2, "A"); l(v3, g, "A")
            }, "The photon across the vertex: the UV pole that cancels against the self-energy's."),
            Template("Higgs self-energy, top loop", "One loop", d("Higgs self-energy, top loop") {
                val a = p(0f, 90f); val v1 = p(90f, 90f); val v2 = p(230f, 90f); val b = p(320f, 90f)
                l(a, v1, "h"); l(v1, v2, "t", 0.45f); l(v2, v1, "t", 0.45f); l(v2, b, "h")
            }, "The top quark's contribution to the Higgs mass: quadratic in m_t."),
            Template("Higgs bubble", "One loop", d("Higgs bubble") {
                val a = p(0f, 90f); val v1 = p(90f, 90f); val v2 = p(230f, 90f); val b = p(320f, 90f)
                l(a, v1, "h"); l(v1, v2, "h", 0.45f); l(v2, v1, "h", 0.45f); l(v2, b, "h")
            }, "Two hhh vertices and a symmetry factor 1/2."),
            Template("Higgs tadpole", "One loop", d("Higgs tadpole") {
                val a = p(0f, 150f); val v1 = p(160f, 150f); val b = p(320f, 150f)
                l(a, v1, "h"); l(v1, b, "h"); l(v1, v1, "h", 0.5f)
            }, "The quartic coupling with a closed Higgs loop: A₀(m_h²)."),
            Template("Gluon self-energy, quark loop", "One loop", d("Gluon self-energy, quark loop") {
                val a = p(0f, 90f); val v1 = p(90f, 90f); val v2 = p(230f, 90f); val b = p(320f, 90f)
                l(a, v1, "g"); l(v1, v2, "u", 0.45f); l(v2, v1, "u", 0.45f); l(v2, b, "g")
            }, "Like vacuum polarization, with the color factor T_F δ^{ab}."),
            Template("Gluon self-energy, gluon loop", "One loop", d("Gluon self-energy, gluon loop") {
                val a = p(0f, 90f); val v1 = p(90f, 90f); val v2 = p(230f, 90f); val b = p(320f, 90f)
                l(a, v1, "g"); l(v1, v2, "g", 0.45f); l(v2, v1, "g", 0.45f); l(v2, b, "g")
            }, "Two three-gluon vertices; add the ghost loop for a transverse sum."),
            Template("Gluon self-energy, ghost loop", "One loop", d("Gluon self-energy, ghost loop") {
                val a = p(0f, 90f); val v1 = p(90f, 90f); val v2 = p(230f, 90f); val b = p(320f, 90f)
                l(a, v1, "g"); l(v1, v2, "om", 0.45f); l(v2, v1, "om", 0.45f); l(v2, b, "g")
            }, "The Faddeev–Popov ghosts' loop, with its minus sign."),
            Template("h → γγ, top loop", "One loop", d("h → γγ, top loop") {
                val h = p(0f, 110f); val v1 = p(100f, 110f); val v2 = p(210f, 40f); val v3 = p(210f, 180f); val g1 = p(320f, 0f); val g2 = p(320f, 220f)
                l(h, v1, "h"); l(v1, v2, "t"); l(v2, v3, "t"); l(v3, v1, "t"); l(v2, g1, "A"); l(v3, g2, "A")
            }, "A triangle: finite, a C₀ function of m_t and m_h."),
        )
    }
}
