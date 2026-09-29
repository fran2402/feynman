package com.example.feynman.physics

/*
 * Renormalization in the MS-bar scheme: the counterterm that cancels a one-loop UV pole,
 * written with the usual constants.
 *
 *   fermion self-energy:  counterterm i(p̸ δZ − δM),     δZ = −A/K, δM = B/K   (pole A p̸ + B)
 *   vector self-energy:   counterterm −iδZ(p²g − pp) + iδM² g,  δZ = a/K, δM² = −c/K
 *   scalar self-energy:   counterterm i(δZ p² − δM²),   δZ = −a/K, δM² = c/K
 *   vertex:               counterterm δ₁ × (tree vertex),  δ₁ = −i (pole/tree)/K
 *
 * with K = 16π²ε̄. For photon and gluon self-energies the pole also gives the loop's share of
 * the β function (with Z₁ = Z₂, as in QED or the background-field gauge): β(g) ⊃ −a₀ g³/(16π²)
 * for a pole a = a₀ g² p²(g − pp/p²).
 */
object Renorm {
    private const val K = "16\\pi^{2}\\bar\\epsilon"

    /** e/K written as (e) × 1/(16π²ε̄), the sign in front. */
    private fun over(e: Expr, sign: Int = 1): String {
        val t = Tex.of(if (sign < 0) -e else e)
        if (t == "0") return "0"
        val neg = t.startsWith("-") && !Tex.needsParen(t.drop(1))
        val body = if (neg) t.drop(1) else t
        return (if (neg) "-" else "") + "${Tex.paren(body)}\\,\\frac{1}{$K}"
    }

    /** The structure of a term: its non-symbol atoms and Dirac strings. */
    private fun split(e: Expr): Map<TermKey, Expr> {
        val out = LinkedHashMap<TermKey, Expr>()
        for ((k, c) in e.terms) {
            val s = k.mono.factors.filter { (a, _) -> !(a is Sym) }
            val n = k.mono.factors.filter { (a, _) -> a is Sym }
            val key = TermKey(Mono(s), k.chains)
            out[key] = (out[key] ?: Expr.ZERO) + Expr(mapOf(TermKey(Mono(n), emptyList()) to c))
        }
        return out
    }

    fun steps(amp: Amplitude, r: LoopResult, ctx: RuleContext, others: List<Diagram>): Step? {
        if (r.isFinite) return Step("Renormalization", listOf(Block.Text("The integral is UV finite, so no counterterm is needed.")))
        val ext = amp.externals
        val blocks = ArrayList<Block>()
        blocks.add(Block.Text("In the MS-bar scheme the counterterm cancels exactly the \\(1/\\bar\\epsilon\\) pole, \\(i\\mathcal{M}_{\\text{ct}} = -\\frac{i}{$K}\\times\\text{(pole)}\\)."))
        when {
            ext.size == 2 && ext[0].particle.isFermion -> fermion(r.pole, amp, blocks)
            ext.size == 2 && ext[0].particle.isVector -> vector(r.pole, amp, blocks)
            ext.size == 2 && ext[0].particle.spin == Spin.Scalar -> scalar(r.pole, blocks)
            ext.size == 3 -> vertex(amp, r, ctx, others, blocks)
            else -> blocks.add(Block.Math("i\\mathcal{M}_{\\text{ct}} = -\\frac{i}{$K}\\left(${Tex.of(r.pole)}\\right)"))
        }
        return Step("Renormalization", blocks)
    }

    /** δZ (per 1/K) of a fermion self-energy, if the pole has the vector form. */
    fun fermionDeltaZ(pole: Expr, p: String): Expr? {
        var a = Expr.ZERO
        for ((k, c) in split(pole)) {
            if (k.mono.factors.isNotEmpty()) return null
            when (k.chains.singleOrNull()) {
                listOf(G.S(p)) -> a += c
                emptyList<G>() -> {}
                else -> return null
            }
        }
        return -a
    }

