package com.example.feynman.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feynman.draw.Geometry
import com.example.feynman.draw.Pt
import com.example.feynman.physics.Editing
import com.example.feynman.physics.Io
import com.example.feynman.physics.Particle
import com.example.feynman.physics.SM
import com.example.feynman.physics.Templates
import com.example.feynman.physics.Theory
import com.example.feynman.physics.Topology

/** The drawing mode: tabs of diagrams, the canvas with its tools, and the particle palette. */
@Composable
fun EditorScreen(vm: FeynmanViewModel) {
    val colors = MaterialTheme.colorScheme
    var templates by remember { mutableStateOf(false) }
    val topo = remember(vm.diagram) { Topology.of(vm.diagram) }
    val issues = vm.solution?.issues.orEmpty() + topo.issues
    val bad = issues.mapNotNull { it.point }.toSet()

    Column(Modifier.fillMaxSize()) {
        DiagramTabs(vm)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceContainerLowest),
        ) {
            DiagramEditor(vm, Modifier.fillMaxSize(), bad)
            // What the diagram is, or what's wrong with it.
            StatusChip(vm, topo, issues.map { it.message }.distinct(), Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            if (vm.diagram.lines.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Drag between two points to draw a line", style = MaterialTheme.typography.titleMedium, color = inkVariant())
                    Text("Pick the particle below first. Ends with one line are external particles; points where three or four lines meet are vertices.", style = MaterialTheme.typography.bodyMedium, color = inkVariant())
                    TextButton(onClick = { templates = true }) { Text("Start from a textbook diagram") }
                }
            }
            ExpressiveToolbar(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                ToolButton(Icons.Default.Timeline, "Draw lines", vm.tool == Tool.Draw) { vm.tool = Tool.Draw }
                ToolButton(Icons.Default.OpenWith, "Move points (or the view)", vm.tool == Tool.Move) { vm.tool = Tool.Move }
                ToolButton(Icons.Default.Gesture, "Bend lines", vm.tool == Tool.Bend) { vm.tool = Tool.Bend }
                ToolButton(Icons.Default.Delete, "Erase", vm.tool == Tool.Erase) { vm.tool = Tool.Erase }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo") }
                IconButton(onClick = vm::redo, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo") }
                IconButton(onClick = { templates = true }) { Icon(Icons.Default.AutoAwesomeMosaic, contentDescription = "Textbook diagrams") }
            }
        }
        val selected = vm.selectedLine?.let { id -> vm.diagram.lines.firstOrNull { it.id == id } }
        AnimatedVisibility(selected != null) { if (selected != null) LineSheet(vm, selected.id) }
        if (selected == null) Palette(vm)
    }
    if (templates) TemplatesPage(vm, onClose = { templates = false })
    vm.helpLine?.let { id -> LineHelpDialog(vm, id, onDismiss = { vm.helpLine = null }) }
    vm.helpPoint?.let { id -> VertexHelpDialog(vm, id, onDismiss = { vm.helpPoint = null }) }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (on) colors.primary else Color.Transparent, label = "tool")
    val fg by animateColorAsState(if (on) colors.onPrimary else ink(), label = "toolFg")
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = fg) }
}

