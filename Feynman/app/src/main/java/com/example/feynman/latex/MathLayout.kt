package com.example.feynman.latex

import kotlin.math.max

/*
 * A small TeX: parses the LaTeX the app writes (fractions, scripts, roots, stretchy
 * delimiters, accents, \slashed, big operators, Greek, \text) and lays it out in boxes the
 * way TeX does, in Computer Modern. Plain Kotlin: the app draws the result with Android's
 * Canvas, and the previews (preview-render) with Java2D, through [MathFonts].
 */

enum class MathFont { Roman, Italic, Cal, Big }

/** Measures text in the math fonts; sizes are in pixels. */
interface MathFonts {
    fun width(text: String, font: MathFont, size: Float): Float
    /** Height of the ink above the baseline. */
    fun ascent(text: String, font: MathFont, size: Float): Float
    /** Depth of the ink below the baseline. */
    fun descent(text: String, font: MathFont, size: Float): Float
    /** Whether the font has a glyph for this character (else Roman is used). */
    fun has(font: MathFont, codePoint: Int): Boolean = true
}

/** Something to draw, relative to the box's baseline at x = 0. */
sealed class Draw {
    data class Text(val x: Float, val y: Float, val text: String, val font: MathFont, val size: Float) : Draw()
    /** A filled rectangle (fraction bars, overlines, rules). */
    data class Rule(val x: Float, val y: Float, val w: Float, val h: Float) : Draw()
    /** A stroked polyline. */
    data class Stroke(val points: List<Pair<Float, Float>>, val width: Float) : Draw()
    fun shift(dx: Float, dy: Float): Draw = when (this) {
        is Text -> copy(x = x + dx, y = y + dy)
        is Rule -> copy(x = x + dx, y = y + dy)
        is Stroke -> copy(points = points.map { (a, b) -> a + dx to b + dy })
    }
}

class Box(val width: Float, val ascent: Float, val descent: Float, val items: List<Draw>) {
    val height get() = ascent + descent
    fun shifted(dx: Float, dy: Float) = items.map { it.shift(dx, dy) }
    companion object { val EMPTY = Box(0f, 0f, 0f, emptyList()) }
}

/** The parsed formula. */
sealed class Node {
    enum class Kind { Ord, Bin, Rel, Open, Close, Punct, Op, Inner }
    data class Glyph(val text: String, val font: MathFont, val kind: Kind = Kind.Ord) : Node()
    data class Row(val items: List<Node>) : Node()
    data class Frac(val num: Node, val den: Node) : Node()
    data class Scripts(val base: Node, val sup: Node?, val sub: Node?) : Node()
    data class Sqrt(val body: Node) : Node()
    data class Fenced(val left: String, val body: Node, val right: String) : Node()
    data class Space(val em: Float) : Node()
    data class Accent(val kind: String, val body: Node) : Node()
    /** ∫, ∑, ∏, ∮ with optional limits. */
    data class BigOp(val glyph: String, val sub: Node?, val sup: Node?, val limits: Boolean) : Node()
    /** A function name or \text: upright, as a word. */
    data class Word(val text: String, val kind: Kind = Kind.Ord) : Node()
}

