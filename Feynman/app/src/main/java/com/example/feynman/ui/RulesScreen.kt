package com.example.feynman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feynman.draw.Geometry
import com.example.feynman.draw.Pt
import com.example.feynman.latex.MathLayout
import com.example.feynman.latex.MathParser
import com.example.feynman.physics.ConventionPresets
import com.example.feynman.physics.RuleCatalog
import com.example.feynman.physics.SM
import kotlin.math.cos
import kotlin.math.sin

/** The rules of the chosen theory, drawn and written out, by section. */
@Composable
fun RulesScreen() {
    val colors = MaterialTheme.colorScheme
    val c = AppSettings.conventions
    val theory = AppSettings.theory
    val entries = remember(theory) { RuleCatalog.forTheory(theory) }
    val usesPaper = entries.any { it.label == null }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.secondaryContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(theory.label, style = MaterialTheme.typography.titleSmall, color = colors.onSecondaryContainer)
                Text(theory.about + " Only this theory's rules are listed; pick another in Settings → Theory.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer)
                if (usesPaper) {
                    Text("Rules with a number are Romão & Silva's, Int. J. Mod. Phys. A 27 (2012) 1230025, in an Rξ gauge with the signs η left in. Metric (+, −, −, −); all momenta incoming, except in ghost vertices, where p is the outgoing ghost's.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer)
                    MathTex("\\eta = ${s(c.eta)},\\ \\eta' = ${s(c.etaPrime)},\\ \\eta_Z = ${s(c.etaZ)},\\ \\eta_\\theta = ${s(c.etaTheta)},\\ \\eta_Y = ${s(c.etaY)},\\ \\eta_e = ${s(c.etaE)},\\ \\eta_s = ${s(c.etaS)},\\ \\eta_G = ${s(c.etaG)}",
                        Modifier.fillMaxWidth(), fontSize = 16.sp, color = colors.onSecondaryContainer)
                    Text("Your signs: ${ConventionPresets.nameOf(c)} (change them in Settings). Calculations use them in the ${AppSettings.gauge.label} gauge.", style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer)
                }
                if (theory.supersymmetric) Text("Superpartners' gauge couplings follow the same covariant derivative and signs; gaugino and higgsino couplings are Martin's (hep-ph/9709356). Majorana lines have no arrow: a fermion line is read along a chosen fermion flow, and a vertex read the other way uses Γ′ = CΓᵀC⁻¹ (Denner et al., Nucl. Phys. B 387 (1992) 467).",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSecondaryContainer)
            }
        }
        for (section in RuleCatalog.sectionsOf(entries)) {
            item(key = "section:$section") { Text(section, style = MaterialTheme.typography.titleMedium, color = colors.primary, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
            items(entries.filter { it.section == section }, key = { "${section}:${it.key}" }) { e -> RuleCard(e) }
        }
        item { Box(Modifier.height(24.dp)) }
    }
}

private fun s(n: Int) = if (n > 0) "+1" else "-1"

@Composable
private fun RuleCard(e: RuleCatalog.Entry) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RuleDrawing(e, Modifier.width(if (e.isPropagator) 120.dp else 110.dp).height(if (e.isPropagator) 48.dp else 100.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.tag, style = MaterialTheme.typography.labelMedium, color = colors.primary,
                modifier = Modifier.clip(CircleShape).background(colors.primaryContainer).padding(horizontal = 10.dp, vertical = 3.dp))
            MathTex(e.tex, Modifier.fillMaxWidth(), fontSize = 16.sp)
        }
    }
}

/** The vertex or propagator as in the paper's figures, with its legs' labels. */
@Composable
fun RuleDrawing(e: RuleCatalog.Entry, modifier: Modifier) {
    val fonts = LocalMathFonts.current
    val dc = diagramColors()
    val density = LocalDensity.current.density
    Canvas(modifier) {
        val w = size.width / density
        val h = size.height / density
        val style = drawingStyle().copy(amplitude = 3.2f, wavelength = 9f, gluonAmplitude = 2.8f, gluonWavelength = 5.5f, arrow = 6f)
        fun screen(p: Pt) = Offset(p.x * density, p.y * density)
        val labels = MathLayout(fonts, 12f * density)
        if (e.isPropagator) {
            val leg = e.legs[0]
            val p = SM.byId(leg.particle)
            val a = Pt(14f, h * 0.45f); val b = Pt(w - 14f, h * 0.45f)
            drawLineShape(Geometry.shape(p, a, b, 0f, false, style, false), dc.line, ::screen, density, style)
            for ((pt, lab) in listOf(Pt(6f, h * 0.45f) to e.legs[0].label, Pt(w - 6f, h * 0.45f) to e.legs[1].label)) {
                if (lab.isEmpty()) continue
                val box = labels.layout(MathParser.parse(lab))
                val o = screen(pt)
                drawMath(fonts, box, o.x - box.width / 2, o.y + h * 0.4f * density, dc.label)
            }
            return@Canvas
        }
        val centre = Pt(w * 0.45f, h / 2)
        val r = minOf(w, h) * 0.38f
        val angles = when (e.legs.size) {
            3 -> listOf(145.0, 215.0, 0.0)
            else -> listOf(135.0, 225.0, 45.0, 315.0)
        }
        e.legs.forEachIndexed { i, leg ->
            val t = Math.toRadians(angles[i])
            val end = Pt(centre.x + (r * cos(t)).toFloat(), centre.y - (r * sin(t)).toFloat())
            val p = SM.byId(leg.particle)
            // Oriented lines point the way the particle flows.
            val (a, b) = if (p?.oriented == true && !leg.into) centre to end else end to centre
            drawLineShape(Geometry.shape(p, a, b, 0f, false, style, false), dc.line, ::screen, density, style)
            val box = labels.layout(MathParser.parse(leg.label))
            val lp = Pt(centre.x + ((r + 11f) * cos(t)).toFloat(), centre.y - ((r + 11f) * sin(t)).toFloat())
            val o = screen(lp)
            drawMath(fonts, box, o.x - box.width / 2, o.y + (box.ascent - box.descent) / 2, dc.label)
        }
        drawCircle(dc.vertex, 2.5f * density, screen(centre))
    }
}