    private fun fermion(pole: Expr, amp: Amplitude, out: MutableList<Block>) {
        val p = amp.externals[0].momentum
        var a = Expr.ZERO; var b = Expr.ZERO; var a5 = Expr.ZERO; var b5 = Expr.ZERO
        for ((k, c) in split(pole)) when (k.chains.singleOrNull()) {
            listOf(G.S(p)) -> a += c
            emptyList<G>() -> b += c
            listOf(G.S(p), G.Five) -> a5 += c
            listOf(G.Five) -> b5 += c
            else -> {}
        }
        out.add(Block.Text("Counterterm vertex \\(i(\\slashed{p}\\,\\delta Z - \\delta M)\\) with \\(\\delta M = \\delta m + m\\,\\delta Z\\):"))
        if (a5.isZero) out.add(Block.Math("\\delta Z = ${over(a, -1)}"))
        else {
            out.add(Block.Text("The pole has a \\(\\gamma^5\\) part, so left and right fields renormalize differently:"))
            out.add(Block.Math("\\delta Z_L = ${over(a - a5, -1)},\\quad \\delta Z_R = ${over(a + a5, -1)}"))
        }
        out.add(Block.Math("\\delta M = ${over(b)}"))
        if (!b5.isZero) out.add(Block.Math("\\delta M_5 = ${over(b5)}\\ (\\gamma^5\\text{ mass term})"))
        amp.externals[0].particle.mass?.let { m ->
            val z = if (a5.isZero) -a else Expr.ZERO
            if (!a5.isZero) return@let
            // δm = δM − m δZ
            val dm = b - sym(m) * z
            out.add(Block.Math("\\delta m = ${over(dm)}"))
        }
    }

    private fun vector(pole: Expr, amp: Amplitude, out: MutableList<Block>) {
        val (x1, x2) = amp.externals
        val p = x1.momentum
        val e1 = if (x1.incoming) "eps${x1.number}" else "eps${x1.number}*"
        val e2 = if (x2.incoming) "eps${x2.number}" else "eps${x2.number}*"
        val ee = Dot.of(e1, e2)
        val pe = Mono.of(listOf(Dot.of(p, e1) to 1, Dot.of(p, e2) to 1))
        var a = Expr.ZERO; var b = Expr.ZERO; var c = Expr.ZERO
        for ((k, coef) in pole.terms) {
            val m = k.mono
            val psq = m.power(Mandelstam.p2)
            val rest = Mono(m.factors.filter { (at, _) -> at is Sym && at != Mandelstam.p2 })
            val structure = Mono(m.factors.filter { (at, _) -> !(at is Sym) })
            val term = Expr(mapOf(TermKey(rest, emptyList()) to coef))
            when {
                structure == Mono.of(ee) && psq == 1 -> a += term
                structure == Mono.of(ee) && psq == 0 -> c += term
                structure == pe && psq == 0 -> b += term
            }
        }
        out.add(Block.Text("Counterterm vertex \\(-i\\,\\delta Z\\,(p^2 g^{\\mu\\nu} - p^\\mu p^\\nu) + i\\,\\delta M^2 g^{\\mu\\nu}\\):"))
        out.add(Block.Math("\\delta Z = ${over(a)}"))
        if (!c.isZero) out.add(Block.Math("\\delta M^{2} = ${over(c, -1)}"))
        out.add(if ((a + b).isZero) Block.Text("The pole is transverse, \\(\\propto p^2 g^{\\mu\\nu} - p^\\mu p^\\nu\\), as gauge invariance requires.")
        else Block.Note("Longitudinal part \\(${Tex.of(a + b)}\\): not transverse on its own (gauge invariance needs the other diagrams of this order, e.g. ghost loops)."))
        // β function from a photon or gluon self-energy.
        val v = x1.particle
        val g = when (v) { SM.photon -> Couplings.e; SM.gluon -> Couplings.gs; else -> null } ?: return
        val a0 = a * sym(g, -2)
        if (a0.atoms().any { it is Sym && (it == g) }) return
        val colorCoef = if (v === SM.gluon) amp.terms.singleOrNull()?.let { t ->
            val ext = amp.externals.mapNotNull { amp.colorIndices[it.line.id] }
            Color.coefficient(Color.tensor(t.color, ext), ext)
        } else CQ.ONE
        colorCoef ?: return
        val beta = -(a0 * colorCoef)
        out.add(Block.Text("With \\(Z_1 = Z_2\\) (QED, or the background-field gauge), this loop adds to the β function:"))
        out.add(Block.Math("\\beta(${g.tex}) \\supset ${Tex.of(beta).let { if (it == "0") "0" else "${Tex.paren(it)}\\,\\frac{${g.tex}^{3}}{16\\pi^{2}}" }}"))
        if (v === SM.gluon) out.add(Block.Text("(Including the color factor. For QCD the full coefficient, \\(-\\frac{g_s^3}{16\\pi^2}\\left(11 - \\frac{2}{3}n_f\\right)\\), needs the gluon and ghost loops and the vertex too.)"))
    }