object MathParser {
    private val greek = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ϵ", "varepsilon" to "ε",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ",
        "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ", "sigma" to "σ",
        "tau" to "τ", "upsilon" to "υ", "phi" to "ϕ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ",
        "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )
    private val symbols = mapOf(
        "cdot" to ("⋅" to Node.Kind.Bin), "times" to ("×" to Node.Kind.Bin), "pm" to ("±" to Node.Kind.Bin), "mp" to ("∓" to Node.Kind.Bin),
        "otimes" to ("⊗" to Node.Kind.Bin), "to" to ("→" to Node.Kind.Rel), "rightarrow" to ("→" to Node.Kind.Rel), "leftarrow" to ("←" to Node.Kind.Rel),
        "leq" to ("≤" to Node.Kind.Rel), "geq" to ("≥" to Node.Kind.Rel), "neq" to ("≠" to Node.Kind.Rel), "approx" to ("≈" to Node.Kind.Rel),
        "equiv" to ("≡" to Node.Kind.Rel), "sim" to ("∼" to Node.Kind.Rel), "propto" to ("∝" to Node.Kind.Rel), "in" to ("∈" to Node.Kind.Rel), "supset" to ("⊃" to Node.Kind.Rel), "subset" to ("⊂" to Node.Kind.Rel),
        "dagger" to ("†" to Node.Kind.Ord), "infty" to ("∞" to Node.Kind.Ord), "partial" to ("∂" to Node.Kind.Ord), "nabla" to ("∇" to Node.Kind.Ord),
        "ell" to ("ℓ" to Node.Kind.Ord), "hbar" to ("ℏ" to Node.Kind.Ord), "prime" to ("′" to Node.Kind.Ord), "ldots" to ("…" to Node.Kind.Inner),
        "cdots" to ("⋯" to Node.Kind.Inner), "dots" to ("…" to Node.Kind.Inner), "circ" to ("∘" to Node.Kind.Bin), "ast" to ("∗" to Node.Kind.Bin),
        "star" to ("⋆" to Node.Kind.Bin), "langle" to ("⟨" to Node.Kind.Open), "rangle" to ("⟩" to Node.Kind.Close), "vert" to ("|" to Node.Kind.Ord),
    )
    private val functions = setOf("ln", "log", "exp", "sin", "cos", "tan", "Re", "Im", "det", "max", "min", "lim", "arg")
    private val bigOps = mapOf("int" to "∫", "sum" to "∑", "prod" to "∏", "oint" to "∮")

    fun parse(s: String): Node = Parser(s).row(null)

    private class Parser(val s: String) {
        var i = 0

        fun row(end: Char?): Node {
            val items = ArrayList<Node>()
            while (i < s.length) {
                val c = s[i]
                if (end != null && c == end) { i++; break }
                if (c == '}' && end == null) { i++; continue }
                if (isRight(i)) break
                val atom = atom() ?: continue
                items.add(scripts(atom))
            }
            return if (items.size == 1) items[0] else Node.Row(items)
        }

        private fun scripts(base: Node): Node {
            var sup: Node? = null
            var sub: Node? = null
            var b = base
            while (i < s.length) {
                skipSpaces()
                if (i >= s.length) break
                when (s[i]) {
                    '^' -> { i++; sup = group() }
                    '_' -> { i++; sub = group() }
                    '\'' -> { i++; sup = Node.Glyph("′", MathFont.Roman) }
                    else -> break
                }
            }
            if (b is Node.BigOp) return b.copy(sub = sub ?: b.sub, sup = sup ?: b.sup)
            return if (sup == null && sub == null) b else Node.Scripts(b, sup, sub)
        }

        /** \right, but not \rightarrow. */
        fun isRight(at: Int) = s.startsWith("\\right", at) && (at + 6 >= s.length || !s[at + 6].isLetter())

        private fun skipSpaces() { while (i < s.length && s[i] == ' ') i++ }

        /** A braced group or a single atom. */
        fun group(): Node {
            skipSpaces()
            if (i < s.length && s[i] == '{') { i++; return row('}') }
            return atom() ?: Node.Row(emptyList())
        }

        private fun command(): String {
            val start = i
            if (i < s.length && s[i].isLetter()) { while (i < s.length && s[i].isLetter()) i++ } else i++
            return s.substring(start, i)
        }

        private fun textArg(): String {
            skipSpaces()
            if (i < s.length && s[i] == '{') {
                var depth = 1
                val start = ++i
                while (i < s.length && depth > 0) { if (s[i] == '{') depth++ else if (s[i] == '}') depth--; i++ }
                return s.substring(start, i - 1)
            }
            return if (i < s.length) s[i++].toString() else ""
        }

        private fun delimiter(): String {
            skipSpaces()
            if (i >= s.length) return "."
            if (s[i] == '\\') {
                i++
                return when (val cmd = command()) { "{" -> "{"; "}" -> "}"; "|" -> "‖"; "langle" -> "⟨"; "rangle" -> "⟩"; "vert" -> "|"; else -> cmd }
            }
            return s[i++].toString()
        }

