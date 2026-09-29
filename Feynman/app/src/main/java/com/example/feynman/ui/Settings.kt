package com.example.feynman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feynman.physics.ConventionPresets
import com.example.feynman.physics.Conventions

@Composable
fun AppSettingsPage(vm: FeynmanViewModel, onBack: () -> Unit, onAcknowledgements: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val changed = { vm.settingsChanged() }
    FullScreenPage("Settings", onBack = onBack) {
        SettingsSection("Sign conventions")
        Text("Books differ in the signs of the couplings and fields (Romão & Silva's Tables 2 and 3). Pick a book, or set each η.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        val conv = AppSettings.conventions
        ConventionPresets.all.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { AppSettings.changeConventions(p.conv); changed() }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = conv == p.conv && ConventionPresets.all.first { it.conv == conv } == p, onClick = { AppSettings.changeConventions(p.conv); changed() })
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    Text(p.refs, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
        val etas: List<Triple<String, Int, (Conventions, Int) -> Conventions>> = listOf(
            Triple("\\eta", conv.eta) { c, v -> c.copy(eta = v) },
            Triple("\\eta'", conv.etaPrime) { c, v -> c.copy(etaPrime = v) },
            Triple("\\eta_Z", conv.etaZ) { c, v -> c.copy(etaZ = v) },
            Triple("\\eta_\\theta", conv.etaTheta) { c, v -> c.copy(etaTheta = v) },
            Triple("\\eta_Y", conv.etaY) { c, v -> c.copy(etaY = v) },
            Triple("\\eta_e", conv.etaE) { c, v -> c.copy(etaE = v) },
            Triple("\\eta_s", conv.etaS) { c, v -> c.copy(etaS = v) },
            Triple("\\eta_G", conv.etaG) { c, v -> c.copy(etaG = v) },
        )
        for ((tex, value, set) in etas) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MathTex(tex, fontSize = 18.sp, wrap = false) }
                SingleChoiceSegmentedButtonRow {
                    listOf(1, -1).forEachIndexed { k, v ->
                        SegmentedButton(selected = value == v, onClick = { AppSettings.changeConventions(set(conv, v)); changed() },
                            shape = SegmentedButtonDefaults.itemShape(k, 2), icon = {}, label = { Text(if (v > 0) "+1" else "−1") })
                    }
                }
            }
        }
        Text("Only η, η_e, η_Z, η_s and η_G appear in Feynman rules; η′, η_θ and η_Y are shown for completeness.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)

        SettingsSection("Calculation")
        SettingsToggle("Massless fermions", "Neglect every fermion mass except the top quark's (the high-energy limit)", AppSettings.masslessFermions) { AppSettings.changeMasslessFermions(it); changed() }
        SettingsToggle("CKM matrix = 1", "Quarks couple to the W only within a generation", AppSettings.ckmIdentity) { AppSettings.changeCkmIdentity(it); changed() }

        SettingsSection("Drawing")
        SettingsToggle("Gluons as coils", "Most textbooks; off draws the paper's tight wave", AppSettings.gluonCoils, AppSettings::changeGluonCoils)
        SettingsToggle("Momentum arrows", "Beside each line, with its momentum", AppSettings.showMomenta, AppSettings::changeShowMomenta)
        SettingsToggle("Snap to the grid", null, AppSettings.snapToGrid, AppSettings::changeSnapToGrid)
        SettingsToggle("Show the grid", null, AppSettings.showGrid, AppSettings::changeShowGrid)

        SettingsSection("Appearance")
        SettingsChoice("Theme", listOf("System", "Light", "Dark"), AppSettings.theme, AppSettings::changeTheme)
        val wallpaperColors = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        if (wallpaperColors) SettingsToggle("Colors from your wallpaper", "Material You dynamic color", AppSettings.dynamicColor, AppSettings::changeDynamicColor)
        if (!wallpaperColors || !AppSettings.dynamicColor) ThemeColorChoice()
        SettingsChoice("Maths size", listOf("Small", "Medium", "Large"), AppSettings.mathSize, AppSettings::changeMathSize)
        SettingsToggle("Expressive motion", "Springy animations; off for calmer ones", AppSettings.expressiveMotion, AppSettings::changeExpressiveMotion)

        SettingsSection("Touch and screen")
        SettingsToggle("Haptic feedback", null, AppSettings.haptics, AppSettings::changeHaptics)
        SettingsToggle("Keep the screen on", "While the app is open", AppSettings.keepScreenOn, AppSettings::changeKeepScreenOn)

        SettingsSection("About")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Open the acknowledgements") { onAcknowledgements() }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Acknowledgements", style = MaterialTheme.typography.bodyLarge)
                Text("The paper, fonts, libraries and methods, with links", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSection(title: String) =
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun SettingsToggle(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsChoice(title: String, options: List<String>, selected: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { k, name ->
                SegmentedButton(selected = selected == k, onClick = { onChange(k) }, shape = SegmentedButtonDefaults.itemShape(k, options.size), icon = {}, label = { Text(name, maxLines = 1) })
            }
        }
    }
}

/** Swatches for the app's color when Material You is off (the first is the built-in olive). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeColorChoice() {
    val colors = MaterialTheme.colorScheme
    val current = AppSettings.themeColor
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("App color", color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalScheme.PRESETS.forEach { (name, seed) ->
                val selected = current == seed
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(if (seed == 0) 0xFF5B6133.toInt() else seed))
                        .then(if (selected) Modifier.border(3.dp, colors.onSurface, CircleShape) else Modifier)
                        .clickable(onClickLabel = name) { AppSettings.changeThemeColor(seed) }
                        .semantics { contentDescription = name + if (selected) ", chosen" else "" },
                )
            }
        }
    }
}
