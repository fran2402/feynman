package com.example.feynman.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.example.feynman.physics.Numbers
import com.example.feynman.physics.Running
import kotlin.math.pow

/** α(μ) and α_s(μ) at one loop, with fermion thresholds. */
@Composable
fun RunningPage(onBack: () -> Unit) {
    var scale by remember { mutableStateOf("91.188") }
    FullScreenPage("Running couplings", onBack = onBack) {
        Text("One-loop running with each fermion switched on at its mass. The QED coefficient is the vacuum polarization's δZ₃ (see the Vacuum polarization diagram's renormalization step).",
            style = MaterialTheme.typography.bodyMedium, color = inkVariant())
        MathTex(Running.QED_TEX, Modifier.fillMaxWidth(), fontSize = 17.sp)
        MathTex(Running.QCD_TEX, Modifier.fillMaxWidth(), fontSize = 17.sp)
        OutlinedTextField(scale, { scale = it }, label = { Text("μ (GeV)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        val mu = scale.toDoubleOrNull()?.takeIf { it > 0 }
        if (mu != null) {
            MathTex("\\alpha(\\mu) = 1/${Numbers.texOf(1 / Running.alpha(mu))}", Modifier.fillMaxWidth(), fontSize = 18.sp)
            if (mu >= 1.0) MathTex("\\alpha_s(\\mu) = ${Numbers.texOf(Running.alphaS(mu))}\\quad (n_f = ${Running.flavours(mu)})", Modifier.fillMaxWidth(), fontSize = 18.sp)
            else Text("α_s isn't perturbative below about 1 GeV.", color = inkVariant())
        }
        // Curves on a log scale in μ.
        val xs = DoubleArray(80) { 0.0 + 4.0 * it / 79 } // log10 μ from 1 to 10⁴ GeV
        LineChart(xs, DoubleArray(80) { 1 / Running.alpha(10.0.pow(xs[it])) }, "\\log_{10}(\\mu/\\text{GeV})", "1/\\alpha(\\mu)", Modifier.fillMaxWidth())
        LineChart(xs, DoubleArray(80) { Running.alphaS(10.0.pow(xs[it])) }, "\\log_{10}(\\mu/\\text{GeV})", "\\alpha_s(\\mu)", Modifier.fillMaxWidth())
        Text("Starting values: α(m_e) = 1/137.036 and α_s(m_Z) = 0.118. Below a few GeV the hadronic contributions to α need data, so the one-loop curve is only indicative there.",
            style = MaterialTheme.typography.bodySmall, color = inkVariant())
    }
}