        fun atom(): Node? {
            val c = s[i]
            when {
                c == ' ' -> { i++; return null }
                c == '{' -> { i++; return row('}') }
                c == '\\' -> {
                    i++
                    val cmd = command()
                    greek[cmd]?.let { g -> return Node.Glyph(g, if (g[0].isUpperCase()) MathFont.Roman else MathFont.Italic) }
                    symbols[cmd]?.let { (g, k) -> return Node.Glyph(g, MathFont.Roman, k) }
                    bigOps[cmd]?.let { g -> return Node.BigOp(g, null, null, cmd == "sum" || cmd == "prod") }
                    if (cmd in functions) return Node.Word(cmd, Node.Kind.Op)
                    return when (cmd) {
                        "frac", "dfrac", "tfrac" -> { val a = group(); val b = group(); Node.Frac(a, b) }
                        "sqrt" -> { skipSpaces(); if (i < s.length && s[i] == '[') { while (i < s.length && s[i] != ']') i++; i++ }; Node.Sqrt(group()) }
                        "left" -> {
                            val l = delimiter()
                            val body = row(null)
                            var r = "."
                            if (isRight(i)) { i += 6; r = delimiter() }
                            Node.Fenced(l, body, r)
                        }
                        "right" -> null
                        "," -> Node.Space(3f / 18)
                        ":", ">" -> Node.Space(4f / 18)
                        ";" -> Node.Space(5f / 18)
                        "!" -> Node.Space(-3f / 18)
                        " " -> Node.Space(6f / 18)
                        "quad" -> Node.Space(1f)
                        "qquad" -> Node.Space(2f)
                        "{" -> Node.Glyph("{", MathFont.Roman, Node.Kind.Open)
                        "}" -> Node.Glyph("}", MathFont.Roman, Node.Kind.Close)
                        "|" -> Node.Glyph("‖", MathFont.Roman)
                        "slashed", "bar", "overline", "hat", "tilde", "vec", "dot", "widehat", "widetilde" -> Node.Accent(cmd, group())
                        "text", "mathrm", "textrm", "operatorname" -> Node.Word(textArg(), if (cmd == "operatorname") Node.Kind.Op else Node.Kind.Ord)
                        "big", "Big", "bigg", "Bigg" -> Node.Space(0f)
                        "mathcal" -> Node.Glyph(textArg(), MathFont.Cal)
                        "mathit" -> Node.Glyph(textArg(), MathFont.Italic)
                        "mathbf", "boldsymbol" -> group()
                        else -> Node.Word(cmd)
                    }
                }
                else -> {
                    i++
                    return when {
                        c.isLetter() -> Node.Glyph(c.toString(), MathFont.Italic)
                        c.isDigit() || c == '.' -> {
                            // A run of digits is one number.
                            val start = i - 1
                            while (i < s.length && (s[i].isDigit() || (s[i] == '.' && i + 1 < s.length && s[i + 1].isDigit()))) i++
                            Node.Glyph(s.substring(start, i), MathFont.Roman)
                        }
                        c == '+' -> Node.Glyph("+", MathFont.Roman, Node.Kind.Bin)
                        c == '-' || c == '−' -> Node.Glyph("−", MathFont.Roman, Node.Kind.Bin)
                        c == '*' -> Node.Glyph("∗", MathFont.Roman, Node.Kind.Bin)
                        c == '=' || c == '<' || c == '>' -> Node.Glyph(c.toString(), MathFont.Roman, Node.Kind.Rel)
                        c == ',' || c == ';' -> Node.Glyph(c.toString(), MathFont.Roman, Node.Kind.Punct)
                        c == '(' || c == '[' -> Node.Glyph(c.toString(), MathFont.Roman, Node.Kind.Open)
                        c == ')' || c == ']' -> Node.Glyph(c.toString(), MathFont.Roman, Node.Kind.Close)
                        c == '~' -> Node.Space(6f / 18)
                        c == '&' -> null
                        else -> Node.Glyph(c.toString(), MathFont.Roman)
                    }
                }
            }
        }
    }
}

/**
 * Lays out a formula at [size] pixels (the body size). Levels: 0 display, 1 script, 2 script-script.
 */
class MathLayout(private val fonts: MathFonts, private val size: Float) {
    private fun sizeOf(level: Int) = when (level) { 0 -> size; 1 -> size * 0.71f; else -> size * 0.55f }
    private fun axis(level: Int) = sizeOf(level) * 0.25f
    private fun rule(level: Int) = max(1f, sizeOf(level) * 0.045f)