    private fun scalar(pole: Expr, out: MutableList<Block>) {
        var a = Expr.ZERO; var c = Expr.ZERO
        for ((k, coef) in pole.terms) {
            val psq = k.mono.power(Mandelstam.p2)
            val term = Expr(mapOf(TermKey(k.mono.without(Mandelstam.p2), emptyList()) to coef))
            if (psq == 1) a += term else if (psq == 0) c += term
        }
        out.add(Block.Text("Counterterm vertex \\(i(\\delta Z\\, p^2 - \\delta M^2)\\):"))
        out.add(Block.Math("\\delta Z = ${over(a, -1)},\\quad \\delta M^{2} = ${over(c)}"))
    }

    /** The tree-level vertex with the same external particles. */
    private fun treeVertex(amp: Amplitude, ctx: RuleContext): Pair<Amplitude, Expr>? {
        val d = amp.topology.diagram
        val centre = Point(9999, 0f, 0f)
        val points = ArrayList<Point>(); val lines = ArrayList<Line>()
        points.add(centre)
        for (x in amp.externals) {
            val pt = d.point(x.point)
            points.add(pt.copy(io = if (x.incoming) Io.In else Io.Out))
            val l = x.line
            lines.add(if (l.from == x.point) l.copy(to = centre.id, bend = 0f) else l.copy(from = centre.id, bend = 0f))
        }
        val tree = Amplitude.build(Diagram(points, lines), RuleContext(ctx.conv, ctx.massOf, ctx.ckmIdentity, IndexNames(), ctx.theory, ctx.gauge))
        if (!tree.ok || tree.terms.size != 1) return null
        val e = reduceChains(contract(tree.terms[0].expr), tree.chains.map { it.left to it.right })
        return tree to Kinematics(tree.externals, ctx.massOf).apply(e)
    }

    /** δ₁ (per 1/K, times −i) of a vertex diagram: pole = λ × tree. */
    fun vertexRatio(amp: Amplitude, r: LoopResult, ctx: RuleContext): Expr? {
        val (_, t) = treeVertex(amp, ctx) ?: return null
        val tp = split(t)
        val pp = split(r.pole)
        if (tp.keys != pp.keys) return null
        var lambda: Expr? = null
        for ((k, tv) in tp) {
            val (tk, tc) = tv.terms.entries.singleOrNull() ?: return null
            val ratio = Expr(pp[k]!!.terms.mapKeys { (kk, _) -> TermKey(kk.mono * tk.mono.inverse(), emptyList()) }.mapValues { it.value / tc })
            if (lambda == null) lambda = ratio else if (lambda != ratio) return null
        }
        return lambda
    }

    private fun vertex(amp: Amplitude, r: LoopResult, ctx: RuleContext, others: List<Diagram>, out: MutableList<Block>) {
        val lambda = vertexRatio(amp, r, ctx)
        if (lambda == null) {
            out.add(Block.Math("i\\mathcal{M}_{\\text{ct}} = -\\frac{i}{$K}\\left(${Tex.of(r.pole)}\\right)"))
            out.add(Block.Text("The pole isn't a multiple of the tree vertex, so it's written as it is."))
            return
        }
        val delta1 = lambda * CQ.imag(-1)
        out.add(Block.Text("The pole is the tree vertex times a number, so the counterterm is the tree vertex times"))
        out.add(Block.Math("\\delta_1 = ${over(delta1)}"))
        // Ward identity: compare with a fermion self-energy in another tab.
        val fermion = amp.externals.firstOrNull { it.particle.isFermion }?.particle ?: return
        for (d in others) {
            val o = Amplitude.build(d, ctx)
            if (!o.ok || o.topology.loops != 1 || o.externals.size != 2 || o.externals[0].particle !== fermion) continue
            val lr = Loop.evaluate(o, Kinematics(o.externals, ctx.massOf)) ?: continue
            val dz = fermionDeltaZ(lr.pole, o.externals[0].momentum) ?: continue
            out.add(Block.Text("From the ${fermion.name.replaceFirstChar { it.lowercase() }} self-energy in \"${d.name}\":"))
            out.add(Block.Math("\\delta_2 = ${over(dz)}"))
            out.add(if (dz == delta1) Block.Text("\\(\\delta_1 = \\delta_2\\): the Ward identity holds.") else Block.Note("\\(\\delta_1 \\ne \\delta_2\\) here (expected only in QED-like vertices)."))
            break
        }
    }
}
