package com.example.feynman.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feynman.draw.TikZ

/** The whole app: projects on the left, the draw / solution / rules switcher, the ⋮ menu. */
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(runCatching { Mode.valueOf(prefs.getString("mode", Mode.Draw.name)!!) }.getOrDefault(Mode.Draw)) }
    val vm: FeynmanViewModel = viewModel()
    val colors = MaterialTheme.colorScheme
    val switchTo = { m: Mode -> mode = m; prefs.edit().putString("mode", m.name).apply() }
    var projects by remember { mutableStateOf(false) }

    // A Surface sets the content color, so every text, icon and formula without its own color
    // is onSurface (white-ish in dark mode) rather than black.
    Surface(Modifier.fillMaxSize(), color = colors.surface, contentColor = ink()) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { projects = true }) { Icon(Icons.Default.FolderOpen, contentDescription = "Saved diagrams", tint = inkVariant()) }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ModeSwitcher(mode, onSelect = switchTo) }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { MenuAction(vm) }
        }
        AnimatedContent(targetState = mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mode", modifier = Modifier.weight(1f)) { m ->
            when (m) {
                Mode.Draw -> EditorScreen(vm)
                Mode.Solution -> SolutionScreen(vm)
                Mode.Rules -> RulesScreen()
            }
        }
    }
    }
    if (projects) ProjectsPage(vm, onClose = { projects = false })
}

/** The ⋮ menu: copying the diagram as TikZ, settings, acknowledgements. */
@Composable
private fun MenuAction(vm: FeynmanViewModel) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var acknowledgements by remember { mutableStateOf(false) }
    if (settings) AppSettingsPage(vm, onBack = { settings = false }, onAcknowledgements = { settings = false; acknowledgements = true })
    if (acknowledgements) AcknowledgementsDialog(onDismiss = { acknowledgements = false })
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = inkVariant()) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Copy as TikZ-Feynman") }, onClick = { menu = false; clipboard.setText(AnnotatedString(TikZ.of(vm.diagram))) })
            DropdownMenuItem(text = { Text("Copy the amplitude (LaTeX)") }, onClick = {
                menu = false
                vm.solution?.amplitude?.let { clipboard.setText(AnnotatedString("i\\mathcal{M} = " + it.tex)) }
            })
            DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; settings = true })
            DropdownMenuItem(text = { Text("Acknowledgements") }, onClick = { menu = false; acknowledgements = true })
        }
    }
}