    fun layout(n: Node, level: Int = 0): Box = when (n) {
        is Node.Glyph -> glyph(n.text, n.font, level)
        is Node.Word -> word(n.text, level)
        is Node.Row -> row(n.items, level)
        is Node.Space -> { val w = n.em * sizeOf(level); Box(w, 0f, 0f, emptyList()) }
        is Node.Frac -> frac(n, level)
        is Node.Scripts -> scripts(layout(n.base, level), n.sup, n.sub, level, n.base)
        is Node.Sqrt -> sqrt(n, level)
        is Node.Fenced -> fenced(n, level)
        is Node.Accent -> accent(n, level)
        is Node.BigOp -> bigOp(n, level)
    }

    private fun font(text: String, font: MathFont): MathFont =
        if (font == MathFont.Roman || text.isEmpty()) font else if (text.codePoints().allMatch { fonts.has(font, it) }) font else MathFont.Roman

    private fun glyph(text: String, font: MathFont, level: Int): Box {
        val sz = sizeOf(level)
        val f = font(text, font)
        val w = fonts.width(text, f, sz)
        // Italic letters get a little room on the right, like TeX's italic correction.
        val extra = if (f == MathFont.Italic) sz * 0.02f else 0f
        return Box(w + extra, fonts.ascent(text, f, sz), fonts.descent(text, f, sz), listOf(Draw.Text(0f, 0f, text, f, sz)))
    }

    private fun word(text: String, level: Int): Box {
        val sz = sizeOf(level)
        val w = fonts.width(text, MathFont.Roman, sz)
        return Box(w, fonts.ascent(text, MathFont.Roman, sz), fonts.descent(text, MathFont.Roman, sz), listOf(Draw.Text(0f, 0f, text, MathFont.Roman, sz)))
    }

    private fun kindOf(n: Node): Node.Kind = when (n) {
        is Node.Glyph -> n.kind
        is Node.Word -> n.kind
        is Node.BigOp -> Node.Kind.Op
        is Node.Scripts -> kindOf(n.base).let { if (it == Node.Kind.Bin || it == Node.Kind.Rel) it else Node.Kind.Ord }
        is Node.Fenced -> Node.Kind.Inner
        is Node.Frac -> Node.Kind.Inner
        is Node.Space -> Node.Kind.Ord
        else -> Node.Kind.Ord
    }

    /** The spacing TeX puts between two kinds of atom, in mu (1/18 em). */
    private fun spaceBetween(a: Node.Kind, b: Node.Kind, level: Int): Float {
        val mu = when {
            a == Node.Kind.Bin || b == Node.Kind.Bin -> if (level == 0) 4 else 0
            a == Node.Kind.Rel || b == Node.Kind.Rel -> if (level == 0) 5 else 0
            a == Node.Kind.Punct -> if (level == 0) 3 else 0
            a == Node.Kind.Op && (b == Node.Kind.Ord || b == Node.Kind.Open || b == Node.Kind.Inner) -> 3
            a == Node.Kind.Ord && b == Node.Kind.Op -> 3
            a == Node.Kind.Inner && b == Node.Kind.Ord -> if (level == 0) 2 else 0
            a == Node.Kind.Ord && b == Node.Kind.Inner -> if (level == 0) 2 else 0
            a == Node.Kind.Inner && b == Node.Kind.Inner -> if (level == 0) 2 else 0
            a == Node.Kind.Close && b == Node.Kind.Inner -> if (level == 0) 2 else 0
            else -> 0
        }
        return mu * sizeOf(level) / 18f
    }

    /** Kinds with a binary operator made unary where TeX does (at the start, after an operator or an opening). */
    private fun kinds(items: List<Node>): List<Node.Kind> {
        val out = ArrayList<Node.Kind>()
        var prev: Node.Kind? = null
        for (n in items) {
            if (n is Node.Space) { out.add(Node.Kind.Ord); continue }
            var k = kindOf(n)
            if (k == Node.Kind.Bin && (prev == null || prev == Node.Kind.Bin || prev == Node.Kind.Rel || prev == Node.Kind.Open || prev == Node.Kind.Punct || prev == Node.Kind.Op)) k = Node.Kind.Ord
            out.add(k)
            prev = k
        }
        return out
    }

    fun row(items: List<Node>, level: Int): Box {
        val boxes = items.map { layout(it, level) }
        return join(boxes, items, level)
    }

