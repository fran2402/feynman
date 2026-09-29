package com.example.feynman.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.feynman.R
import com.example.feynman.draw.DiagramShapes
import com.example.feynman.draw.Export
import com.example.feynman.draw.TikZ
import com.example.feynman.latex.MathFont
import com.example.feynman.latex.MathLayout
import com.example.feynman.latex.MathParser
import com.example.feynman.physics.Block
import com.example.feynman.physics.Diagram
import com.example.feynman.physics.Solution
import java.io.File

/** The formats a diagram (and its solution) can be exported in. */
enum class ExportFormat(val label: String, val about: String, val ext: String, val mime: String) {
    Png("PNG image", "The diagram, at three times screen resolution, on white.", "png", "image/png"),
    Svg("SVG drawing", "The diagram as vectors, with the Computer Modern fonts embedded.", "svg", "image/svg+xml"),
    Pdf("PDF", "The diagram and the whole worked solution, page by page.", "pdf", "application/pdf"),
    Latex("LaTeX document", "The diagram in TikZ-Feynman and every step as equations (compile with LuaLaTeX).", "tex", "application/x-tex"),
    Tikz("TikZ-Feynman", "Just the diagram, to paste into a paper.", "tex", "application/x-tex"),
}

/** Light colors for exports, whatever the app's theme. */
private val exportColors = DiagramColors(
    line = Color(0xFF1C1C16), label = Color(0xFF5B6133), momentum = Color(0xFF787767), vertex = Color(0xFF1C1C16),
    selected = Color(0xFF1C1C16), error = Color(0xFFBA1A1A), grid = Color.Transparent,
)

object Exporter {
    fun bytes(context: Context, fonts: AndroidMathFonts, format: ExportFormat, d: Diagram, s: Solution?): ByteArray = when (format) {
        ExportFormat.Png -> png(fonts, d)
        ExportFormat.Svg -> Export.svg(d, drawingStyle(), fonts, fontFiles(context), AppSettings.showMomenta).toByteArray()
        ExportFormat.Pdf -> pdf(fonts, d, s)
        ExportFormat.Latex -> (if (s != null) Export.latex(d, s) else TikZ.of(d)).toByteArray()
        ExportFormat.Tikz -> TikZ.of(d).toByteArray()
    }

    private fun fontFiles(context: Context): Map<MathFont, ByteArray> = mapOf(
        MathFont.Roman to R.font.cm_main, MathFont.Italic to R.font.cm_italic, MathFont.Cal to R.font.cm_cal, MathFont.Big to R.font.cm_size2,
    ).mapValues { (_, id) -> context.resources.openRawResource(id).use { it.readBytes() } }

    /** The diagram's extent (lines and labels) in diagram units. */
    private fun bounds(fonts: AndroidMathFonts, shapes: DiagramShapes): FloatArray {
        val labels = Export.labels(shapes, fonts)
        val xs = shapes.shapes.values.flatMap { sh -> sh.strokes.flatten().map { it.x } + sh.spine.map { it.x } } + labels.flatMap { listOf(it.x, it.x + it.box.width) }
        val ys = shapes.shapes.values.flatMap { sh -> sh.strokes.flatten().map { it.y } + sh.spine.map { it.y } } + labels.flatMap { listOf(it.y - it.box.ascent, it.y + it.box.descent) }
        return floatArrayOf(xs.min() - 12, ys.min() - 12, xs.max() + 12, ys.max() + 12)
    }