@Composable
private fun DiagramTabs(vm: FeynmanViewModel) {
    val colors = MaterialTheme.colorScheme
    var renaming by remember { mutableStateOf<Int?>(null) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        vm.diagrams.forEachIndexed { i, d ->
            val on = i == vm.current
            Row(
                Modifier.clip(CircleShape).background(if (on) colors.secondaryContainer else colors.surfaceContainerHigh)
                    .clickable { if (on) renaming = i else vm.selectTab(i) }.padding(start = 14.dp, end = if (vm.diagrams.size > 1) 4.dp else 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(d.name.ifEmpty { "Diagram ${i + 1}" }, style = MaterialTheme.typography.labelLarge, color = if (on) colors.onSecondaryContainer else inkVariant())
                if (vm.diagrams.size > 1) Box(Modifier.size(28.dp).clip(CircleShape).clickable { vm.closeTab(i) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, contentDescription = "Close ${d.name}", modifier = Modifier.size(16.dp), tint = inkVariant())
                }
            }
        }
        Box(Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceContainerHigh).clickable { vm.newTab() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = "New diagram", modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        // Tidy: lines up the ends, spaces the vertices, straightens lines; nothing is removed.
        IconButton(onClick = vm::tidy, enabled = vm.diagram.lines.isNotEmpty()) { Icon(Icons.Default.AutoFixHigh, contentDescription = "Tidy the diagram", tint = inkVariant()) }
        IconButton(onClick = vm::recenter, enabled = vm.diagram.lines.isNotEmpty()) { Icon(Icons.Default.CenterFocusStrong, contentDescription = "Bring the diagram into view", tint = inkVariant()) }
        IconButton(onClick = vm::clear, enabled = vm.diagram.lines.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, contentDescription = "Clear this diagram", tint = inkVariant()) }
    }
    renaming?.let { i -> NameDialog("Rename diagram", vm.diagrams[i].name, onDone = { vm.rename(i, it); renaming = null }, onDismiss = { renaming = null }) }
}

@Composable
private fun StatusChip(vm: FeynmanViewModel, topo: Topology, issues: List<String>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    if (vm.diagram.lines.isEmpty()) return
    val bad = issues.isNotEmpty()
    Row(
        modifier.clip(CircleShape).background(if (bad) colors.errorContainer else colors.secondaryContainer).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (bad) Icon(Icons.Default.Warning, contentDescription = null, tint = colors.onErrorContainer, modifier = Modifier.size(16.dp))
        val text = if (bad) issues.first() else buildString {
            val ins = topo.externals.filter { it.incoming }.joinToString(" ") { it.tex }
            val outs = topo.externals.filter { !it.incoming }.joinToString(" ") { it.tex }
            append("\\($ins \\to $outs\\) · ")
            append(when (topo.loops) { 0 -> "tree"; 1 -> "one loop"; else -> "${topo.loops} loops" })
        }
        RichText(text, style = MaterialTheme.typography.labelLarge, color = if (bad) colors.onErrorContainer else colors.onSecondaryContainer)
    }
}

/** Places in each palette group: the largest group (six) fills two rows of three. */
private val PALETTE_SLOTS = Theory.entries.maxOf { t -> t.groups.maxOf { g -> t.particles.count { it.group == g } } }.let { (it + 2) / 3 * 3 }

/** The particles, in groups, like the calculator's keypad. */
@Composable
private fun Palette(vm: FeynmanViewModel) {
    val colors = MaterialTheme.colorScheme
    val theory = AppSettings.theory
    val groups = theory.groups
    var chosen by remember { mutableStateOf(SM.byId(vm.particle)?.group ?: groups.first()) }
    val group = if (chosen in groups) chosen else groups.first()
    var help by remember { mutableStateOf<Particle?>(null) }
    help?.let { p -> ParticleHelpDialog(p, onDismiss = { help = null }) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Connected button group.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            groups.forEachIndexed { i, g ->
                val on = g == group
                val outer = 20.dp; val inner = 6.dp
                val shape = when {
                    on -> RoundedCornerShape(outer)
                    i == 0 -> RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
                    i == groups.lastIndex -> RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
                    else -> RoundedCornerShape(inner)
                }
                Box(
                    Modifier.weight(1f).height(40.dp).clip(shape).background(if (on) colors.primary else colors.surfaceContainerHigh).clickable { chosen = g },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(g.substringBefore(' ').let { if (it == "Gauge") "Bosons" else it }, style = MaterialTheme.typography.labelMedium, color = if (on) colors.onPrimary else ink(), maxLines = 1)
                }
            }
        }
        val list = theory.particles.filter { it.group == group }
        // Every group is two rows of three, padded with empty places (as the ghosts are), so the
        // keypad keeps its size whichever group is open.
        val slots: List<Particle?> = list + List(maxOf(0, PALETTE_SLOTS - list.size)) { null }
        LazyVerticalGrid(
            GridCells.Fixed(3),
            Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            userScrollEnabled = false,
        ) {
            items(slots.size) { i ->
                val p = slots[i]
                if (p == null) Spacer(Modifier.height(64.dp))
                else ParticleKey(p, p.id == vm.particle, onHelp = { help = p }) { vm.choose(p.id); if (vm.tool != Tool.Draw) vm.tool = Tool.Draw }
            }
        }
    }
}