    private fun join(boxes: List<Box>, items: List<Node>, level: Int): Box {
        val ks = kinds(items)
        var x = 0f
        var asc = 0f
        var desc = 0f
        val draws = ArrayList<Draw>()
        var prevKind: Node.Kind? = null
        for ((idx, b) in boxes.withIndex()) {
            val k = ks[idx]
            if (items[idx] !is Node.Space) {
                if (prevKind != null) x += spaceBetween(prevKind, k, level)
                prevKind = k
            }
            draws.addAll(b.shifted(x, 0f))
            x += b.width
            asc = max(asc, b.ascent)
            desc = max(desc, b.descent)
        }
        return Box(x, asc, desc, draws)
    }

    private fun childLevel(level: Int) = if (level == 0) 0 else minOf(level + 1, 2)

    private fun frac(n: Node.Frac, level: Int): Box {
        val cl = if (level == 0) 1 else minOf(level + 1, 2)
        // Display fractions keep full-size numerators, like \dfrac, when short enough.
        val numLevel = if (level == 0) 0 else cl
        val num = layout(n.num, numLevel)
        val den = layout(n.den, numLevel)
        val sz = sizeOf(level)
        val t = rule(level)
        val gap = sz * 0.16f
        val w = max(num.width, den.width) + sz * 0.24f
        val a = axis(level)
        val numShift = a + t / 2 + gap + num.descent
        val denShift = -(a - t / 2 - gap - den.ascent)
        val draws = ArrayList<Draw>()
        draws.addAll(num.shifted((w - num.width) / 2, -numShift))
        draws.addAll(den.shifted((w - den.width) / 2, denShift))
        draws.add(Draw.Rule(sz * 0.06f, -a - t / 2, w - sz * 0.12f, t))
        return Box(w, numShift + num.ascent, denShift + den.descent, draws)
    }

    private fun scripts(base: Box, sup: Node?, sub: Node?, level: Int, baseNode: Node): Box {
        val sl = minOf(level + 1, 2)
        val sz = sizeOf(level)
        val supBox = sup?.let { layout(it, sl) }
        val subBox = sub?.let { layout(it, sl) }
        // Scripts on a tall base (a bracket) sit higher and lower.
        val tall = baseNode is Node.Fenced || baseNode is Node.Frac
        var supShift = if (tall) base.ascent - sz * 0.25f else sz * 0.38f
        var subShift = if (tall) base.descent - sz * 0.1f else sz * 0.18f
        if (supBox != null) supShift = max(supShift, supBox.descent + sz * 0.18f)
        if (subBox != null) subShift = max(subShift, subBox.ascent - sz * 0.32f)
        if (supBox != null && subBox != null) {
            val gap = (supShift - supBox.descent) - (subBox.ascent - subShift)
            if (gap < sz * 0.12f) subShift += sz * 0.12f - gap
        }
        val italicKern = if (baseNode is Node.Glyph && baseNode.font == MathFont.Italic) sz * 0.02f else 0f
        val x = base.width + italicKern
        val draws = ArrayList(base.items)
        var w = base.width
        var asc = base.ascent
        var desc = base.descent
        // Subscripts tuck in under italic letters.
        if (supBox != null) { draws.addAll(supBox.shifted(x, -supShift)); w = max(w, x + supBox.width); asc = max(asc, supShift + supBox.ascent) }
        if (subBox != null) { draws.addAll(subBox.shifted(base.width - italicKern, subShift)); w = max(w, base.width - italicKern + subBox.width); desc = max(desc, subShift + subBox.descent) }
        return Box(w + sz * 0.04f, asc, desc, draws)
    }

    private fun sqrt(n: Node.Sqrt, level: Int): Box {
        val body = layout(n.body, level)
        val sz = sizeOf(level)
        val t = rule(level)
        val gap = sz * 0.12f
        val top = body.ascent + gap + t
        val bottom = body.descent
        val hook = sz * 0.55f
        val draws = ArrayList<Draw>()
        // The radical sign drawn as strokes: a small tick, down to the bottom, up to the bar.
        draws.add(Draw.Stroke(listOf(0f to -sz * 0.2f, hook * 0.25f to -sz * 0.28f, hook * 0.55f to bottom, hook to -top + t / 2), t * 1.1f))
        draws.add(Draw.Rule(hook, -top, body.width + sz * 0.1f, t))
        draws.addAll(body.shifted(hook + sz * 0.05f, 0f))
        return Box(hook + body.width + sz * 0.15f, top + t, bottom + t, draws)
    }

