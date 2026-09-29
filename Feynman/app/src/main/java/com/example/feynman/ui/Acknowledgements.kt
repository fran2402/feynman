package com.example.feynman.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * One thing the app is built on or with: its name, who made it, what it does in this app,
 * a line on what it is, its licence, and a link.
 */
data class Credit(val name: String, val by: String, val use: String, val about: String, val licence: String, val url: String)

/** A group of credits with a line saying what the group is. */
data class CreditGroup(val title: String, val intro: String, val credits: List<Credit>)

val CREDITS: List<CreditGroup> = listOf(
    CreditGroup("The physics", "Where the rules come from.", listOf(
        Credit("A resource for signs and Feynman diagrams of the Standard Model", "Jorge C. Romão and João P. Silva", "Every Feynman rule, equations (45)–(119), and the sign conventions of the books",
            "Int. J. Mod. Phys. A 27 (2012) 1230025: all Standard Model rules with ghosts in an Rξ gauge, with the signs η that differ between books.", "Published paper", "https://doi.org/10.1142/S0217751X12300256"),
        Credit("An Introduction to Quantum Field Theory", "Michael E. Peskin and Daniel V. Schroeder", "Dimensional regularization, Feynman parameters and the one-loop master integrals (A.44)–(A.47)",
            "The textbook the loop tests are checked against (vacuum polarization, the electron self-energy).", "Book", "https://doi.org/10.1201/9780429503559"),
        Credit("Passarino–Veltman functions", "Giampiero Passarino and Martinus Veltman", "The names A₀, B₀, C₀, D₀ for scalar one-loop integrals",
            "Nucl. Phys. B 160 (1979) 151.", "Published paper", "https://doi.org/10.1016/0550-3213(79)90234-7"),
        Credit("TikZ-Feynman", "Joshua Ellis", "The diagram export format",
            "A LaTeX package for drawing Feynman diagrams.", "LaTeX Project Public License", "https://ctan.org/pkg/tikz-feynman"),
    )),
    CreditGroup("Fonts", "Every font is bundled with the app under the SIL Open Font License, whose text is in the app's source.", listOf(
        Credit("Google Sans Flex", "Google", "Labels, menus and all other text",
            "A variable font; the app always uses its fully rounded setting (ROND 100).", "SIL Open Font License 1.1", "https://fonts.google.com/specimen/Google+Sans+Flex"),
        Credit("Roboto", "Christian Robertson, Google", "Characters Google Sans Flex doesn't have",
            "Android's own typeface.", "SIL Open Font License 1.1", "https://fonts.google.com/specimen/Roboto"),
        Credit("MathJax TeX fonts", "The MathJax Consortium, after Donald Knuth's Computer Modern", "All the maths and particle names",
            "The fonts LaTeX documents are set in, so amplitudes look as they would in print.", "SIL Open Font License 1.1", "https://github.com/mathjax/MathJax/tree/legacy-v2/fonts"),
    )),
    CreditGroup("Libraries", "Open-source code the app is built with.", listOf(
        Credit("Kotlin", "JetBrains", "The whole app", "The algebra, the traces and the loop integrals are plain Kotlin.", "Apache License 2.0", "https://kotlinlang.org"),
        Credit("Jetpack Compose and Material 3 Expressive", "Google", "The interface", "material3 1.4.0-alpha18, as in CAS Calculator.", "Apache License 2.0", "https://developer.android.com/compose"),
    )),
    CreditGroup("Methods", "Published methods behind the numbers.", listOf(
        Credit("Gauss–Legendre quadrature", "Carl Friedrich Gauss, Adrien-Marie Legendre", "Integrals over Feynman parameters",
            "Exact for polynomials of high degree; mapped onto the simplex for two and three parameters.", "Published method", "https://en.wikipedia.org/wiki/Gauss%E2%80%93Legendre_quadrature"),
        Credit("Gell-Mann matrices", "Murray Gell-Mann", "Color factors, summed exactly",
            "The generators of SU(3); f^{abc} follows from their commutators.", "Published method", "https://en.wikipedia.org/wiki/Gell-Mann_matrices"),
    )),
    CreditGroup("Inspiration", "Apps whose look this one borrows.", listOf(
        Credit("CAS Calculator", "Fran Stimac", "The look: fonts, colors, the mode switcher and the keypad", "The sister app this one is built alongside.", "—", "https://github.com/fran2402/cascalc"),
    )),
)

/** The acknowledgements: each group with a line on what it is, then a card per credit that opens its link. */
@Composable
fun AcknowledgementsDialog(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    // A full-screen page, not a popup.
    FullScreenPage("Acknowledgements", onBack = onDismiss) {
        Text(
            "Feynman is built on the work of many people. Tap any entry to read more.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
        )
        CREDITS.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, color = colors.primary)
                Text(group.intro, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                group.credits.forEach { c ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surfaceContainer)
                            .clickable(onClickLabel = "Open ${c.name}") { runCatching { uri.openUri(c.url) } }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        }
                        if (c.by != "—") Text(c.by, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Text("Used for: " + c.use, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                        Text(c.about, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (c.licence != "—") {
                            Text(
                                c.licence,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSecondaryContainer,
                                modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(colors.secondaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
