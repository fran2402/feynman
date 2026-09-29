package com.example.feynman.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.feynman.physics.Conventions
import com.example.feynman.physics.Gauge
import com.example.feynman.physics.SolveOptions
import com.example.feynman.physics.Theory

/** App-wide settings, kept in preferences and observed by Compose. */
object AppSettings {
    private var prefs: SharedPreferences? = null

    /** 0 follow the system, 1 light, 2 dark. */
    var theme by mutableStateOf(0)
        private set
    /** Colors from the wallpaper (Android 12+). */
    var dynamicColor by mutableStateOf(true)
        private set
    /** The color the scheme is built from when dynamic color is off (ARGB); 0 is the built-in olive. */
    var themeColor by mutableStateOf(0)
        private set
    var expressiveMotion by mutableStateOf(true)
        private set
    var keepScreenOn by mutableStateOf(false)
        private set
    var haptics by mutableStateOf(true)
        private set
    /** 0 small, 1 medium, 2 large. */
    var mathSize by mutableStateOf(1)
        private set

    /** The signs η of Romão & Silva (a book's, or your own). */
    var conventions by mutableStateOf(Conventions())
        private set
    /** Neglect fermion masses (except the top quark's). */
    var masslessFermions by mutableStateOf(false)
        private set
    /** CKM matrix = 1. */
    var ckmIdentity by mutableStateOf(false)
        private set

    /** Gluons as coils (as in most textbooks) rather than the paper's tight wave. */
    var gluonCoils by mutableStateOf(false)
        private set
    /** Momentum arrows beside the lines. */
    var showMomenta by mutableStateOf(true)
        private set
    /** Snap points to the grid while drawing. */
    var snapToGrid by mutableStateOf(true)
        private set
    var showGrid by mutableStateOf(true)
        private set

    /** The theory the palette and the rules come from. */
    var theory by mutableStateOf(Theory.SM)
        private set
    var gauge by mutableStateOf(Gauge.Feynman)
        private set
    /** Breit–Wigner widths for unstable particles in cross sections and widths. */
    var widths by mutableStateOf(true)
        private set
    /** MSSM: the mixing matrices N, U, V as numbers (from M₁, M₂, μ, tan β) rather than symbols. */
    var numericMixing by mutableStateOf(true)
        private set

    val mathScale: Float get() = when (mathSize) { 0 -> 0.85f; 2 -> 1.18f; else -> 1f }

    val solveOptions: SolveOptions get() = SolveOptions(conventions, masslessFermions, ckmIdentity, theory, gauge, widths)

    fun init(context: Context) {
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        theme = p.getInt("theme", 0)
        dynamicColor = p.getBoolean("dynamicColor", true)
        themeColor = p.getInt("themeColor", 0)
        expressiveMotion = p.getBoolean("expressiveMotion", true)
        keepScreenOn = p.getBoolean("keepScreenOn", false)
        haptics = p.getBoolean("haptics", true)
        mathSize = p.getInt("mathSize", 1)
        conventions = Conventions.decode(p.getString("conventions", null)) ?: Conventions()
        masslessFermions = p.getBoolean("masslessFermions", false)
        ckmIdentity = p.getBoolean("ckmIdentity", false)
        gluonCoils = p.getBoolean("gluonCoils", false)
        showMomenta = p.getBoolean("showMomenta", true)
        snapToGrid = p.getBoolean("snapToGrid", true)
        showGrid = p.getBoolean("showGrid", true)
        theory = runCatching { Theory.valueOf(p.getString("theory", null)!!) }.getOrDefault(Theory.SM)
        gauge = runCatching { Gauge.valueOf(p.getString("gauge", null)!!) }.getOrDefault(Gauge.Feynman)
        widths = p.getBoolean("widths", true)
        numericMixing = p.getBoolean("numericMixing", true)
    }

    private fun save(key: String, value: Any) {
        prefs?.edit()?.apply {
            when (value) { is Int -> putInt(key, value); is Boolean -> putBoolean(key, value); is String -> putString(key, value) }
            apply()
        }
    }

    fun changeTheme(v: Int) { theme = v; save("theme", v) }
    fun changeDynamicColor(v: Boolean) { dynamicColor = v; save("dynamicColor", v) }
    fun changeThemeColor(v: Int) { themeColor = v; save("themeColor", v) }
    fun changeExpressiveMotion(v: Boolean) { expressiveMotion = v; save("expressiveMotion", v) }
    fun changeKeepScreenOn(v: Boolean) { keepScreenOn = v; save("keepScreenOn", v) }
    fun changeHaptics(v: Boolean) { haptics = v; save("haptics", v) }
    fun changeMathSize(v: Int) { mathSize = v; save("mathSize", v) }
    fun changeConventions(v: Conventions) { conventions = v; save("conventions", v.encode()) }
    fun changeMasslessFermions(v: Boolean) { masslessFermions = v; save("masslessFermions", v) }
    fun changeCkmIdentity(v: Boolean) { ckmIdentity = v; save("ckmIdentity", v) }
    fun changeGluonCoils(v: Boolean) { gluonCoils = v; save("gluonCoils", v) }
    fun changeShowMomenta(v: Boolean) { showMomenta = v; save("showMomenta", v) }
    fun changeSnapToGrid(v: Boolean) { snapToGrid = v; save("snapToGrid", v) }
    fun changeShowGrid(v: Boolean) { showGrid = v; save("showGrid", v) }
    fun changeTheory(v: Theory) { theory = v; save("theory", v.name) }
    fun changeGauge(v: Gauge) { gauge = v; save("gauge", v.name) }
    fun changeWidths(v: Boolean) { widths = v; save("widths", v) }
    fun changeNumericMixing(v: Boolean) { numericMixing = v; save("numericMixing", v) }
}