    private fun fenced(n: Node.Fenced, level: Int): Box {
        val body = layout(n.body, level)
        val sz = sizeOf(level)
        val a = axis(level)
        // Symmetric about the axis, at least one line tall.
        val half = max(max(body.ascent - a, body.descent + a), sz * 0.5f) + sz * 0.08f
        val left = delim(n.left, half, level)
        val right = delim(n.right, half, level)
        val draws = ArrayList<Draw>()
        draws.addAll(left.items)
        val bx = left.width + (if (n.left == ".") 0f else sz * 0.04f)
        draws.addAll(body.shifted(bx, 0f))
        val rx = bx + body.width + (if (n.right == ".") 0f else sz * 0.04f)
        draws.addAll(right.shifted(rx, 0f))
        return Box(rx + right.width, max(body.ascent, max(left.ascent, right.ascent)), max(body.descent, max(left.descent, right.descent)), draws)
    }

    /** A delimiter reaching [half] above and below the axis: the font's glyph when it's tall enough, else strokes. */
    private fun delim(d: String, half: Float, level: Int): Box {
        if (d == "." || d.isEmpty()) return Box.EMPTY
        val sz = sizeOf(level)
        val a = axis(level)
        val glyphAsc = fonts.ascent("(", MathFont.Roman, sz)
        val glyphDesc = fonts.descent("(", MathFont.Roman, sz)
        if (half <= (glyphAsc + glyphDesc) / 2 + sz * 0.02f && d in listOf("(", ")", "[", "]", "|", "{", "}", "⟨", "⟩", "‖")) {
            return glyph(d, MathFont.Roman, level)
        }
        val top = -(a + half)
        val bottom = -(a - half)
        val w = sz * (0.28f + 0.04f * (half / sz))
        val t = max(1f, sz * 0.055f)
        val pts: List<Pair<Float, Float>> = when (d) {
            "(" -> arc(w * 0.85f, top, bottom, w * 0.15f, true)
            ")" -> arc(w * 0.15f, top, bottom, w * 0.85f, false)
            "[" -> listOf(w * 0.8f to top, w * 0.3f to top, w * 0.3f to bottom, w * 0.8f to bottom)
            "]" -> listOf(w * 0.2f to top, w * 0.7f to top, w * 0.7f to bottom, w * 0.2f to bottom)
            "|" -> listOf(w * 0.5f to top, w * 0.5f to bottom)
            "‖" -> return Box(w * 1.3f, a + half, half - a, listOf(Draw.Stroke(listOf(w * 0.4f to top, w * 0.4f to bottom), t), Draw.Stroke(listOf(w * 0.9f to top, w * 0.9f to bottom), t)))
            "⟨" -> listOf(w * 0.8f to top, w * 0.2f to (top + bottom) / 2, w * 0.8f to bottom)
            "⟩" -> listOf(w * 0.2f to top, w * 0.8f to (top + bottom) / 2, w * 0.2f to bottom)
            "{" -> brace(w, top, bottom, true)
            "}" -> brace(w, top, bottom, false)
            else -> return glyph(d, MathFont.Roman, level)
        }
        return Box(w, a + half, half - a, listOf(Draw.Stroke(pts, t)))
    }

