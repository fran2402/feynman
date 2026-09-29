package com.example.feynman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feynman.physics.Amplitude
import com.example.feynman.physics.Block
import com.example.feynman.physics.Evaluate
import com.example.feynman.physics.Numbers
import com.example.feynman.physics.Kinematics
import com.example.feynman.physics.Observables
import com.example.feynman.physics.Solver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.feynman.physics.Solution
import com.example.feynman.physics.Step

/** The worked solution of the current diagram, one card per step. */
@Composable
fun SolutionScreen(vm: FeynmanViewModel) {
    val colors = MaterialTheme.colorScheme
    val s = vm.solution
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainerLowest).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiagramThumbnail(vm.diagram, Modifier.width(150.dp).height(96.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(vm.diagram.name.ifEmpty { "Diagram" }, style = MaterialTheme.typography.titleMedium)
                    if (vm.solving) Row(verticalAlignment = Alignment.CenterVertically) { Busy(Modifier.size(24.dp)); Text("  Working it out…", style = MaterialTheme.typography.bodySmall) }
                    if (vm.diagrams.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Add the other tabs", style = MaterialTheme.typography.bodySmall, color = inkVariant(), modifier = Modifier.weight(1f))
                        Switch(vm.sumDiagrams, { vm.sumDiagrams = it; vm.resolve() })
                    }
                }
            }
        }
        if (s == null) {
            item { Text(if (vm.solving) "" else "Draw a diagram to see its amplitude.", color = inkVariant(), modifier = Modifier.padding(16.dp)) }
        } else {
            items(s.steps) { step -> StepCard(step) }
            if ((s.loop != null || s.squared != null) && s.symbols.isNotEmpty()) item { NumbersCard(vm, s) }
            if (s.squared != null && s.amplitude != null) item { ObservablesCard(vm, s) }
        }
        item { Box(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StepCard(step: Step) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    // Very long formulas start folded.
    val long = step.blocks.any { it is Block.Math && it.tex.length > 1500 }
    var open by remember(step) { mutableStateOf(!long) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(step.title, style = MaterialTheme.typography.titleMedium, color = colors.primary, modifier = Modifier.weight(1f))
            val tex = step.blocks.filterIsInstance<Block.Math>().joinToString("\n") { it.tex } +
                step.blocks.filterIsInstance<Block.Rule>().joinToString("\n") { "% ${it.tag}\n${it.tex}" }
            if (tex.isNotBlank()) IconButton(onClick = { clipboard.setText(AnnotatedString(tex)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy LaTeX", tint = inkVariant(), modifier = Modifier.size(20.dp))
            }
            if (long) IconButton(onClick = { open = !open }) { Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Fold" else "Show") }
        }
        if (!open) { Text("Long — tap to show.", style = MaterialTheme.typography.bodySmall, color = inkVariant()); return@Column }
        for (b in step.blocks) when (b) {
            is Block.Text -> RichText(b.text, style = MaterialTheme.typography.bodyMedium, color = inkVariant())
            is Block.Math -> MathTex(b.tex, Modifier.fillMaxWidth(), fontSize = 18.sp, color = ink())
            is Block.Rule -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MathTex(b.what, fontSize = 14.sp, color = inkVariant(), wrap = false)
                    Box(Modifier.weight(1f))
                    Text(b.tag, style = MaterialTheme.typography.labelMedium, color = colors.primary,
                        modifier = Modifier.clip(CircleShape).background(colors.primaryContainer).padding(horizontal = 10.dp, vertical = 3.dp))
                }
                MathTex(b.tex, Modifier.fillMaxWidth(), fontSize = 17.sp, color = ink())
            }
            is Block.Note -> RichText(b.text, Modifier.clip(RoundedCornerShape(12.dp)).background(colors.tertiaryContainer).padding(10.dp),
                style = MaterialTheme.typography.bodySmall, color = colors.onTertiaryContainer)
        }
    }
}

/** Values for every symbol in the answer, and the numbers they give. */
@Composable
private fun NumbersCard(vm: FeynmanViewModel, s: Solution) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Numbers", style = MaterialTheme.typography.titleMedium, color = colors.primary)
        Text("Masses and energies in GeV (Standard Model values to start with).", style = MaterialTheme.typography.bodySmall, color = inkVariant())
        val fields: List<Pair<String, String>> = s.symbols.map { it.name.removeSuffix("*") to it.tex.removeSuffix("^{*}") }.distinct() +
            (if (s.loop != null) listOf("mu" to "\\mu") else emptyList())
        for ((name, tex) in fields) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(64.dp)) { MathTex(tex, fontSize = 17.sp, wrap = false) }
                OutlinedTextField(
                    value = if (name == "mu") vm.mu else vm.values[name] ?: "",
                    onValueChange = { v -> if (name == "mu") vm.mu = v else vm.values[name] = v },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val values = vm.numericValues()
        val loop = s.loop
        if (loop != null) {
            val mu = vm.mu.toDoubleOrNull() ?: 91.188
            val rows = remember(loop, values, mu) {
                runCatching {
                    Evaluate.loop(loop, values, mu, { i -> Amplitude.spinorTex(loop.chains[i].left) + "\\," }, { i -> "\\," + Amplitude.spinorTex(loop.chains[i].right) })
                }.getOrNull()
            }
            if (rows == null) Text("Give every symbol a number.", color = colors.error)
            else {
                Text("iℳ = i/(16π²) × [pole/ε̄ + finite], for each structure:", style = MaterialTheme.typography.bodySmall, color = inkVariant())
                for (r in rows) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh).padding(12.dp)) {
                        MathTex(r.structureTex, fontSize = 16.sp)
                        MathTex("\\frac{1}{\\bar\\epsilon}:\\ ${Numbers.texOfComplex(r.pole)}", fontSize = 16.sp)
                        MathTex("\\text{finite}:\\ ${Numbers.texOfComplex(r.finite)}", fontSize = 16.sp)
                        MathTex("\\frac{1}{16\\pi^{2}}\\times\\text{finite}:\\ ${Numbers.texOfComplex(r.finite * Evaluate.loopFactor)}", fontSize = 16.sp)
                    }
                }
            }
            val integrals = remember(loop, values, mu) { runCatching { Evaluate.scalarIntegrals(loop, values, mu) }.getOrNull().orEmpty() }
            if (integrals.isNotEmpty()) {
                Text("Scalar integrals (finite parts, 1/ε̄ left out):", style = MaterialTheme.typography.bodySmall, color = inkVariant())
                for ((name, v) in integrals) MathTex("$name = ${Numbers.texOfComplex(v)}", Modifier.fillMaxWidth(), fontSize = 16.sp)
            }
        }
        val sq = s.squared
        if (sq != null) {
            val v = remember(sq, values) { Evaluate.scalar(sq.result, values) }
            if (v == null) Text("Give every symbol a number.", color = colors.error)
            else MathTex("\\overline{|\\mathcal{M}|^{2}} = ${Numbers.texOfComplex(v)}", fontSize = 18.sp)
        }
    }
}

