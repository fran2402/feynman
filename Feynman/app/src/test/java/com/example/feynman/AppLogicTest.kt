package com.example.feynman

import com.example.feynman.draw.TikZ
import com.example.feynman.latex.MathParser
import com.example.feynman.physics.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class AppLogicTest {
    @Test fun everyTemplateSolves() {
        for (t in Templates.all) {
            val s = Solver.solve(t.diagram, SolveOptions(theory = t.theory))
            assertTrue("${t.name}: ${s.issues.map { it.message }}", s.issues.isEmpty())
            assertTrue(t.name, s.squared != null || s.loop != null || s.multiLoop != null)
            // Every formula parses.
            for (step in s.steps) for (b in step.blocks) if (b is Block.Math) MathParser.parse(b.tex)
        }
    }

    @Test fun everyRuleOfThePaperIsListed() {
        val eqs = RuleCatalog.all.map { it.eq }.toSet()
        for (n in 45..119) if (n != 69) assertTrue("eq. $n", n in eqs)
    }

    @Test fun drawingAndErasing() {
        var d = Diagram()
        d = Editing.addLine(d, 0f, 0f, 100f, 0f, "e", 20f, true).first
        d = Editing.addLine(d, 100f, 0f, 200f, 0f, "e", 20f, true).first
        d = Editing.addLine(d, 100f, 0f, 100f, 100f, "A", 20f, true).first
        assertEquals(4, d.points.size)
        assertEquals(1, Topology.of(d).vertices.size)
        // A second line between the same points bends both apart.
        d = Editing.addLine(d, 100f, 0f, 200f, 0f, "e", 20f, true).first
        assertTrue(d.lines.filter { it.bend != 0f }.size == 2)
        d = Editing.deleteLine(d, d.lines.first().id)
        assertEquals(3, d.lines.size)
        assertEquals(d, Diagram.decode(d.encode()))
        assertTrue(TikZ.of(Templates.all.first().diagram).contains("\\diagram*"))
    }

    @Test fun vacuumPolarizationNumbers() {
        // The finite part's p²ε·ε* coefficient is 8e²∫x(1−x) ln(Δ/μ²) dx with Δ = m² − x(1−x)p².
        val c = Solver.context(SolveOptions())
        val amp = Amplitude.build(Templates.all.first { it.name == "Vacuum polarization" }.diagram, c)
        val r = Loop.evaluate(amp, Kinematics(amp.externals, c.massOf))!!
        val rows = Evaluate.loop(r, mapOf("e" to 1.0, "m_e" to 1.0, "psq" to 2.0), 1.0, { "" }, { "" })!!
        var expected = 0.0
        val n = 20000
        for (i in 0 until n) { val x = (i + 0.5) / n; expected += 8 * x * (1 - x) * ln(1 - x * (1 - x) * 2.0) / n }
        val row = rows.first { it.structureTex.contains("\\varepsilon_{1}\\cdot \\varepsilon^{*}_{2}") }
        assertTrue("${row.finite} vs ${expected * 2}", abs(row.finite.re - expected * 2) < 1e-6)
        assertTrue(abs(row.pole.re - (-4.0 / 3 * 2)) < 1e-9)
    }
}

class TidyTest {
    @Test fun tidyKeepsTheDiagramAndLinesUpTheEnds() {
        for (t in Templates.all) {
            // Scramble the template, then tidy it.
            val r = java.util.Random(7)
            // Pin each end in or out first: scrambling would otherwise change which way they go.
            val ext = Topology.of(t.diagram).externals.associate { it.point to it.incoming }
            val messy = t.diagram.copy(points = t.diagram.points.map { p ->
                p.copy(x = p.x + r.nextInt(200) - 100, y = p.y + r.nextInt(200) - 100, io = ext[p.id]?.let { if (it) Io.In else Io.Out } ?: p.io)
            })
            val before = Solver.solve(messy, SolveOptions())
            val tidy = Editing.tidy(messy)
            val after = Solver.solve(tidy, SolveOptions())
            assertEquals(t.name, messy.lines, tidy.lines.map { l -> l.copy(bend = messy.lines.first { it.id == l.id }.bend) })
            val topo = Topology.of(tidy)
            val xsIn = topo.externals.filter { it.incoming }.map { tidy.point(it.point).x }.toSet()
            val xsOut = topo.externals.filter { !it.incoming }.map { tidy.point(it.point).x }.toSet()
            assertEquals(t.name, 1, xsIn.size); assertEquals(t.name, 1, xsOut.size)
            assertTrue(t.name, xsIn.first() < xsOut.first())
            assertEquals(t.name, tidy.points.size, tidy.points.map { it.x to it.y }.toSet().size)
            // Same physics before and after (momenta may be routed the other way round, so compare
            // the final answer in invariants).
            before.squared?.let { assertEquals(t.name, Tex.of(it.result), Tex.of(after.squared!!.result)) }
            before.loop?.let { assertEquals(t.name, Tex.of(it.pole), Tex.of(after.loop!!.pole)) }
        }
    }
}

class SourcesTest {
    @Test fun isbnsHaveValidCheckDigits() {
        val isbns = ConventionPresets.all.flatMap { it.sources }.filter { it.id.startsWith("ISBN") }.map { it.id.filter { c -> c.isDigit() } }
        assertTrue(isbns.size >= 10)
        for (i in isbns) {
            assertEquals(i, 13, i.length)
            val sum = i.mapIndexed { k, c -> (c - '0') * if (k % 2 == 0) 1 else 3 }.sum()
            assertEquals("check digit of $i", 0, sum % 10)
        }
    }

    @Test fun everyParticleHasACard() {
        for (p in SM.all) {
            val card = Help.particle(p)
            assertTrue(p.id, card.theory.isNotEmpty())
            for ((_, tex) in card.facts + card.formulas) com.example.feynman.latex.MathParser.parse(tex)
        }
        // A line and a vertex of a real diagram.
        val d = Templates.all.first().diagram
        for (l in d.lines) assertTrue(Help.line(d, l.id, SolveOptions())!!.formulas.isNotEmpty())
        val v = Topology.of(d).vertices.first()
        assertEquals(67, RuleCatalog.byEq(67)!!.eq)
        assertTrue(Help.vertex(d, v, SolveOptions())!!.formulas.first().second.contains("\\gamma"))
    }
}
