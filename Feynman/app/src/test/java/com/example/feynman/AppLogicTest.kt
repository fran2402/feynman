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
            val s = Solver.solve(t.diagram, SolveOptions())
            assertTrue("${t.name}: ${s.issues.map { it.message }}", s.issues.isEmpty())
            assertTrue(t.name, s.squared != null || s.loop != null)
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