/** Cross section or decay width: numbers and curves from |ℳ|², off the main thread. */
@Composable
private fun ObservablesCard(vm: FeynmanViewModel, s: Solution) {
    val colors = MaterialTheme.colorScheme
    val sq = s.squared ?: return
    val amp = s.amplitude ?: return
    val kin = remember(amp) { Kinematics(amp.externals, Solver.context(AppSettings.solveOptions).massOf) }
    val kind = remember(kin) { Observables.kind(kin) }
    if (kind == Observables.Kind.None) return
    var sqrtS by remember { mutableStateOf("200") }
    var cut by remember { mutableStateOf("0.99") }
    val values = vm.numericValues()
    val bw = AppSettings.widths
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (kind == Observables.Kind.Scattering) "Cross section" else "Decay width", style = MaterialTheme.typography.titleMedium, color = colors.primary)
        when (kind) {
            Observables.Kind.Scattering -> {
                MathTex("\\frac{d\\sigma}{d\\cos\\theta} = \\frac{\\overline{|\\mathcal{M}|^{2}}}{32\\pi s}\\frac{|\\vec p_f|}{|\\vec p_i|}", Modifier.fillMaxWidth(), fontSize = 17.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sqrtS, { sqrtS = it }, label = { Text("√s (GeV)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(cut, { cut = it }, label = { Text("|cos θ| ≤") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                val e = sqrtS.toDoubleOrNull() ?: 0.0
                val c = (cut.toDoubleOrNull() ?: 0.99).coerceIn(0.01, 1.0)
                val result by produceState<Triple<Double?, Observables.Curve?, Observables.Curve?>?>(null, sq, values, e, c, bw) {
                    value = withContext(Dispatchers.Default) {
                        runCatching {
                            Triple(
                                Observables.sigma(sq, kin, values, e, c, bw),
                                Observables.angularCurve(sq, kin, values, e, bw),
                                Observables.energyCurve(sq, kin, values, maxOf(e * 2, 10.0), c, bw),
                            )
                        }.getOrNull()
                    }
                }
                val r = result
                if (r == null) Row(verticalAlignment = Alignment.CenterVertically) { Busy(Modifier.size(24.dp)); Text("  Integrating…", color = inkVariant()) }
                else {
                    r.first?.let { MathTex("\\sigma(\\sqrt{s} = ${Numbers.texOf(e)}\\ \\text{GeV}) = ${Numbers.texOf(it)}\\ \\text{pb}", Modifier.fillMaxWidth(), fontSize = 18.sp) }
                        ?: Text("Give every mass and coupling a number.", color = colors.error)
                    if (c < 1.0) Text("Integrated over |cos θ| ≤ ${Numbers.format(c)} (photon exchange in the t-channel diverges in the forward direction).", style = MaterialTheme.typography.bodySmall, color = inkVariant())
                    if (Observables.symmetry(kin) != 1.0) Text("Includes 1/n! for identical particles in the final state.", style = MaterialTheme.typography.bodySmall, color = inkVariant())
                    r.second?.let { LineChart(it.xs, it.ys, it.xLabel, it.yLabel, Modifier.fillMaxWidth()) }
                    r.third?.let { LineChart(it.xs, it.ys, it.xLabel, it.yLabel, Modifier.fillMaxWidth(), logY = it.ys.all { y -> y > 0 }) }
                }
            }
            Observables.Kind.Decay2, Observables.Kind.Decay3 -> {
                MathTex(if (kind == Observables.Kind.Decay2) "\\Gamma = \\frac{|\\vec p|}{8\\pi M^{2}}\\,\\overline{|\\mathcal{M}|^{2}}" else "\\Gamma = \\frac{1}{(2\\pi)^{3}\\,32 M^{3}}\\int dm_{12}^{2}\\,dm_{23}^{2}\\,\\overline{|\\mathcal{M}|^{2}}", Modifier.fillMaxWidth(), fontSize = 17.sp)
                val width by produceState<Double?>(null, sq, values, bw) { value = withContext(Dispatchers.Default) { runCatching { Observables.width(sq, kin, values, bw) }.getOrNull() } }
                val w = width
                if (w == null) Text("Give every mass and coupling a number.", color = inkVariant())
                else {
                    MathTex("\\Gamma = ${Numbers.texOf(w)}\\ \\text{GeV}", Modifier.fillMaxWidth(), fontSize = 18.sp)
                    // ħ = 6.582 × 10⁻²⁵ GeV s
                    if (w > 0) MathTex("\\tau = \\hbar/\\Gamma = ${Numbers.texOf(6.582119569e-25 / w)}\\ \\text{s}", Modifier.fillMaxWidth(), fontSize = 16.sp)
                }
            }
            else -> {}
        }
        if (bw) Text("Unstable particles in propagators have their widths (Breit–Wigner); switch off in Settings.", style = MaterialTheme.typography.bodySmall, color = inkVariant())
    }
}
