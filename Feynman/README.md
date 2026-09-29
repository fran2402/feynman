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
  29 ready-made ones (including two- and three-loop and supersymmetric diagrams; picking one
  switches to its theory if needed). The target button beside the tabs brings the diagram back
  into view. Generated diagrams are laid out tidily (ends in columns, vertices spaced, lines
  straight).
  - **Long-press** a palette key, a drawn line or a vertex for its card, as on CAS Calculator's
    keys: spin, charge, T³, color, mass (with its value), how it's drawn, its propagator, a line
    of theory, and every vertex of the paper it takes part in (drawn). For a drawn line the card
    also gives its momentum and what it is in this diagram (u(p₁), ε*_μ(p₃), or its
    propagator); for a vertex, the rule it uses with your signs next to the paper's form.
  - The palette is always two rows of three, with empty places when a group is smaller, so it
    keeps its size.
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
  - **two and three loops:** the denominators, the Feynman parameters and the Symanzik
    polynomials 𝒰 = det M and ℱ = Qᵀadj(M)Q − 𝒰J, the shift of each loop momentum, the numerator
    after Gaussian pairing of the loop momenta (each pair −½g^{μν}adj(M)_{ab}/𝒰, by the number of
    pairs), and the parametric integral with its Γ(N − Ld/2 − r) and powers of 𝒰 and ℱ. Under
    Numbers the coefficients of (i/16π²)^L ε^k, k = −2L … 0 (MS-bar), for each Lorentz/Dirac
    structure, with errors: iterated sector decomposition (Binoth–Heinrich), Taylor subtraction
    of the singular powers (the poles), and randomized quasi-Monte Carlo for the rest. With a
    single scale and no masses it's computed at −v = 1 and continued analytically; above a
    threshold (ℱ changing sign inside the region) it says a contour deformation would be needed.
- **Rules.** Only the rules of the chosen theory, drawn and written out by section: the paper's
  (with the η signs left in and your current signs at the top) and each model's own.

## Also in the app
- **Zoom:** pinch with two fingers to zoom and pan, whatever tool is on; **double-tap** the canvas
  to fit the whole diagram in view.
- **Theories** (Settings → Theory): the Standard Model; QED; QCD; scalar φ⁴ (with φ³); a type-II
  two-Higgs-doublet model (H, A, H± with couplings from the paper's rules times the usual
  α, β factors, as in the Higgs Hunter's Guide; the extra scalars' self-couplings aren't
  included); the Standard Model with a sequential Z′; and four supersymmetric theories:
  - **Wess–Zumino:** a complex scalar φ and a Majorana ψ with W = mΦ²/2 + yΦ³/6 (its scalar
    self-energy's pole comes out ∝ p² + m²: only wave-function renormalization).
  - **Supersymmetric QED:** e, μ, their scalar partners ẽ_L, ẽ_R, μ̃_L, μ̃_R, the photon and the
    photino, with the D-term four-scalar couplings.
  - **Supersymmetric QCD:** squarks q̃_L, q̃_R and the gluino.
  - **MSSM:** sleptons, sneutrinos, squarks (no left–right mixing), four neutralinos and two
    charginos from M₁, M₂, μ and tan β (Martin's mass matrices, diagonalized numerically), the
    gluino, and the type-II Higgs sector h, H, A, H±. Gauge couplings of the new fields come from
    the paper's covariant derivative with its signs; gaugino and higgsino couplings are Martin's.
    Settings → *Mixing matrices as numbers* (on by default) puts N, U and V in as decimals, which
    keeps |ℳ|² short. Left out: A-terms, the MSSM's four-scalar and Higgs self-couplings.

  Majorana fermions (drawn without an arrow; photino and gluino with a wave on the line) and
  fermion-number-violating vertices use Denner's fermion flow: each line is read one way, a
  vertex read against its written order gets Γ′ = CΓᵀC⁻¹, spinors follow from in/out, and in
  |ℳ|² lines are transposed where needed to join the spin sums. The palette shows the theory's
  fields (a scrolling row of groups for the MSSM).
- **Gauges** (Settings → Gauge): Feynman–'t Hooft, Landau, general Rξ with ξ kept as a symbol
  (e⁺e⁻ → μ⁺μ⁻ comes out independent of ξ; the electron self-energy pole is e²ξp̸ − (3 + ξ)e²m),
  and unitary (no Goldstones or massive-boson ghosts). Gauge propagators with several
  denominators split a loop into several integrals, each worked through and added.
- **Generate diagrams** (⋮ menu): pick the incoming and outgoing particles; every tree diagram
  (or every one-loop diagram, without leg corrections and tadpoles if you like) of the theory
  opens as tabs, laid out tidily. Trees are built from currents joined at valid vertices; loops by
  joining two extra legs of trees, with copies removed by graph isomorphism. For example QED
  gives 2 diagrams for Bhabha, Compton and e⁺e⁻ → γγ, and the Standard Model 4 for
  e⁺e⁻ → μ⁺μ⁻ (γ, Z, h, φ_Z).
- **Cross sections and widths** (under Numbers): σ at √s in pb with dσ/dcos θ and σ(√s) charts
  for 2 → 2, Γ and τ for 1 → 2 and 1 → 3 decays (Dalitz-plot integration), with Breit–Wigner
  widths for Z, W, h, t and new particles (can be switched off).
- **Passarino–Veltman functions:** A₀ and B₀ in closed form (every ∫₀¹ xᵏ ln Δ dx exactly, at the
  roots of Δ), so two-point finite parts are exact; C₀ with its inner integral in closed form;
  D₀ numerically. Their values appear under Numbers.
- **Renormalization** (a step for one-loop diagrams): the MS-bar counterterm with δZ, δM (or
  δZ_L, δZ_R), δZ₃ and δM², δ₁ for vertices, a transversality check for vector self-energies,
  the Ward identity δ₁ = δ₂ when the self-energy is in another tab, and the loop's share of
  β(e) or β(g_s). **Running couplings** (⋮ menu): α(μ) and α_s(μ) at one loop with thresholds.
- **Export** (⋮ menu): PNG, SVG (fonts embedded), PDF (the diagram and the whole solution),
  a LaTeX document (TikZ-Feynman diagram and every step with breqn; compile with LuaLaTeX) or
  just the TikZ-Feynman code; share or save each.

## How lines are drawn
As in the paper's figures: fermions solid with an arrow on the line, γ, Z and W as waves (W with
a small arrow for the flow of W⁺), gluons as a tighter wave (or coils, in Settings), h and φ
dashed (φ± with an arrow), ghosts dotted with an arrow, and momenta as short arrows beside the
lines with their labels (p₁, p₁ + p₂, k …). *Copy as TikZ-Feynman* in the ⋮ menu gives the
diagram as `tikz-feynman` code, placed as drawn.

## Signs and gauge
Settings → *Sign conventions* picks a book from the paper's Tables 2 and 3 (Bailin & Love,
Pokorski, Peskin & Schroeder, Cheng & Li, Itzykson & Zuber, Branco–Lavoura–Silva, Romão, …) or
sets each η (η, η′, η_Z, η_θ, η_Y, η_e, η_s, η_G). Each book is linked by ISBN (to WorldCat),
DOI or arXiv number; Bailin & Love, Aitchison & Hey, Okun and Das link to a catalogue search,
since their ISBNs couldn't be confirmed. In dark mode every formula, label, line, key and icon
is white. Calculations are in the Feynman–'t Hooft
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
  `Evaluate.kt`, `Solution.kt` (the steps), `Templates.kt`, `Editing.kt`; `Susy.kt` (the
  supersymmetric fields, mixing matrices and rules), `ModelRules.kt` (the models' rules for the
  Rules panel), `MultiLoop.kt` (Symanzik polynomials, shifts, pairings) and `Sectors.kt`
  (ε-series, sector decomposition, subtraction and quasi-Monte Carlo).
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

