package com.example.feynman.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.feynman.physics.Diagram
import com.example.feynman.physics.Editing
import com.example.feynman.physics.Evaluate
import com.example.feynman.physics.Generate
import com.example.feynman.physics.SM
import com.example.feynman.physics.SolveOptions
import com.example.feynman.physics.Solution
import com.example.feynman.physics.Solver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tool { Draw, Move, Bend, Erase }

/**
 * The diagrams being drawn (tabs, so several diagrams of one process can be added up), the
 * drawing tools, undo and redo, saved projects, and the solution worked out in the background.
 */
class FeynmanViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("diagrams", Context.MODE_PRIVATE)

    val diagrams = mutableStateListOf<Diagram>()
    var current by mutableStateOf(0)
        private set
    val diagram: Diagram get() = diagrams.getOrElse(current) { Diagram() }

    var tool by mutableStateOf(Tool.Draw)
    var particle by mutableStateOf(SM.electron.id)
    var selectedLine by mutableStateOf<Int?>(null)
    var selectedPoint by mutableStateOf<Int?>(null)
    /** The line or vertex whose explanation is open (after a long press). */
    var helpLine by mutableStateOf<Int?>(null)
    var helpPoint by mutableStateOf<Int?>(null)

    private val undo = ArrayList<Pair<Int, Diagram>>()
    private val redo = ArrayList<Pair<Int, Diagram>>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    var solution by mutableStateOf<Solution?>(null)
        private set
    var solving by mutableStateOf(false)
        private set
    /** Add up every tab with the same external particles. */
    var sumDiagrams by mutableStateOf(true)

    /** Values for numerical evaluation, by symbol name, as typed. */
    val values = mutableStateMapOf<String, String>()
    var mu by mutableStateOf("91.188")

    class Project(val key: String, val name: String, val data: String, val savedAt: Long)
    val projects = mutableStateListOf<Project>()

    init {
        val saved = prefs.getString("tabs", null)?.split(TAB_SEPARATOR)?.mapNotNull { Diagram.decode(it) }.orEmpty()
        if (saved.isEmpty()) diagrams.add(Diagram(name = "Diagram 1")) else diagrams.addAll(saved)
        current = prefs.getInt("current", 0).coerceIn(0, diagrams.size - 1)
        prefs.getString("particle", null)?.let { id -> if (SM.byId(id) != null) particle = id }
        loadProjects()
        Evaluate.defaults.forEach { (k, v) -> values[k] = v.toString() }
        resolve()
    }

    private fun persist() {
        prefs.edit().putString("tabs", diagrams.joinToString(TAB_SEPARATOR) { it.encode() }).putInt("current", current).putString("particle", particle).apply()
    }

    /** Replaces the current diagram, remembering the old one for undo. */
    fun edit(change: (Diagram) -> Diagram) {
        val old = diagram
        val new = change(old)
        if (new == old) return
        undo.add(current to old)
        if (undo.size > 100) undo.removeAt(0)
        redo.clear()
        diagrams[current] = new
        canUndo = true; canRedo = false
        persist()
        resolve()
    }

    fun undo() {
        val (i, d) = undo.removeLastOrNull() ?: return
        if (i < diagrams.size) { redo.add(i to diagrams[i]); diagrams[i] = d; current = i }
        canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        persist(); resolve()
    }

    fun redo() {
        val (i, d) = redo.removeLastOrNull() ?: return
        if (i < diagrams.size) { undo.add(i to diagrams[i]); diagrams[i] = d; current = i }
        canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        persist(); resolve()
    }

    fun choose(p: String) { particle = p; persist() }

    fun selectTab(i: Int) {
        current = i.coerceIn(0, diagrams.size - 1)
        selectedLine = null; selectedPoint = null
        persist(); resolve()
    }

    fun newTab(d: Diagram = Diagram(name = "Diagram ${diagrams.size + 1}")) {
        diagrams.add(d)
        selectTab(diagrams.size - 1)
    }

    fun closeTab(i: Int) {
        if (diagrams.size == 1) { diagrams[0] = Diagram(name = "Diagram 1"); selectTab(0); return }
        diagrams.removeAt(i)
        undo.removeAll { it.first == i }; redo.removeAll { it.first == i }
        selectTab(minOf(current, diagrams.size - 1))
    }

    fun rename(i: Int, name: String) { diagrams[i] = diagrams[i].copy(name = name); persist() }

    /** Puts a template (or an opened project) in a new tab, or the current one if it's empty. */
    fun open(d: Diagram) {
        if (diagram.lines.isEmpty()) { edit { d } } else newTab(d)
    }

    fun clear() = edit { Diagram(name = it.name) }

    /** Bumped to make the canvas bring the diagram back into view. */
    var viewVersion by mutableStateOf(0)
        private set

    /** Tidies the drawing (undoable) and brings it into view. */
    fun tidy() {
        edit { Editing.tidy(it) }
        viewVersion++
    }

    fun recenter() { viewVersion++ }

    // --- Generating every diagram of a process ----------------------------------------------

    var generating by mutableStateOf(false)
        private set
    var generateNote by mutableStateOf<String?>(null)

    /** Makes every diagram of the process and opens them as tabs; [onDone] gets how many. */
    fun generate(legs: List<Generate.Leg>, options: Generate.Options, onDone: (Int) -> Unit) {
        generating = true
        generateNote = null
        viewModelScope.launch {
            val ctx = Solver.context(AppSettings.solveOptions)
            val r = withContext(Dispatchers.Default) { runCatching { Generate.generate(legs, ctx, options) }.getOrNull() }
            generating = false
            if (r == null) { generateNote = "Something went wrong making the diagrams."; onDone(0); return@launch }
            generateNote = r.note
            if (r.diagrams.isNotEmpty()) {
                val first = diagrams.size
                val replaceEmpty = diagrams.size == 1 && diagrams[0].lines.isEmpty()
                r.diagrams.forEachIndexed { i, d -> diagrams.add(d.copy(name = "${i + 1}/${r.diagrams.size}")) }
                if (replaceEmpty) diagrams.removeAt(0)
                selectTab(if (replaceEmpty) 0 else first)
                viewVersion++
            }
            onDone(r.diagrams.size)
            persist()
        }
    }

    // --- Solving -----------------------------------------------------------------------------

    private var job: Job? = null

    /** Works the solution out again (after a short pause, off the main thread). */
    fun resolve() {
        job?.cancel()
        val d = diagram
        val others = if (sumDiagrams) diagrams.filterIndexed { i, _ -> i != current } else emptyList()
        val options = AppSettings.solveOptions
        solving = true
        job = viewModelScope.launch {
            delay(150)
            val s = withContext(Dispatchers.Default) { runCatching { Solver.solve(d, options, others) }.getOrNull() }
            solution = s
            solving = false
        }
    }

    fun numericValues(): Map<String, Double> = values.mapNotNull { (k, v) -> v.replace("−", "-").trim().toDoubleOrNull()?.let { k to it } }.toMap()

    // --- Projects ----------------------------------------------------------------------------

    private fun loadProjects() {
        projects.clear()
        val p = getApplication<Application>().getSharedPreferences("projects", Context.MODE_PRIVATE)
        p.all.keys.filter { it.startsWith("project_") }.mapNotNull { key ->
            val data = p.getString(key, null) ?: return@mapNotNull null
            val d = Diagram.decode(data.substringAfter('\n', "")) ?: return@mapNotNull null
            val time = data.substringBefore('\n').toLongOrNull() ?: 0L
            Project(key, d.name.ifEmpty { "Untitled" }, data.substringAfter('\n', ""), time)
        }.sortedByDescending { it.savedAt }.forEach { projects.add(it) }
    }

    fun saveProject(name: String) {
        val p = getApplication<Application>().getSharedPreferences("projects", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val d = diagram.copy(name = name)
        diagrams[current] = d
        p.edit().putString("project_$now", "$now\n" + d.encode()).apply()
        persist()
        loadProjects()
    }

    fun deleteProject(project: Project) {
        getApplication<Application>().getSharedPreferences("projects", Context.MODE_PRIVATE).edit().remove(project.key).apply()
        loadProjects()
    }

    fun openProject(project: Project) { Diagram.decode(project.data)?.let { open(it) } }

    fun settingsChanged() = resolve()

    fun defaultsFor(options: SolveOptions) = options

    companion object {
        private const val TAB_SEPARATOR = "\n=====\n"
        const val HIT_RADIUS = 22f
        val snap get() = AppSettings.snapToGrid
        fun snapValue(v: Float) = Editing.snap(v, snap)
    }
}
