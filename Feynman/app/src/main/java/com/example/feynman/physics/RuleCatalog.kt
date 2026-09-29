package com.example.feynman.physics

/*
 * Every Feynman rule of Romão & Silva, Int. J. Mod. Phys. A 27 (2012) 1230025, written as in the
 * paper with the signs η left in, for the app's reference page. Legs are listed as drawn: for
 * three legs upper left, lower left, right; for four, upper left, lower left, upper right,
 * lower right. All momenta incoming except in ghost vertices.
 */
object RuleCatalog {
    /** [into]: for oriented particles, whether the particle flows into the vertex. */
    class RLeg(val particle: String, val into: Boolean, val label: String)

    class Entry(val eq: Int, val section: String, val legs: List<RLeg>, val tex: String) {
        val isPropagator get() = legs.size == 2
    }

    private fun l(p: String, label: String, into: Boolean = true) = RLeg(p, into, label)
    private fun prop(eq: Int, sec: String, p: String, left: String, right: String, tex: String) =
        Entry(eq, sec, listOf(l(p, left), l(p, right)), tex)

    private const val QCDP = "4.1 QCD propagators"
    private const val QCDV = "4.2–4.5 QCD vertices"
    private const val EWP = "5.1 Electroweak propagators"
    private const val TG = "5.2–5.3 Gauge boson self-interactions"
    private const val FF = "5.4–5.6 Fermion interactions"
    private const val HG = "5.7–5.8 Higgs and Goldstone with gauge bosons"
    private const val HH = "5.9–5.10 Higgs and Goldstone self-interactions"
    private const val GH = "5.11–5.13 Ghosts"

    private const val TRIPLE = "\\left[g_{\\sigma\\rho}(p_- - p_+)_\\mu + g_{\\rho\\mu}(p_+ - q)_\\sigma + g_{\\mu\\sigma}(q - p_-)_\\rho\\right]"