The tests check, among others: σ(e⁺e⁻ → μ⁺μ⁻) = 4πα²/3s, Drell–Yan's 1/N_c, Γ(Z → ee),
Γ(h → bb̄), the muon lifetime G_F²m_μ⁵/192π³, ξ-independence, the Ward identity, B₀ and C₀
against quadrature, diagram counts, tidy and the 2HDM scaling of h → bb̄; and also: traces (including γ5 and ε·ε = −24) and d-dimensional
contractions; e⁻e⁺ → μ⁻μ⁺ (2e⁴(t² + u²)/s² massless, and the full massive formula); Bhabha
scattering with its interference sign; the QED vacuum polarization (transverse, pole
−(4/3)e²(p²g − pp), finite part against a direct integration) and the electron self-energy pole
e²(p̸ − 4m); that all templates solve and every rule (45)–(119) is listed.

Supersymmetry: e⁺e⁻ → γ̃γ̃ is a P wave with the known t/u shape, e⁻e⁻ → ẽ_R⁻ẽ_R⁻ needs the Majorana
mass (t and u adding), q q̄ → g̃g̃ is q q̄ → QQ̄ times 6, the Wess–Zumino mass isn't renormalized,
the neutralino, chargino and W Goldstone couplings obey their Ward identities (∝ mass
differences, 1/m_Z and 1/m_W), and W⁺W⁻ → χ̃⁺χ̃⁻, ZZ → χ̃⁰χ̃⁰ and e⁻Z → ẽ_L χ̃⁰ stay bounded at high
energy in the unitary gauge.

Loops: the multi-loop engine agrees with the one-loop code (poles and finite parts), and with
exact results for the massless two-loop sunset (space- and timelike), the sunset with a k₁·k₂
numerator, the three-loop banana and the three-loop bubble chain (G-functions), and the
massive double bubble's poles against the one-loop bubble.

`preview-render/` holds two of those renders.

## Building
Open `Feynman/` in Android Studio, or `./gradlew assembleDebug` (with a Gradle wrapper; the
versions are the same as CAS Calculator's: AGP 8.10, Kotlin 2.1.21, compileSdk 36, material3
1.4.0-alpha18). For release signing, copy `keystore.properties.example`.