    private fun draw(canvas: android.graphics.Canvas, fonts: AndroidMathFonts, shapes: DiagramShapes, b: FloatArray, scale: Float, ox: Float, oy: Float) {
        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(canvas.width.toFloat(), canvas.height.toFloat())) {
            drawDiagram(shapes, fonts, exportColors, { p -> Offset(ox + (p.x - b[0]) * scale, oy + (p.y - b[1]) * scale) }, scale)
        }
    }

    fun png(fonts: AndroidMathFonts, d: Diagram, scale: Float = 3f): ByteArray {
        val shapes = DiagramShapes(d, drawingStyle(), AppSettings.showMomenta)
        val b = bounds(fonts, shapes)
        val bmp = Bitmap.createBitmap(((b[2] - b[0]) * scale).toInt().coerceAtLeast(1), ((b[3] - b[1]) * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        draw(canvas, fonts, shapes, b, scale, 0f, 0f)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /** A4 pages: the diagram, then each step's title, text and equations (long ones broken). */
    fun pdf(fonts: AndroidMathFonts, d: Diagram, s: Solution?): ByteArray {
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 48f
        val width = pageW - 2 * margin
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        var y = margin
        fun newPage() { doc.finishPage(page); pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create()); y = margin }
        fun need(h: Float) { if (y + h > pageH - margin) newPage() }
        val ink = android.graphics.Color.rgb(0x1C, 0x1C, 0x16)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; isFakeBoldText = true; color = android.graphics.Color.rgb(0x5B, 0x61, 0x33) }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = android.graphics.Color.rgb(0x47, 0x47, 0x3B) }
        // Title and diagram.
        page.canvas.drawText(d.name.ifEmpty { "Feynman diagram" }, margin, y + 18, Paint(title).apply { textSize = 22f }); y += 36
        val shapes = DiagramShapes(d, drawingStyle(), AppSettings.showMomenta)
        if (d.lines.isNotEmpty()) {
            val b = bounds(fonts, shapes)
            val scale = minOf(width / (b[2] - b[0]), 260f / (b[3] - b[1]), 1.4f)
            draw(page.canvas, fonts, shapes, b, scale, margin + (width - (b[2] - b[0]) * scale) / 2, y)
            y += (b[3] - b[1]) * scale + 16
        }
        val math = MathLayout(fonts, 11f)
        s?.steps?.forEach { step ->
            need(40f)
            page.canvas.drawText(step.title, margin, y + 16, title); y += 26
            for (blk in step.blocks) {
                val text = when (blk) { is Block.Text -> blk.text; is Block.Note -> "Note: " + blk.text; is Block.Rule -> "${blk.tag}  "; else -> null }
                if (text != null) {
                    // Words wrapped to the width (inline maths kept as its LaTeX source, readable in a PDF).
                    val words = text.replace("\\(", "").replace("\\)", "").split(" ")
                    var line = ""
                    for (w in words) {
                        val trial = if (line.isEmpty()) w else "$line $w"
                        if (body.measureText(trial) > width) { need(14f); page.canvas.drawText(line, margin, y + 11, body); y += 14; line = w } else line = trial
                    }
                    if (line.isNotEmpty()) { need(14f); page.canvas.drawText(line, margin, y + 11, body); y += 14 }
                }
                val tex = when (blk) { is Block.Math -> blk.tex; is Block.Rule -> blk.tex; else -> null } ?: continue
                val box = runCatching { math.lines(MathParser.parse(tex), width) }.getOrNull() ?: continue
                need(box.height + 10)
                val scope = CanvasDrawScope()
                val c = page.canvas
                scope.draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(c), Size(pageW.toFloat(), pageH.toFloat())) {
                    drawMath(fonts, box, margin, y + box.ascent + 4, Color(ink))
                }
                y += box.height + 10
            }
            y += 8
        }
        doc.finishPage(page)
        val out = java.io.ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun fileName(d: Diagram, f: ExportFormat) = (d.name.ifEmpty { "diagram" }.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "diagram" }) +
        (if (f == ExportFormat.Tikz) "_tikz" else "") + "." + f.ext

    /** Writes the file to the cache and opens the share sheet. */
    fun share(context: Context, name: String, mime: String, bytes: ByteArray) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(send, "Share $name"))
    }

    fun save(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
    }
}

/** Export: pick a format, then share it or save it where you like. */
@Composable
fun ExportPage(vm: FeynmanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val fonts = LocalMathFonts.current
    val colors = MaterialTheme.colorScheme
    var pending by remember { mutableStateOf<ExportFormat?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = pending
        if (uri != null && f != null) {
            runCatching { Exporter.save(context, uri, Exporter.bytes(context, fonts, f, vm.diagram, vm.solution)) }
                .onSuccess { message = "Saved." }.onFailure { message = "Couldn't save: ${it.message}" }
        }
        pending = null
    }
    FullScreenPage("Export", onBack = onBack) {
        Text("\"${vm.diagram.name.ifEmpty { "Diagram" }}\" in the tab that's open.", style = MaterialTheme.typography.bodyMedium, color = inkVariant())
        for (f in ExportFormat.entries) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(f.label, style = MaterialTheme.typography.titleMedium)
                Text(f.about, style = MaterialTheme.typography.bodySmall, color = inkVariant())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        runCatching { Exporter.share(context, Exporter.fileName(vm.diagram, f), f.mime, Exporter.bytes(context, fonts, f, vm.diagram, vm.solution)) }
                            .onFailure { message = "Couldn't export: ${it.message}" }
                    }, enabled = vm.diagram.lines.isNotEmpty()) { Text("Share") }
                    OutlinedButton(onClick = { pending = f; saver.launch(Exporter.fileName(vm.diagram, f)) }, enabled = vm.diagram.lines.isNotEmpty()) { Text("Save") }
                }
            }
        }
        message?.let { Text(it, color = inkVariant()) }
    }
}