    /** A parenthesis as a flattened arc from top to bottom bulging toward [bulgeX]. */
    private fun arc(x: Float, top: Float, bottom: Float, bulgeX: Float, left: Boolean): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        val steps = 24
        for (s in 0..steps) {
            val u = s.toFloat() / steps
            val y = top + (bottom - top) * u
            // Parabola-like bulge, flatter in the middle like the printed glyph.
            val b = 1 - Math.pow((2 * u - 1).toDouble(), 2.0).toFloat()
            out.add(x + (bulgeX - x) * (b * 0.85f + 0.15f * kotlin.math.sqrt(b)) to y)
        }
        return out
    }

    private fun brace(w: Float, top: Float, bottom: Float, left: Boolean): List<Pair<Float, Float>> {
        val mid = (top + bottom) / 2
        val (outer, inner, tip) = if (left) Triple(w * 0.75f, w * 0.45f, w * 0.1f) else Triple(w * 0.25f, w * 0.55f, w * 0.9f)
        return listOf(outer to top, inner to top + (mid - top) * 0.15f, inner to mid - (mid - top) * 0.2f, tip to mid,
            inner to mid + (bottom - mid) * 0.2f, inner to bottom - (bottom - mid) * 0.15f, outer to bottom)
    }

    private fun accent(n: Node.Accent, level: Int): Box {
        val body = layout(n.body, level)
        val sz = sizeOf(level)
        val t = rule(level)
        val draws = ArrayList(body.items)
        val italic = (n.body as? Node.Glyph)?.font == MathFont.Italic
        val skew = if (italic) sz * 0.08f else 0f
        return when (n.kind) {
            "slashed" -> {
                // Feynman's slash: a stroke through the letter.
                val h = max(body.ascent, sz * 0.5f)
                draws.add(Draw.Stroke(listOf(body.width * 0.2f to body.descent.coerceAtLeast(sz * 0.08f), body.width * 0.85f + skew to -h - sz * 0.04f), t * 0.9f))
                Box(body.width, h + sz * 0.05f, max(body.descent, sz * 0.1f), draws)
            }
            "bar", "overline" -> {
                val y = -(body.ascent + sz * 0.1f)
                val single = n.kind == "bar"
                val x0 = if (single) body.width * 0.12f + skew else 0f
                val x1 = if (single) body.width * 0.92f + skew else body.width
                draws.add(Draw.Rule(x0, y - t, x1 - x0, t))
                Box(body.width, body.ascent + sz * 0.1f + t, body.descent, draws)
            }
            "vec" -> {
                val y = -(body.ascent + sz * 0.14f)
                val x0 = body.width * 0.1f + skew; val x1 = body.width * 0.95f + skew
                draws.add(Draw.Stroke(listOf(x0 to y, x1 to y), t))
                draws.add(Draw.Stroke(listOf(x1 - sz * 0.12f to y - sz * 0.08f, x1 to y, x1 - sz * 0.12f to y + sz * 0.08f), t))
                Box(body.width, body.ascent + sz * 0.24f, body.descent, draws)
            }
            "dot" -> {
                val y = -(body.ascent + sz * 0.14f)
                val c = body.width / 2 + skew
                draws.add(Draw.Rule(c - t, y - t, 2 * t, 2 * t))
                Box(body.width, body.ascent + sz * 0.18f, body.descent, draws)
            }
            else -> {
                // hat and tilde as the font's accents over the middle.
                val mark = if (n.kind.contains("tilde")) "~" else "^"
                val m = glyph(mark, MathFont.Roman, level)
                draws.addAll(m.shifted((body.width - m.width) / 2 + skew, -(body.ascent - sz * 0.45f)))
                Box(body.width, body.ascent + sz * 0.25f, body.descent, draws)
            }
        }
    }

    private fun bigOp(n: Node.BigOp, level: Int): Box {
        val sz = sizeOf(level)
        val big = level == 0
        val g = if (big) glyph(n.glyph, MathFont.Big, level) else glyph(n.glyph, MathFont.Roman, level)
        // Center the operator on the axis.
        val a = axis(level)
        val shift = (g.ascent - g.descent) / 2 - a
        val op = Box(g.width, g.ascent - shift, g.descent + shift, g.shifted(0f, shift))
        val sl = minOf(level + 1, 2)
        val sub = n.sub?.let { layout(it, sl) }
        val sup = n.sup?.let { layout(it, sl) }
        if (n.limits && big) {
            val w = max(op.width, max(sub?.width ?: 0f, sup?.width ?: 0f))
            val draws = ArrayList<Draw>()
            draws.addAll(op.shifted((w - op.width) / 2, 0f))
            var asc = op.ascent
            var desc = op.descent
            if (sup != null) { val y = -(op.ascent + sz * 0.12f + sup.descent); draws.addAll(sup.shifted((w - sup.width) / 2, y)); asc = -y + sup.ascent }
            if (sub != null) { val y = op.descent + sz * 0.12f + sub.ascent; draws.addAll(sub.shifted((w - sub.width) / 2, y)); desc = y + sub.descent }
            return Box(w + sz * 0.1f, asc, desc, draws)
        }
        // ∫ with scripts at its corners (the lower one tucked in under the slant).
        val draws = ArrayList(op.items)
        var w = op.width
        var asc = op.ascent
        var desc = op.descent
        if (sup != null) { val y = -(op.ascent - sup.ascent * 0.6f); draws.addAll(sup.shifted(op.width, y)); w = max(w, op.width + sup.width); asc = max(asc, -y + sup.ascent) }
        if (sub != null) { val x = op.width - sz * (if (big) 0.35f else 0.1f); val y = op.descent - sub.descent * 0.3f; draws.addAll(sub.shifted(x, y)); w = max(w, x + sub.width); desc = max(desc, y + sub.descent) }
        return Box(w + sz * 0.08f, asc, desc, draws)
    }

    /** Opens up brackets too wide for a line, so they can break inside (at normal size). */
    private fun flatten(items: List<Node>, maxWidth: Float): List<Node> {
        val out = ArrayList<Node>()
        for (n in items) {
            if (n is Node.Fenced && layout(n, 0).width > maxWidth) {
                if (n.left != ".") out.add(Node.Glyph(n.left, MathFont.Roman, Node.Kind.Open))
                out.addAll(flatten((n.body as? Node.Row)?.items ?: listOf(n.body), maxWidth))
                if (n.right != ".") out.add(Node.Glyph(n.right, MathFont.Roman, Node.Kind.Close))
            } else if (n is Node.Row && layout(n, 0).width > maxWidth) {
                out.addAll(flatten(n.items, maxWidth))
            } else out.add(n)
        }
        return out
    }

    /**
     * Lays out a whole formula in lines no wider than [maxWidth] where it can: breaks go before a
     * top-level +, − or = (after the first =, later lines are indented to line up under it) or at
     * a top-level thin space between factors.
     */
    fun lines(root: Node, maxWidth: Float, lineGap: Float = size * 0.35f): Box {
        val items = flatten((root as? Node.Row)?.items ?: listOf(root), maxWidth)
        val boxes = items.map { layout(it, 0) }
        val whole = join(boxes, items, 0)
        if (whole.width <= maxWidth || items.size < 2) return whole
        val ks = kinds(items)
        // Where a line may start: at a binary/relation operator, or after a thin space.
        val breakable = BooleanArray(items.size) { i ->
            i > 0 && ((ks[i] == Node.Kind.Bin || ks[i] == Node.Kind.Rel) || (items[i - 1] is Node.Space && (items[i - 1] as Node.Space).em > 0f))
        }
        // Indent continuation lines under the first relation when it's near the start.
        val firstRel = items.indices.firstOrNull { ks[it] == Node.Kind.Rel }
        var indent = if (firstRel != null) join(boxes.subList(0, firstRel), items.subList(0, firstRel), 0).width else 0f
        if (indent > maxWidth * 0.4f) indent = size * 1.5f
        val linesOut = ArrayList<Box>()
        var start = 0
        while (start < items.size) {
            val avail = if (linesOut.isEmpty()) maxWidth else maxWidth - indent
            var end = start + 1
            var lastBreak = -1
            while (end < items.size) {
                if (breakable[end]) {
                    val w = join(boxes.subList(start, end), items.subList(start, end), 0).width
                    if (w > avail && lastBreak > start) break
                    lastBreak = end
                    if (w > avail) break
                }
                end++
            }
            val cut = if (end >= items.size) {
                val w = join(boxes.subList(start, items.size), items.subList(start, items.size), 0).width
                if (w <= avail || lastBreak <= start) items.size else lastBreak
            } else if (lastBreak > start) lastBreak else end
            // Drop spaces at the start of a line.
            var s0 = start
            while (s0 < cut - 1 && items[s0] is Node.Space) s0++
            linesOut.add(join(boxes.subList(s0, cut), items.subList(s0, cut), 0))
            start = cut
        }
        var y = 0f
        var width = 0f
        val draws = ArrayList<Draw>()
        var asc0 = 0f
        for ((li, b) in linesOut.withIndex()) {
            val x = if (li == 0) 0f else indent
            if (li == 0) { asc0 = b.ascent; y = 0f } else y += b.ascent
            draws.addAll(b.shifted(x, y))
            width = max(width, x + b.width)
            y += b.descent + lineGap
        }
        return Box(width, asc0, y - lineGap, draws)
    }
}
