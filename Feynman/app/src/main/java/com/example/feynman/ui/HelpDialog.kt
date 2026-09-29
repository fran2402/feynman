package com.example.feynman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.feynman.physics.Help
import com.example.feynman.physics.HelpCard
import com.example.feynman.physics.Particle

/** A particle's card, from a long press on its key. */
@Composable
fun ParticleHelpDialog(p: Particle, onDismiss: () -> Unit) {
    val card = remember(p) { Help.particle(p) }
    HelpDialog(card, onDismiss)
}

/** A drawn line's card, from a long press on the canvas. */
@Composable
fun LineHelpDialog(vm: FeynmanViewModel, lineId: Int, onDismiss: () -> Unit) {
    val card = remember(vm.diagram, lineId, AppSettings.conventions, AppSettings.masslessFermions) { Help.line(vm.diagram, lineId, AppSettings.solveOptions) } ?: return
    HelpDialog(card, onDismiss)
}

/** A vertex's card: the rule it uses, as the paper writes it and with this diagram's indices. */
@Composable
fun VertexHelpDialog(vm: FeynmanViewModel, pointId: Int, onDismiss: () -> Unit) {
    val card = remember(vm.diagram, pointId, AppSettings.conventions, AppSettings.masslessFermions) { Help.vertex(vm.diagram, pointId, AppSettings.solveOptions) } ?: return
    HelpDialog(card, onDismiss)
}

/**
 * The card, as on CAS Calculator's keys: over the darkened screen until Dismiss (or Back), with
 * the symbol, the facts, the formulas, a line of theory, how to use it, and the paper's vertices.
 */
@Composable
fun HelpDialog(card: HelpCard, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var showVertices by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MathTex(card.symbolTex, fontSize = 26.sp, color = colors.primary, wrap = false)
                Text(card.title)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHighest).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for ((label, tex) in card.facts) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = inkVariant(), modifier = Modifier.width(104.dp))
                        Box(Modifier.horizontalScroll(rememberScrollState())) { MathTex(tex, fontSize = 17.sp, color = ink(), wrap = false) }
                    }
                }
                for ((caption, tex) in card.formulas) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RichText(caption, style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHighest)
                            .horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { MathTex(tex, fontSize = 20.sp, color = ink(), wrap = false) }
                }
                if (card.theory.isNotEmpty()) RichText(card.theory, style = MaterialTheme.typography.bodyLarge, color = ink())
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("How to use", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Text(card.usage, style = MaterialTheme.typography.bodyMedium, color = inkVariant())
                }
                if (card.vertices.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vertices (${card.vertices.size})", style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showVertices = !showVertices }) { Text(if (showVertices) "Hide" else "Show") }
                    }
                    if (!showVertices) Text(card.vertices.joinToString("  ") { "(${it.eq})" }, style = MaterialTheme.typography.bodyMedium, color = inkVariant())
                    else for (e in card.vertices) Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHighest).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RuleDrawing(e, Modifier.width(84.dp).height(76.dp))
                        Column(Modifier.weight(1f)) {
                            Text("(${e.eq})", style = MaterialTheme.typography.labelMedium, color = colors.primary,
                                modifier = Modifier.clip(CircleShape).background(colors.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp))
                            Box(Modifier.horizontalScroll(rememberScrollState())) { MathTex(e.tex, fontSize = 15.sp, color = ink(), wrap = false) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
    )
}