/** A key: the particle's symbol above a sample of its line. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ParticleKey(p: Particle, on: Boolean, onHelp: () -> Unit, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val bg by animateColorAsState(if (on) colors.primaryContainer else colors.surfaceContainerHighest, label = "key")
    val fg = if (on && !isDark()) colors.onPrimaryContainer else ink()
    val corner = if (on) 50 else 30
    Column(
        Modifier.height(64.dp).clip(RoundedCornerShape(percent = corner)).background(bg)
            // Long-press for the particle's card, as on CAS Calculator's keys.
            .combinedClickable(onClick = onClick, onLongClick = {
                if (AppSettings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onHelp()
            }, onLongClickLabel = "Explain ${p.name}")
            .semantics { contentDescription = p.name }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MathTex(p.tex, fontSize = 17.sp, color = fg, wrap = false)
        LineSample(p, fg, Modifier.width(56.dp).height(14.dp))
    }
}

@Composable
fun LineSample(p: Particle, color: Color, modifier: Modifier) {
    val density = LocalDensity.current.density
    Canvas(modifier) {
        val w = size.width / density
        val h = size.height / density
        val style = drawingStyle().copy(amplitude = 3f, wavelength = 8f, gluonAmplitude = 2.6f, gluonWavelength = 5f, arrow = 6f, dotSpacing = 3.6f)
        val sh = Geometry.shape(p, Pt(2f, h / 2), Pt(w - 2f, h / 2), 0f, false, style, false)
        drawLineShape(sh, color, { pt -> Offset(pt.x * density, pt.y * density) }, density, style.copy(stroke = 1.4f))
    }
}

/** Options for the selected line: its particle, its direction, in or out, bend, delete. */
@Composable
private fun LineSheet(vm: FeynmanViewModel, lineId: Int) {
    val colors = MaterialTheme.colorScheme
    val line = vm.diagram.lines.firstOrNull { it.id == lineId } ?: return
    val p = SM.byId(line.particle)
    val topo = remember(vm.diagram) { Topology.of(vm.diagram) }
    val ext = topo.external(line.id)
    Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MathTex(ext?.tex ?: p?.tex ?: line.particle, fontSize = 22.sp, color = colors.primary, wrap = false)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p?.name ?: line.particle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            ext != null -> "External, ${if (ext.incoming) "incoming" else "outgoing"}${if (ext.anti) " antiparticle" else ""}"
                            line.isSelfLoop -> "Loop at one vertex"
                            else -> "Internal line (propagator)"
                        },
                        style = MaterialTheme.typography.bodySmall, color = inkVariant(),
                    )
                }
                IconButton(onClick = { vm.selectedLine = null }) { Icon(Icons.Default.Close, contentDescription = "Done") }
            }
            // Change the particle, within its group first.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (q in AppSettings.theory.particles.sortedBy { if (it.group == p?.group) 0 else 1 }) {
                    FilterChip(selected = q.id == line.particle, onClick = { vm.edit { Editing.setParticle(it, line.id, q.id) } }, label = { MathTex(q.tex, fontSize = 15.sp, wrap = false) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (p?.oriented == true) TextButton(onClick = { vm.edit { Editing.flip(it, line.id) } }) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null); Spacer(Modifier.width(4.dp))
                    Text(if (p.isFermion) "Reverse arrow" else "Swap charge")
                }
                if (line.bend != 0f && !line.isSelfLoop) TextButton(onClick = { vm.edit { Editing.setBend(it, line.id, 0f) } }) {
                    Icon(Icons.Default.Straighten, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Straighten")
                }
                TextButton(onClick = { vm.edit { Editing.toggleMomentum(it, line.id) } }) { Text(if (line.showMomentum) "Hide momentum" else "Show momentum") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.edit { Editing.deleteLine(it, line.id) }; vm.selectedLine = null }) { Icon(Icons.Default.Delete, contentDescription = "Delete line", tint = colors.error) }
            }
            if (ext != null) {
                val point = vm.diagram.point(ext.point)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Io.entries.forEachIndexed { i, io ->
                        SegmentedButton(
                            selected = point.io == io,
                            onClick = { vm.edit { Editing.setIo(it, point.id, io) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Io.entries.size),
                        ) { Text(when (io) { Io.Auto -> "Auto"; Io.In -> "Incoming"; Io.Out -> "Outgoing" }) }
                    }
                }
            }
        }
    }
}

/** Textbook diagrams to start from. */
@Composable
private fun TemplatesPage(vm: FeynmanViewModel, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FullScreenPage("Textbook diagrams", onBack = onClose) {
        Text("Opens in a new tab (or this one, if it's empty). Every diagram can be edited.", style = MaterialTheme.typography.bodyMedium, color = inkVariant())
        for (group in Templates.all.map { it.group }.distinct()) {
            Text(group, style = MaterialTheme.typography.titleMedium, color = colors.primary, modifier = Modifier.padding(top = 8.dp))
            for (t in Templates.all.filter { it.group == group }) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh)
                        .clickable { vm.open(t.diagram); onClose() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DiagramThumbnail(t.diagram, Modifier.width(120.dp).height(76.dp), labels = false)
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall)
                        Text(t.about, style = MaterialTheme.typography.bodySmall, color = inkVariant())
                    }
                }
            }
        }
    }
}
