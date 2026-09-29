package com.example.feynman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feynman.physics.Generate

/**
 * Every diagram of a process: pick the incoming and outgoing particles (tap one to swap it for
 * its antiparticle), tree level or one loop, and the diagrams open as tabs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratePage(vm: FeynmanViewModel, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val incoming = remember { mutableStateListOf<Generate.Leg>() }
    val outgoing = remember { mutableStateListOf<Generate.Leg>() }
    var loops by remember { mutableStateOf(0) }
    var goldstones by remember { mutableStateOf(true) }
    var amputated by remember { mutableStateOf(true) }
    var adding by remember { mutableStateOf(true) } // true: add to incoming
    var result by remember { mutableStateOf<String?>(null) }
    val theory = AppSettings.theory

    FullScreenPage("Generate diagrams", onBack = onClose) {
        RichText("Every diagram of a process in ${theory.label} (change the theory in Settings). Tap a particle below to add it; tap an added one to swap particle and antiparticle.",
            style = MaterialTheme.typography.bodyMedium, color = inkVariant())
        // The process as it stands.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHigh).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val tex = (incoming.joinToString("\\, ") { it.tex }.ifEmpty { "?" }) + " \\to " + (outgoing.joinToString("\\, ") { it.tex }.ifEmpty { "?" })
            MathTex(tex, Modifier.fillMaxWidth(), fontSize = 24.sp)
            for ((title, list, isIn) in listOf(Triple("Incoming", incoming, true), Triple("Outgoing", outgoing, false))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.width(84.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        list.forEachIndexed { i, leg ->
                            Row(
                                Modifier.clip(CircleShape).background(colors.secondaryContainer)
                                    .clickable { if (leg.p.oriented) list[i] = leg.copy(anti = !leg.anti) }
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MathTex(leg.tex, fontSize = 17.sp, color = colors.onSecondaryContainer, wrap = false)
                                if (leg.p.oriented) Icon(Icons.Default.SwapHoriz, contentDescription = "Antiparticle", tint = colors.onSecondaryContainer, modifier = Modifier.size(16.dp))
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = colors.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { list.removeAt(i) }.padding(6.dp))
                            }
                        }
                    }
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Add incoming", "Add outgoing").forEachIndexed { k, name ->
                SegmentedButton(selected = adding == (k == 0), onClick = { adding = k == 0 }, shape = SegmentedButtonDefaults.itemShape(k, 2), icon = {}, label = { Text(name) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            theory.particles.filter { !it.isGhost }.forEach { p ->
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHighest)
                        .clickable { (if (adding) incoming else outgoing).add(Generate.Leg(p, adding, false)) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) { MathTex(p.tex, fontSize = 18.sp, wrap = false) }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Tree level", "One loop").forEachIndexed { k, name ->
                SegmentedButton(selected = loops == k, onClick = { loops = k }, shape = SegmentedButtonDefaults.itemShape(k, 2), icon = {}, label = { Text(name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Goldstone bosons", style = MaterialTheme.typography.bodyLarge)
                Text("Needed in the Feynman and general gauges; they couple in proportion to masses", style = MaterialTheme.typography.bodySmall, color = inkVariant())
            }
            Switch(goldstones, { goldstones = it })
        }
        if (loops == 1) Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Leave out leg corrections and tadpoles", style = MaterialTheme.typography.bodyLarge)
                Text("Loops on external lines, and loops hanging off one line", style = MaterialTheme.typography.bodySmall, color = inkVariant())
            }
            Switch(amputated, { amputated = it })
        }
        Button(
            onClick = {
                result = null
                vm.generate(incoming.toList() + outgoing.toList(), Generate.Options(loops = loops, goldstones = goldstones, onlyAmputated = amputated)) { n ->
                    result = if (n == 0) vm.generateNote ?: "No diagrams." else "$n diagram${if (n == 1) "" else "s"} opened as tabs." + (vm.generateNote?.let { " $it" } ?: "")
                    if (n > 0) onClose()
                }
            },
            enabled = !vm.generating && incoming.isNotEmpty() && incoming.size + outgoing.size >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (vm.generating) "Working…" else "Generate") }
        if (vm.generating) Row(verticalAlignment = Alignment.CenterVertically) { Busy(Modifier.size(28.dp)); Spacer(Modifier.width(8.dp)); Text("One-loop processes can take a few seconds.", color = inkVariant()) }
        result?.let { Text(it, color = inkVariant()) }
        if (loops == 1) Text("One-loop generation joins two extra legs of every tree into a loop and removes the copies; it's practical for two- and three-point functions and small processes.",
            style = MaterialTheme.typography.bodySmall, color = inkVariant())
    }
}
