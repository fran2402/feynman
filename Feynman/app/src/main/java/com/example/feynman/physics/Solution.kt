package com.example.feynman.physics

/*
 * The worked solution for a diagram, step by step, as the app shows it: the process, the
 * rules used (with the paper's equation numbers), iℳ, and then either the spin-summed |ℳ|²
 * (trees) or the loop integral worked through to its UV pole and finite part (one loop).
 */

sealed class Block {
    class Text(val text: String) : Block()
    /** Display maths. */
    class Math(val tex: String, val tag: String? = null) : Block()
    /** A rule: what it is, the paper's equation number and the factor. */
    class Rule(val what: String, val eq: Int, val tex: String, val tag: String = "($eq)") : Block()
    class Note(val text: String) : Block()
}

class Step(val title: String, val blocks: List<Block>)

data class SolveOptions(
    val conv: Conventions = Conventions(),
    /** Neglect every fermion mass except the top quark's. */
    val masslessFermions: Boolean = false,
    val ckmIdentity: Boolean = false,
    val theory: Theory = Theory.SM,
    val gauge: Gauge = Gauge.Feynman,
    /** Width of unstable bosons in the numbers (Breit–Wigner). */
    val widths: Boolean = true,
)

class Solution(
    val steps: List<Step>,
    val amplitude: Amplitude?,
    val loop: LoopResult?,
    val squared: SquaredResult?,
    val issues: List<Issue>,
    /** Every symbol a numerical evaluation needs a value for. */
    val symbols: List<Sym>,
)

object Solver {
    fun context(o: SolveOptions) = RuleContext(
        o.conv,
        massOf = { p -> if (o.masslessFermions && p.isFermion && p !== SM.top) null else p.mass },
        ckmIdentity = o.ckmIdentity,
        theory = o.theory,
        gauge = o.gauge,
    )

    /** Pulls out the common factor of every term (symbols' lowest powers and the coefficients' gcd). */
    fun factor(e: Expr): Pair<Expr, Expr> {
        if (e.terms.size < 2) return Expr.ONE to e
        val keys = e.terms.keys.toList()
        val atoms = keys.flatMap { k -> k.mono.factors.map { it.first } }.filter { it is Sym || it is Den }.toSet()
        val common = ArrayList<Pair<Atom, Int>>()
        for (a in atoms) {
            val powers = keys.map { it.mono.power(a) }
            val lo = powers.min()
            val hi = powers.max()
            // Only whole factors that every term has with the same sign of power.
            if (lo > 0) common.add(a to lo) else if (hi < 0) common.add(a to hi)
        }
        val coefs = e.terms.values.toList()
        val allReal = coefs.all { it.isReal }
        val allImag = coefs.all { it.isImaginary }
        var num = java.math.BigInteger.ZERO
        var den = java.math.BigInteger.ONE
        if (allReal || allImag) {
            for (c in coefs) {
                val r = if (allReal) c.re else c.im
                num = num.gcd(r.num)
                den = den * r.den / den.gcd(r.den)
            }
        }
        var g = if (num.signum() == 0) CQ.ONE else CQ.of(Rational.of(num, den))
        // Take the sign of the first term out too, so the bracket starts positively.
        val firstSign = if (allReal) coefs.first().re.signum else if (allImag) coefs.first().im.signum else 1
        if (firstSign < 0) g = -g
        if (allImag) g = g * CQ.I
        val commonMono = Mono.of(common)
        if (common.isEmpty() && g == CQ.ONE) return Expr.ONE to e
        val inv = commonMono.inverse()
        val rest = Expr(e.terms.map { (k, c) -> TermKey(k.mono * inv, k.chains) to c / g }.toMap())
        return Expr(mapOf(TermKey(commonMono, emptyList()) to g)) to rest
    }

