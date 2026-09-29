# Feynman (Android)

## Three modes
The switcher at the top, as in CAS Calculator:

- **Draw.** Drag from one point to another to draw a line of the chosen particle. A point with
  one line is an external particle; points where three or four lines meet are vertices. The
  palette below has every Standard Model field in groups: leptons, quarks, gauge bosons
  (γ, Z, W, g), scalars (h, φ_Z, φ±) and ghosts (c_A, c_Z, c±, ω). Tools: draw, move (points,
  or the view when you drag empty space), bend (drag a line's middle), erase, undo and redo.
  Tap a line to change its particle, reverse its arrow (particle ↔ antiparticle, W⁺ ↔ W⁻),
  straighten it, hide its momentum, or mark an external line incoming or outgoing (by default
  ends on the left come in and ends on the right go out). Drawing a second line between the
  same two points bends both, and dragging from a vertex back to itself makes a loop. The chip
  at the top shows the process and the number of loops, or what's wrong (a vertex the Standard
  Model doesn't have is marked in red). Tabs hold several diagrams; *Textbook diagrams* opens
  22 ready-made ones.
- **Solution.** The worked answer, step by step, each card with a copy-LaTeX button:
  the process; every rule used, with the paper's equation number; the momentum on each line;
  iℳ written factor by factor (spinors ū, v̄, u, v and polarizations ε, ε* on the external
  lines, fermion lines read against their arrows, (−1) and a trace for each closed fermion or
  ghost loop, the symmetry factor, ∫d⁴k/(2π)⁴ for the loop); then
  - **trees:** iℳ with the indices contracted, the spin sums as traces, and the averaged
    |ℳ|², first in dot products and then in Mandelstam variables s, t, u (2 → 2) or masses
    (decays). Diagrams in other tabs with the same external particles are added in, with the
    relative sign from the order of the external fermions (Bhabha's s- and t-channels).
    Color factors are summed exactly.
  - **one loop:** the denominators and the scalar integral they make (A₀, B₀, C₀, D₀), the
    Feynman parameters, the shift and Δ, the numerator after the traces and after symmetric
    integration, the master integrals, and the result in d = 4 − 2ε: the UV pole (integrated
    over the Feynman parameters exactly) and the finite part (MS-bar, as an integral
    over the Feynman parameters). The color factor is shown as a multiple of δ, T^a or f^{abc}.
  - **numbers:** every mass, coupling and invariant has a value (Standard Model values to start
    with); the loop's pole and finite part are evaluated for each Lorentz/Dirac structure, the
    Feynman-parameter integrals by Gauss–Legendre quadrature on the simplex (with Δ − i0
    above thresholds), and |ℳ|² at a point.
- **Rules.** All 75 rules of the paper, drawn in its style and written with the η signs left in,
  by section, with your current signs at the top.

## How lines are drawn
As in the paper's figures: fermions solid with an arrow on the line, γ, Z and W as waves (W with
a small arrow for the flow of W⁺), gluons as a tighter wave (or coils, in Settings), h and φ
dashed (φ± with an arrow), ghosts dotted with an arrow, and momenta as short arrows beside the
lines with their labels (p₁, p₁ + p₂, k …). *Copy as TikZ-Feynman* in the ⋮ menu gives the
diagram as `tikz-feynman` code, placed as drawn.

## Signs and gauge
Settings → *Sign conventions* picks a book from the paper's Tables 2 and 3 (Bailin & Love,
Pokorski, Peskin & Schroeder, Cheng & Li, Itzykson & Zuber, Branco–Lavoura–Silva, Romão, …) or
sets each η (η, η′, η_Z, η_θ, η_Y, η_e, η_s, η_G). Calculations are in the Feynman–'t Hooft
gauge, ξ = 1 (so the Goldstone and ghost masses are m_W and m_Z). Other settings: neglect
fermion masses (except the top's), CKM = 1, gluon style, momentum arrows, grid, theme and
colors as in CAS Calculator.

## Code
- `physics/` (plain Kotlin, no Android): the algebra (`Algebra.kt`: exact complex rational
  coefficients, dot products, vectors, metrics, ε tensors, propagator denominators, Dirac
  strings), `Dirac.kt` (index contraction, traces with γ5 in 4 dimensions, d-dimensional
  γ^μ…γ_μ, normal ordering and the Dirac equation), `Model.kt` (fields and the η presets),
  `Rules.kt` (the rules, eqs. 45–119, as algebra and as LaTeX), `RuleCatalog.kt` (as printed),
  `Diagram.kt` (topology, externals, loop count, momentum routing), `Amplitude.kt`,
  `Squared.kt`, `Loop.kt`, `Color.kt` (SU(3) with the Gell-Mann matrices), `Kinematics.kt`,
  `Evaluate.kt`, `Solution.kt` (the steps), `Templates.kt`, `Editing.kt`.
- `latex/MathLayout.kt`: a small TeX (parser and box layout, line breaking at +, − and =) in
  Computer Modern, drawn by the app with Android's Canvas.
- `draw/`: the line shapes (waves, coils, dashes, dots, arrows, momentum arrows) and TikZ export.
- `ui/`: Compose screens. Theme, colors, fonts, the mode switcher and the full-screen pages come
  from CAS Calculator.

## Checks
`jvm/` builds the plain-Kotlin parts on the JVM without the Android SDK:

```
cd jvm
gradle test                       # physics, layout and editing tests
gradle run --args="out-dir"       # renders every textbook diagram and its solution to PNG
```

The tests check, among others: traces (including γ5 and ε·ε = −24) and d-dimensional
contractions; e⁻e⁺ → μ⁻μ⁺ (2e⁴(t² + u²)/s² massless, and the full massive formula); Bhabha
scattering with its interference sign; the QED vacuum polarization (transverse, pole
−(4/3)e²(p²g − pp), finite part against a direct integration) and the electron self-energy pole
e²(p̸ − 4m); that all 22 templates solve and every rule (45)–(119) is listed.

`preview-render/` holds two of those renders.

## Building
Open `Feynman/` in Android Studio, or `./gradlew assembleDebug` (with a Gradle wrapper; the
versions are the same as CAS Calculator's: AGP 8.10, Kotlin 2.1.21, compileSdk 36, material3
1.4.0-alpha18). For release signing, copy `keystore.properties.example`.
