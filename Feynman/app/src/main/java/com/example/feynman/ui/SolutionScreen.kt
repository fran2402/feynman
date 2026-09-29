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
                        Text("Add the other tabs", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Switch(vm.sumDiagrams, { vm.sumDiagrams = it; vm.resolve() })
                    }
                }
            }
        }
        if (s == null) {
            item { Text(if (vm.solving) "" else "Draw a diagram to see its amplitude.", color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        } else {
            items(s.steps) { step -> StepCard(step) }
            if ((s.loop != null || s.squared != null) && s.symbols.isNotEmpty()) item { NumbersCard(vm, s) }
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
                step.blocks.filterIsInstance<Block.Rule>().joinToString("\n") { "% (${it.eq})\n${it.tex}" }
            if (tex.isNotBlank()) IconButton(onClick = { clipboard.setText(AnnotatedString(tex)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy LaTeX", tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            if (long) IconButton(onClick = { open = !open }) { Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Fold" else "Show") }
        }
        if (!open) { Text("Long — tap to show.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant); return@Column }
        for (b in step.blocks) when (b) {
            is Block.Text -> RichText(b.text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            is Block.Math -> MathTex(b.tex, Modifier.fillMaxWidth(), fontSize = 18.sp, color = colors.onSurface)
            is Block.Rule -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MathTex(b.what, fontSize = 14.sp, color = colors.onSurfaceVariant, wrap = false)
                    Box(Modifier.weight(1f))
                    Text("(${b.eq})", style = MaterialTheme.typography.labelMedium, color = colors.primary,
                        modifier = Modifier.clip(CircleShape).background(colors.primaryContainer).padding(horizontal = 10.dp, vertical = 3.dp))
                }
                MathTex(b.tex, Modifier.fillMaxWidth(), fontSize = 17.sp, color = colors.onSurface)
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
        Text("Masses and energies in GeV (Standard Model values to start with).", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
                Text("iℳ = i/(16π²) × [pole/ε̄ + finite], for each structure:", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                for (r in rows) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh).padding(12.dp)) {
                        MathTex(r.structureTex, fontSize = 16.sp)
                        MathTex("\\frac{1}{\\bar\\epsilon}:\\ ${Numbers.texOfComplex(r.pole)}", fontSize = 16.sp)
                        MathTex("\\text{finite}:\\ ${Numbers.texOfComplex(r.finite)}", fontSize = 16.sp)
                        MathTex("\\frac{1}{16\\pi^{2}}\\times\\text{finite}:\\ ${Numbers.texOfComplex(r.finite * Evaluate.loopFactor)}", fontSize = 16.sp)
                    }
                }
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