    /** A factored expression as LaTeX: common factor × (rest). */
    fun factoredTex(e: Expr, names: IndexNames = IndexNames(), open: (Int) -> String = { "" }, close: (Int) -> String = { "" }): String {
        val (c, rest) = factor(e)
        if (c == Expr.ONE) return Tex.of(e, names, open, close)
        val ct = Tex.of(c, names)
        val rt = Tex.of(rest, names, open, close)
        val prefix = when (ct) { "1" -> ""; "-1" -> "-"; else -> ct }
        return if (rest.terms.size == 1) "$prefix\\, $rt" else "$prefix\\left($rt\\right)"
    }

    private fun processTex(externals: List<External>): String {
        fun leg(x: External) = "${x.tex}(${MomNames.tex(x.momentum)})"
        val ins = externals.filter { it.incoming }.joinToString("\\, ") { leg(it) }
        val outs = externals.filter { !it.incoming }.joinToString("\\, ") { leg(it) }
        return "${ins.ifEmpty { "0" }} \\to ${outs.ifEmpty { "0" }}"
    }

    fun solve(diagram: Diagram, options: SolveOptions, others: List<Diagram> = emptyList()): Solution {
        val ctx = context(options)
        val amp = Amplitude.build(diagram, ctx)
        val steps = ArrayList<Step>()
        val topo = amp.topology
        if (topo.externals.isEmpty() || topo.vertices.isEmpty()) {
            return Solution(listOf(Step("Draw a diagram", listOf(Block.Text("Add vertices and lines, with at least one external line, to see its amplitude.")))), null, null, null, amp.issues, emptyList())
        }
        steps.add(Step("Process", buildList {
            add(Block.Math(processTex(topo.externals)))
            add(Block.Text(when (topo.loops) {
                0 -> "Tree level: ${topo.vertices.size} vertices, ${topo.internal.size} internal line${if (topo.internal.size == 1) "" else "s"}."
                1 -> "One loop, loop momentum \\(k\\)."
                else -> "${topo.loops} loops."
            }))
        }))
        if (amp.issues.isNotEmpty()) {
            steps.add(Step("Problems", amp.issues.map { Block.Note(it.message) }))
            return Solution(steps, amp, null, null, amp.issues, emptyList())
        }
        // Rules.
        steps.add(Step("Feynman rules", amp.rules.map { (what, r) -> Block.Rule(what, r.eq, r.tex, r.tag) } +
            Block.Text("Equation numbers are Romão & Silva's. Signs: ${ConventionPresets.nameOf(options.conv)} (η = ${sign(options.conv.eta)}, η_e = ${sign(options.conv.etaE)}, η_Z = ${sign(options.conv.etaZ)}, η_s = ${sign(options.conv.etaS)}, η_G = ${sign(options.conv.etaG)}); ${options.gauge.label} gauge, \\(${options.gauge.tex}\\); ${options.theory.label}.")))
        // Momenta.
        val momBlocks = ArrayList<Block>()
        for (l in topo.internal) {
            val q = topo.momenta[l.id] ?: continue
            val p = SM.byId(l.particle) ?: continue
            momBlocks.add(Block.Math("${p.tex}:\\quad q = ${MomNames.tex(q)}"))
        }
        if (momBlocks.isNotEmpty()) steps.add(Step("Momenta", listOf(Block.Text("Momentum is conserved at each vertex; incoming momenta flow in, outgoing ones out.")) + momBlocks))
        // Amplitude.
        val colorTex = if (amp.terms.size == 1 && amp.terms[0].color.isNotEmpty()) amp.colorTexOf(amp.terms[0]) else ""
        steps.add(Step("Amplitude", buildList {
            add(Block.Math("i\\mathcal{M} = ${amp.tex}"))
            if (amp.symmetry != Rational.ONE) add(Block.Text("Symmetry factor \\(S = ${amp.symmetry.reciprocal()}\\)."))
            if (amp.loopSign < 0) add(Block.Text("A closed fermion or ghost loop gives a factor \\(-1\\) and a trace."))
            if (amp.terms.size == 1) {
                val e = contract(amp.terms[0].expr)
                val open = { i: Int -> Amplitude.spinorTex(amp.chains[i].left) + "\\, " }
                val close = { i: Int -> "\\, " + Amplitude.spinorTex(amp.chains[i].right) }
                val body = factoredTex(Rules.simplifyRoots(e), IndexNames(), open, close)
                if (topo.loops == 0) {
                    add(Block.Text("Contracting the indices:"))
                    add(Block.Math("i\\mathcal{M} = ${if (colorTex.isNotEmpty()) "$colorTex\\, " else ""}$body"))
                }
            }
        }))
        var squared: SquaredResult? = null
        var loop: LoopResult? = null
        val kin = Kinematics(topo.externals, ctx.massOf)
        if (topo.loops == 0) {
            if (topo.externals.size >= 3) {
                val same = others.map { Amplitude.build(it, ctx) }.filter { o -> o.ok && sameProcess(o, amp) }
                val amps = listOf(amp) + same
                squared = Squared.compute(amps, kin)
                steps.add(Step("Spin sums", buildList {
                    if (amps.size > 1) add(Block.Text("Adding ${amps.size} diagrams with the same particles (relative signs from the order of the external fermions: ${amps.joinToString(", ") { if (it.fermionSign > 0) "+" else "−" }})."))
                    add(Block.Text("Average over incoming spins and colors, sum over outgoing ones: \\(\\sum u\\bar u = \\slashed{p} + m\\), \\(\\sum v\\bar v = \\slashed{p} - m\\), \\(\\sum \\varepsilon_\\mu\\varepsilon^*_\\nu = -g_{\\mu\\nu}\\) (\\(+k_\\mu k_\\nu/M^2\\) for massive vectors)."))
                    squared.traceTex?.let { add(Block.Math(it)) }
                    squared.notes.forEach { add(Block.Note(it)) }
                }))
                steps.add(Step("Result", buildList {
                    val lhs = "\\overline{|\\mathcal{M}|^{2}}"
                    add(Block.Math("$lhs = ${factoredTex(squared.dots)}"))
                    if (kin.description.isNotEmpty()) {
                        add(Block.Text("With"))
                        kin.description.forEach { add(Block.Math(it)) }
                        add(Block.Math("$lhs = ${factoredTex(squared.result)}"))
                    }
                }))
            } else {
                steps.add(Step("Result", listOf(Block.Text("With fewer than three external particles there's nothing to square: this is a propagator correction or a vacuum piece."))))
            }
        } else if (topo.loops == 1) {
            loop = Loop.evaluate(amp, kin)
            if (loop != null) steps.addAll(loopSteps(amp, loop, kin, ctx, others))
        } else {
            steps.add(Step("Loop integrals", listOf(Block.Text("Integrals with ${topo.loops} loops are written out above but not evaluated: the app works one-loop integrals through."))))
        }
        val symbols = (listOfNotNull(squared?.dots, squared?.result, loop?.pole).flatMap { it.atoms() } +
            (loop?.pieces.orEmpty().flatMap { it.finiteIntegrand.atoms() + it.delta.atoms() }))
            .flatMap { a -> if (a is Den) a.content.atoms() else listOf(a) }
            .filterIsInstance<Sym>().filter { it != Loop.deltaSym && it != Loop.lnDelta && it != Rules.sqrt2 && !(loop?.pieces.orEmpty().any { p -> it in p.xs }) }
            .distinctBy { it.name }.sortedBy { it.key }
        return Solution(steps, amp, loop, squared, amp.issues, symbols)
    }