    val all: List<Entry> = listOf(
        prop(45, QCDP, "g", "\\mu, a", "\\nu, b", "-i\\delta_{ab}\\left[\\frac{g_{\\mu\\nu}}{k^{2} + i\\epsilon} - (1 - \\xi_G)\\frac{k_\\mu k_\\nu}{(k^{2})^{2}}\\right]"),
        prop(46, QCDP, "om", "a", "b", "\\delta_{ab}\\frac{i\\eta_G}{k^{2} + i\\epsilon}"),
        Entry(47, QCDV, listOf(l("g", "\\rho, c"), l("g", "\\mu, a"), l("g", "\\nu, b")),
            "-\\eta_s g_s f^{abc}\\left[g^{\\mu\\nu}(p_1 - p_2)^\\rho + g^{\\nu\\rho}(p_2 - p_3)^\\mu + g^{\\rho\\mu}(p_3 - p_1)^\\nu\\right]"),
        Entry(48, QCDV, listOf(l("g", "\\sigma, d"), l("g", "\\mu, a"), l("g", "\\rho, c"), l("g", "\\nu, b")),
            "-ig_s^{2}\\left[f_{eab}f_{ecd}(g_{\\mu\\rho}g_{\\nu\\sigma} - g_{\\mu\\sigma}g_{\\nu\\rho}) + f_{eac}f_{edb}(g_{\\mu\\sigma}g_{\\rho\\nu} - g_{\\mu\\nu}g_{\\rho\\sigma}) + f_{ead}f_{ebc}(g_{\\mu\\nu}g_{\\rho\\sigma} - g_{\\mu\\rho}g_{\\nu\\sigma})\\right]"),
        Entry(49, QCDV, listOf(l("u", "i", false), l("u", "j"), l("g", "\\mu, a")), "-i\\eta_s g_s\\gamma^\\mu T^a_{ij}"),
        Entry(50, QCDV, listOf(l("om", "a", false), l("om", "b"), l("g", "\\mu, c")), "-\\eta_s\\eta_G g_s f^{abc} p_1^\\mu"),

        prop(51, EWP, "A", "\\mu", "\\nu", "-i\\left[\\frac{g_{\\mu\\nu}}{k^{2} + i\\epsilon} - (1 - \\xi_A)\\frac{k_\\mu k_\\nu}{(k^{2})^{2}}\\right]"),
        prop(52, EWP, "W", "\\mu", "\\nu", "-i\\frac{1}{k^{2} - m_W^{2} + i\\epsilon}\\left[g_{\\mu\\nu} - (1 - \\xi_W)\\frac{k_\\mu k_\\nu}{k^{2} - \\xi_W m_W^{2}}\\right]"),
        prop(53, EWP, "Z", "\\mu", "\\nu", "-i\\frac{1}{k^{2} - m_Z^{2} + i\\epsilon}\\left[g_{\\mu\\nu} - (1 - \\xi_Z)\\frac{k_\\mu k_\\nu}{k^{2} - \\xi_Z m_Z^{2}}\\right]"),
        prop(54, EWP, "e", "", "", "\\frac{i(\\slashed{p} + m_f)}{p^{2} - m_f^{2} + i\\epsilon}"),
        prop(55, EWP, "h", "", "", "\\frac{i}{p^{2} - m_h^{2} + i\\epsilon}"),
        prop(56, EWP, "phiZ", "", "", "\\frac{i}{p^{2} - \\xi_Z m_Z^{2} + i\\epsilon}"),
        prop(57, EWP, "phi", "", "", "\\frac{i}{p^{2} - \\xi_W m_W^{2} + i\\epsilon}"),
        prop(105, EWP, "cA", "", "", "\\frac{\\eta_G i}{k^{2} + i\\epsilon}"),
        prop(106, EWP, "cp", "", "", "\\frac{\\eta_G i}{k^{2} - \\xi_W m_W^{2} + i\\epsilon}"),
        prop(107, EWP, "cZ", "", "", "\\frac{\\eta_G i}{k^{2} - \\xi_Z m_Z^{2} + i\\epsilon}"),

        Entry(58, TG, listOf(l("W", "W^-_\\sigma", false), l("W", "W^+_\\rho"), l("A", "A_\\mu")), "-i\\eta_e e$TRIPLE"),
        Entry(59, TG, listOf(l("W", "W^-_\\sigma", false), l("W", "W^+_\\rho"), l("Z", "Z_\\mu")), "-i\\eta\\eta_Z g\\cos\\theta_W$TRIPLE"),
        Entry(60, TG, listOf(l("W", "W^+_\\sigma"), l("A", "A_\\mu"), l("W", "W^-_\\rho", false), l("A", "A_\\nu")), "-ie^{2}\\left[2g_{\\sigma\\rho}g_{\\mu\\nu} - g_{\\sigma\\mu}g_{\\rho\\nu} - g_{\\sigma\\nu}g_{\\rho\\mu}\\right]"),
        Entry(61, TG, listOf(l("W", "W^+_\\sigma"), l("Z", "Z_\\mu"), l("W", "W^-_\\rho", false), l("Z", "Z_\\nu")), "-ig^{2}\\cos^{2}\\theta_W\\left[2g_{\\sigma\\rho}g_{\\mu\\nu} - g_{\\sigma\\mu}g_{\\rho\\nu} - g_{\\sigma\\nu}g_{\\rho\\mu}\\right]"),
        Entry(62, TG, listOf(l("W", "W^+_\\sigma"), l("A", "A_\\mu"), l("W", "W^-_\\rho", false), l("Z", "Z_\\nu")), "-i\\eta_e\\eta\\eta_Z eg\\cos\\theta_W\\left[2g_{\\sigma\\rho}g_{\\mu\\nu} - g_{\\sigma\\mu}g_{\\rho\\nu} - g_{\\sigma\\nu}g_{\\rho\\mu}\\right]"),
        Entry(63, TG, listOf(l("W", "W^+_\\sigma"), l("W", "W^+_\\mu"), l("W", "W^-_\\rho", false), l("W", "W^-_\\nu", false)), "ig^{2}\\left[2g_{\\sigma\\mu}g_{\\rho\\nu} - g_{\\sigma\\rho}g_{\\mu\\nu} - g_{\\sigma\\nu}g_{\\rho\\mu}\\right]"),

        Entry(64, FF, listOf(l("u", "u_\\alpha", false), l("d", "d_\\beta"), l("W", "W^+_\\mu")), "-i\\eta\\frac{g}{\\sqrt{2}}\\gamma_\\mu P_L V_{\\alpha\\beta}"),
        Entry(65, FF, listOf(l("d", "d_\\beta", false), l("u", "u_\\alpha"), l("W", "W^-_\\mu", false)), "-i\\eta\\frac{g}{\\sqrt{2}}\\gamma_\\mu P_L V^{*}_{\\alpha\\beta}"),
        Entry(66, FF, listOf(l("nue", "\\nu, \\ell", false), l("e", "\\ell, \\nu"), l("W", "W^\\pm_\\mu")), "-i\\eta\\frac{g}{\\sqrt{2}}\\gamma_\\mu P_L"),
        Entry(67, FF, listOf(l("e", "\\psi_f", false), l("e", "\\psi_f"), l("A", "A_\\mu")), "-i\\eta_e eQ_f\\gamma_\\mu"),
        Entry(68, FF, listOf(l("e", "\\psi_f", false), l("e", "\\psi_f"), l("Z", "Z_\\mu")), "-i\\eta\\eta_Z\\frac{g}{\\cos\\theta_W}\\gamma_\\mu\\left(g_V^f - g_A^f\\gamma_5\\right),\\quad g_V^f = \\frac{1}{2}T^3_f - Q_f\\sin^{2}\\theta_W,\\ g_A^f = \\frac{1}{2}T^3_f"),
        Entry(70, FF, listOf(l("e", "f", false), l("e", "f"), l("h", "h")), "-i\\frac{g}{2}\\frac{m_f}{m_W}"),
        Entry(71, FF, listOf(l("e", "f", false), l("e", "f"), l("phiZ", "\\varphi_Z")), "-gT^3_f\\frac{m_f}{m_W}\\gamma_5"),
        Entry(72, FF, listOf(l("u", "u_\\alpha", false), l("d", "d_\\beta"), l("phi", "\\varphi^+")), "i\\frac{g}{\\sqrt{2}}\\left(\\frac{m_{u\\alpha}}{m_W}P_L - \\frac{m_{d\\beta}}{m_W}P_R\\right)V_{\\alpha\\beta}"),
        Entry(73, FF, listOf(l("d", "d_\\beta", false), l("u", "u_\\alpha"), l("phi", "\\varphi^-", false)), "i\\frac{g}{\\sqrt{2}}\\left(\\frac{m_{u\\alpha}}{m_W}P_R - \\frac{m_{d\\beta}}{m_W}P_L\\right)V^{*}_{\\alpha\\beta}"),
        Entry(74, FF, listOf(l("nue", "\\nu, \\ell", false), l("e", "\\ell, \\nu"), l("phi", "\\varphi^\\pm")), "-i\\frac{g}{\\sqrt{2}}\\frac{m_\\ell}{m_W}P_{R,L}"),

        Entry(75, HG, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("A", "A_\\mu")), "-i\\eta_e e(p_+ - p_-)_\\mu"),
        Entry(76, HG, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("Z", "Z_\\mu")), "-i\\eta\\eta_Z g\\frac{\\cos 2\\theta_W}{2\\cos\\theta_W}(p_+ - p_-)_\\mu"),
        Entry(77, HG, listOf(l("h", "h"), l("phi", "\\varphi^\\mp", false), l("W", "W^\\pm_\\mu")), "\\pm\\frac{i}{2}\\eta g(k - p)_\\mu"),
        Entry(78, HG, listOf(l("phiZ", "\\varphi_Z"), l("phi", "\\varphi^\\mp", false), l("W", "W^\\pm_\\mu")), "-\\eta\\frac{g}{2}(k - p)_\\mu"),
        Entry(79, HG, listOf(l("h", "h"), l("phiZ", "\\varphi_Z"), l("Z", "Z_\\mu")), "-\\eta\\eta_Z\\frac{g}{2\\cos\\theta_W}(k - p)_\\mu"),
        Entry(80, HG, listOf(l("phi", "\\varphi^\\mp", false), l("W", "W^\\pm_\\nu"), l("A", "A_\\mu")), "i\\eta_e\\eta e m_W g_{\\mu\\nu}"),
        Entry(81, HG, listOf(l("phi", "\\varphi^\\mp", false), l("W", "W^\\pm_\\nu"), l("Z", "Z_\\mu")), "-i\\eta_Z g m_Z\\sin^{2}\\theta_W g_{\\mu\\nu}"),
        Entry(82, HG, listOf(l("h", "h"), l("W", "W^\\mp_\\nu", false), l("W", "W^\\pm_\\mu")), "igm_W g_{\\mu\\nu}"),
        Entry(83, HG, listOf(l("h", "h"), l("Z", "Z_\\nu"), l("Z", "Z_\\mu")), "i\\frac{g}{\\cos\\theta_W}m_Z g_{\\mu\\nu}"),
        Entry(84, HG, listOf(l("h", "h"), l("h", "h"), l("W", "W^\\pm_\\mu"), l("W", "W^\\mp_\\nu", false)), "\\frac{i}{2}g^{2}g_{\\mu\\nu}"),
        Entry(85, HG, listOf(l("phiZ", "\\varphi_Z"), l("phiZ", "\\varphi_Z"), l("W", "W^\\pm_\\mu"), l("W", "W^\\mp_\\nu", false)), "\\frac{i}{2}g^{2}g_{\\mu\\nu}"),
        Entry(86, HG, listOf(l("h", "h"), l("h", "h"), l("Z", "Z_\\mu"), l("Z", "Z_\\nu")), "\\frac{i}{2}\\frac{g^{2}}{\\cos^{2}\\theta_W}g_{\\mu\\nu}"),
        Entry(87, HG, listOf(l("phiZ", "\\varphi_Z"), l("phiZ", "\\varphi_Z"), l("Z", "Z_\\mu"), l("Z", "Z_\\nu")), "\\frac{i}{2}\\frac{g^{2}}{\\cos^{2}\\theta_W}g_{\\mu\\nu}"),
        Entry(88, HG, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("A", "A_\\mu"), l("A", "A_\\nu")), "2ie^{2}g_{\\mu\\nu}"),
        Entry(89, HG, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("Z", "Z_\\mu"), l("Z", "Z_\\nu")), "\\frac{i}{2}\\left(\\frac{g\\cos 2\\theta_W}{\\cos\\theta_W}\\right)^{2}g_{\\mu\\nu}"),
        Entry(90, HG, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("W", "W^+_\\mu"), l("W", "W^-_\\nu", false)), "\\frac{i}{2}g^{2}g_{\\mu\\nu}"),
        Entry(91, HG, listOf(l("phi", "\\varphi^\\mp", false), l("h", "h"), l("W", "W^\\pm_\\mu"), l("Z", "Z_\\nu")), "-i\\eta_Z g^{2}\\frac{\\sin^{2}\\theta_W}{2\\cos\\theta_W}g_{\\mu\\nu}"),
        Entry(92, HG, listOf(l("phi", "\\varphi^\\pm"), l("phiZ", "\\varphi_Z"), l("W", "W^\\mp_\\mu", false), l("Z", "Z_\\nu")), "\\mp\\eta_Z g^{2}\\frac{\\sin^{2}\\theta_W}{2\\cos\\theta_W}g_{\\mu\\nu}"),
        Entry(93, HG, listOf(l("phi", "\\varphi^\\pm"), l("h", "h"), l("W", "W^\\mp_\\mu", false), l("A", "A_\\nu")), "\\frac{i}{2}\\eta_e\\eta eg\\, g_{\\mu\\nu}"),
        Entry(94, HG, listOf(l("phi", "\\varphi^\\mp", false), l("phiZ", "\\varphi_Z"), l("W", "W^\\pm_\\mu"), l("A", "A_\\nu")), "\\mp\\frac{1}{2}\\eta_e\\eta eg\\, g_{\\mu\\nu}"),
        Entry(95, HG, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("Z", "Z_\\mu"), l("A", "A_\\nu")), "i\\eta_e\\eta\\eta_Z eg\\frac{\\cos 2\\theta_W}{\\cos\\theta_W}g_{\\mu\\nu}"),

        Entry(96, HH, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("h", "h")), "-\\frac{i}{2}g\\frac{m_h^{2}}{m_W}"),
        Entry(97, HH, listOf(l("h", "h"), l("h", "h"), l("h", "h")), "-\\frac{3}{2}ig\\frac{m_h^{2}}{m_W}"),
        Entry(98, HH, listOf(l("phiZ", "\\varphi_Z"), l("phiZ", "\\varphi_Z"), l("h", "h")), "-\\frac{i}{2}g\\frac{m_h^{2}}{m_W}"),
        Entry(99, HH, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("phi", "\\varphi^-", false)), "-\\frac{i}{2}g^{2}\\frac{m_h^{2}}{m_W^{2}}"),
        Entry(100, HH, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("h", "h"), l("h", "h")), "-\\frac{i}{4}g^{2}\\frac{m_h^{2}}{m_W^{2}}"),
        Entry(101, HH, listOf(l("phi", "\\varphi^+"), l("phi", "\\varphi^-", false), l("phiZ", "\\varphi_Z"), l("phiZ", "\\varphi_Z")), "-\\frac{i}{4}g^{2}\\frac{m_h^{2}}{m_W^{2}}"),
        Entry(102, HH, listOf(l("h", "h"), l("h", "h"), l("h", "h"), l("h", "h")), "-\\frac{3}{4}ig^{2}\\frac{m_h^{2}}{m_W^{2}}"),
        Entry(103, HH, listOf(l("phiZ", "\\varphi_Z"), l("phiZ", "\\varphi_Z"), l("h", "h"), l("h", "h")), "-\\frac{i}{4}g^{2}\\frac{m_h^{2}}{m_W^{2}}"),
        Entry(104, HH, listOf(l("phiZ", "\\varphi_Z"), l("phiZ", "\\varphi_Z"), l("phiZ", "\\varphi_Z"), l("phiZ", "\\varphi_Z")), "-\\frac{3}{4}ig^{2}\\frac{m_h^{2}}{m_W^{2}}"),

        Entry(108, GH, listOf(l("cp", "c^\\pm", false), l("cp", "c^\\pm"), l("A", "A_\\mu")), "\\mp i\\eta_G\\eta_e e p_\\mu"),
        Entry(109, GH, listOf(l("cp", "c^\\pm", false), l("cp", "c^\\pm"), l("Z", "Z_\\mu")), "\\mp i\\eta_G\\eta\\eta_Z g\\cos\\theta_W p_\\mu"),
        Entry(110, GH, listOf(l("cp", "c^\\pm", false), l("cZ", "c_Z"), l("W", "W^\\pm_\\mu")), "\\pm i\\eta_G\\eta\\eta_Z g\\cos\\theta_W p_\\mu"),
        Entry(111, GH, listOf(l("cp", "c^\\pm", false), l("cA", "c_A"), l("W", "W^\\pm_\\mu")), "\\pm i\\eta_G\\eta_e e p_\\mu"),
        Entry(112, GH, listOf(l("cZ", "c_Z", false), l("cp", "c^\\pm"), l("W", "W^\\mp_\\mu", false)), "\\pm i\\eta_G\\eta g\\cos\\theta_W p_\\mu"),
        Entry(113, GH, listOf(l("cA", "c_A", false), l("cp", "c^\\pm"), l("W", "W^\\mp_\\mu", false)), "\\pm i\\eta_G\\eta_e e p_\\mu"),
        Entry(114, GH, listOf(l("cp", "c^\\pm", false), l("cp", "c^\\pm"), l("phiZ", "\\varphi_Z")), "\\pm\\eta_G\\frac{g}{2}\\xi_W m_W"),
        Entry(115, GH, listOf(l("cp", "c^\\pm", false), l("cp", "c^\\pm"), l("h", "h")), "-\\frac{i}{2}\\eta_G g\\xi_W m_W"),
        Entry(116, GH, listOf(l("cZ", "c_Z", false), l("cZ", "c_Z"), l("h", "h")), "-\\eta_G\\frac{ig}{2\\cos\\theta_W}\\xi_Z m_Z"),
        Entry(117, GH, listOf(l("cZ", "c_Z", false), l("cp", "c^\\pm"), l("phi", "\\varphi^\\mp", false)), "\\frac{i}{2}\\eta_G\\eta_Z g\\xi_Z m_Z"),
        Entry(118, GH, listOf(l("cp", "c^\\pm", false), l("cZ", "c_Z"), l("phi", "\\varphi^\\pm")), "-i\\eta_G\\eta_Z g\\frac{\\cos 2\\theta_W}{2\\cos\\theta_W}\\xi_W m_W"),
        Entry(119, GH, listOf(l("cp", "c^\\pm", false), l("cA", "c_A"), l("phi", "\\varphi^\\pm")), "-i\\eta_G\\eta_e\\eta e\\xi_W m_W"),
    )

    val sections: List<String> get() = all.map { it.section }.distinct()

    /** The rule with this equation number. */
    fun byEq(eq: Int) = all.firstOrNull { it.eq == eq }
}
