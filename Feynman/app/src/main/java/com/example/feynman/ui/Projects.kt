package com.example.feynman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.feynman.physics.Diagram
import java.text.DateFormat
import java.util.Date

/** Saved diagrams: save the current one under a name, open one in a new tab, delete. */
@Composable
fun ProjectsPage(vm: FeynmanViewModel, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FeynmanViewModel.Project?>(null) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    FullScreenPage("Saved diagrams", onBack = onClose) {
        Button(onClick = { saving = true }, modifier = Modifier.fillMaxWidth(), enabled = vm.diagram.lines.isNotEmpty()) { Text("Save the current diagram") }
        if (vm.projects.isEmpty()) Text("Nothing saved yet.", style = MaterialTheme.typography.bodyMedium, color = inkVariant())
        vm.projects.toList().forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh)
                    .clickable(onClickLabel = "Open ${p.name}") { vm.openProject(p); onClose() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Diagram.decode(p.data)?.let { DiagramThumbnail(it, Modifier.width(110.dp).height(70.dp), labels = false) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text("Saved ${dateFormat.format(Date(p.savedAt))}", style = MaterialTheme.typography.bodySmall, color = inkVariant())
                }
                IconButton(onClick = { deleting = p }) { Icon(Icons.Default.Delete, contentDescription = "Delete ${p.name}", tint = inkVariant()) }
            }
        }
    }
    if (saving) NameDialog("Save diagram", vm.diagram.name.ifEmpty { "Diagram" }, onDone = { vm.saveProject(it); saving = false }, onDismiss = { saving = false })
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("The saved diagram will be removed. Open tabs aren't affected.") },
            confirmButton = { TextButton(onClick = { vm.deleteProject(p); deleting = null }) { Text("Delete", color = colors.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