    private fun sign(n: Int) = if (n > 0) "+" else "−"

    /** Same external particles, in or out, with the same momenta. */
    fun sameProcess(a: Amplitude, b: Amplitude): Boolean {
        if (a.externals.size != b.externals.size) return false
        return a.externals.zip(b.externals).all { (x, y) -> x.particle === y.particle && x.incoming == y.incoming && x.anti == y.anti }
    }

    private fun loopSteps(amp: Amplitude, r: LoopResult, kin: Kinematics, ctx: RuleContext, others: List<Diagram>): List<Step> {
        val steps = ArrayList<Step>()
        val open = { i: Int -> Amplitude.spinorTex(r.chains[i].left) + "\\, " }
        val close = { i: Int -> "\\, " + Amplitude.spinorTex(r.chains[i].right) }
        val many = r.pieces.size > 1
        if (many) steps.add(Step("Loop integrals", listOf(
            Block.Text("The gauge-boson propagators split the amplitude into ${r.pieces.size} integrals with different denominators; each is worked out below and the results are added."),
        )))
        for ((pi, piece) in r.pieces.withIndex()) {
            val suffix = if (many) " (integral ${pi + 1})" else ""
            val n = piece.n
            steps.add(Step("Denominators$suffix", buildList {
                piece.denominators.forEachIndexed { i, d -> add(Block.Math("D_{${i + 1}} = ${d.tex}")) }
                if (pi == 0) add(Block.Text("In \\(d = 4 - 2\\epsilon\\) dimensions, \\(\\int d^4k/(2\\pi)^4 \\to \\mu^{2\\epsilon}\\int d^dk/(2\\pi)^d\\)."))
                add(Block.Math("\\text{Scalar integral: } \\frac{i}{16\\pi^{2}}\\, ${piece.scalarName}"))
            }))
            if (n > 1) steps.add(Step("Feynman parameters$suffix", buildList {
                val xs = piece.xs.map { it.tex }
                val fact = factorial(n - 1)
                add(Block.Math("\\frac{1}{D_{1}\\cdots D_{$n}} = ${if (fact == 1L) "" else "$fact"}\\int_{0}^{1}${xs.joinToString("") { "d$it\\," }}\\frac{\\delta\\left(1 - ${xs.joinToString(" - ")}\\right)}{\\left(${(1..n).joinToString(" + ") { "${xs[it - 1]} D_{$it}" }}\\right)^{$n}}"))
                add(Block.Text("Shifting \\(k = \\ell - P\\) completes the square, \\(\\sum x_i D_i = \\ell^2 - \\Delta\\), with"))
                add(Block.Math("P = ${piece.shift.joinToString(" + ") { (c, m) -> "${Tex.paren(Tex.of(c))}\\,${MomNames.tex(m)}" }.ifEmpty { "0" }}".replace("+ -", "- ")))
                add(Block.Math("\\Delta = ${Tex.of(piece.delta)}"))
                if (n == 2) add(Block.Text("with \\(x_2 = 1 - x\\)."))
                else add(Block.Text("with \\(${piece.xs.last().tex} = 1 - ${piece.xs.dropLast(1).joinToString(" - ") { it.tex }}\\)."))
            })) else steps.add(Step("Shift$suffix", listOf(Block.Math("\\Delta = ${Tex.of(piece.delta)}"))))
            steps.add(Step("Numerator$suffix", buildList {
                add(Block.Text("After the traces and index contractions (before the shift):"))
                add(Block.Math("N = ${Tex.of(piece.numerator, IndexNames(), open, close)}"))
                add(Block.Text("After \\(k = \\ell - P\\), odd powers of \\(\\ell\\) vanish and \\(\\ell^\\mu\\ell^\\nu \\to g^{\\mu\\nu}\\ell^2/d\\):"))
                for ((a, e) in piece.reduced.toSortedMap()) {
                    val power = when (a) { 0 -> ""; 1 -> "\\ell^{2}\\,"; else -> "(\\ell^{2})^{$a}\\," }
                    add(Block.Math("N_{$a} = $power${Tex.of(e, IndexNames(), open, close)}"))
                }
            }))
        }
        steps.add(Step("Master integrals", listOf(
            Block.Math("\\int\\frac{d^{d}\\ell}{(2\\pi)^{d}}\\frac{(\\ell^{2})^{a}}{(\\ell^{2} - \\Delta)^{n}} = \\frac{(-1)^{n+a} i}{(4\\pi)^{d/2}}\\frac{\\Gamma(a + \\frac{d}{2})\\Gamma(n - a - \\frac{d}{2})}{\\Gamma(\\frac{d}{2})\\Gamma(n)}\\Delta^{\\frac{d}{2} + a - n}"),
            Block.Text("Expanded about \\(d = 4\\) with \\(\\frac{1}{\\bar\\epsilon} = \\frac{1}{\\epsilon} - \\gamma_E + \\ln 4\\pi\\) (the MS-bar scheme):"),
            Block.Math("\\Gamma(\\epsilon)\\left(\\frac{4\\pi\\mu^{2}}{\\Delta}\\right)^{\\epsilon} = \\frac{1}{\\bar\\epsilon} - \\ln\\frac{\\Delta}{\\mu^{2}} + O(\\epsilon)"),
        )))
        steps.add(Step("Result", buildList {
            val lhs = "i\\mathcal{M}"
            val colorTex = amp.terms.singleOrNull()?.takeIf { it.color.isNotEmpty() }?.let { colorFactorTex(amp, it) }
            val cpre = colorTex?.let { "$it\\, " } ?: ""
            if (r.isFinite) add(Block.Text("No UV pole: the integral is finite."))
            else add(Block.Math("$lhs\\big|_{\\text{UV}} = \\frac{i}{16\\pi^{2}\\bar\\epsilon}\\, $cpre${factoredTex(r.pole, IndexNames(), open, close)}"))
            add(Block.Text("The finite part:"))
            val parts = ArrayList<String>()
            if (!r.finitePolynomial.isZero) parts.add(factoredTex(r.finitePolynomial, IndexNames(), open, close))
            for (piece in r.pieces) if (!piece.finiteRest.isZero) {
                val measure = if (piece.n == 1) "" else if (piece.n == 2) "\\int_{0}^{1}dx\\," else "\\int dF\\,"
                parts.add("$measure${Tex.paren(factoredTex(piece.finiteRest, IndexNames(), open, close))}")
            }
            add(Block.Math("$lhs\\big|_{\\text{fin}} = \\frac{i}{16\\pi^{2}}\\, $cpre${if (parts.isEmpty()) "0" else parts.joinToString(" + ")}".replace("+ -", "- ")))
            if (many) add(Block.Text("Each integral has its own \\(\\Delta\\) (given with it above)."))
            val n = r.pieces.maxOf { it.n }
            if (n > 2) add(Block.Text("\\(\\int dF\\) runs over the Feynman parameters \\(\\geq 0\\) with the free ones adding up to at most 1."))
            r.notes.forEach { add(Block.Note(it)) }
            add(Block.Text("Give the masses and momenta values under Numbers to evaluate it."))
        }))
        steps.add(Step("Passarino–Veltman functions", buildList {
            add(Block.Text("The scalar integrals, with the loop factor \\(i/16\\pi^2\\) taken out:"))
            add(Block.Math(Passarino.A0_TEX))
            add(Block.Math(Passarino.B0_TEX))
            add(Block.Math(Passarino.B0_R_TEX))
            add(Block.Math("C_0 = -\\int dF\\, \\frac{1}{\\Delta - i0},\\qquad D_0 = \\int dF\\, \\frac{1}{(\\Delta - i0)^{2}}"))
            add(Block.Text("Two-point integrals are evaluated in closed form (every \\(\\int_0^1 x^k \\ln\\Delta\\,dx\\) exactly, at the roots of the quadratic \\(\\Delta\\)); \\(C_0\\) with its inner integral in closed form; \\(D_0\\) numerically. Values are under Numbers."))
        }))
        Renorm.steps(amp, r, ctx, others)?.let { steps.add(it) }
        return steps
    }

    private fun colorFactorTex(amp: Amplitude, t: AmpTerm): String? {
        val externals = amp.externals.mapNotNull { x -> amp.colorIndices[x.line.id] }
        return runCatching { Color.describe(Color.tensor(t.color, externals), externals) }.getOrNull()
    }

    private fun factorial(k: Int): Long = (1..k).fold(1L) { acc, j -> acc * j }
}
