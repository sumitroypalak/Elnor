package com.elnor.app

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.widget.Toast
import java.io.FileOutputStream
import java.io.IOException

/**
 * Print a note, or save it as a PDF, with Android's own print screen (which has "Save as PDF").
 *
 * The rules that need no Android classes are in [NotePrint] and are tested with plain unit tests.
 * The page drawing is below. It uses the system fonts, so Bengali, emoji and other scripts print
 * the same way they look on the screen.
 */
object NotePrint {

    /** The name of the print job and of the PDF file (without ".pdf"), like the .txt file name. */
    fun jobName(title: String, body: String): String =
        NoteExport.fileName(title, body, NoteExportFormat.TXT).removeSuffix("." + NoteExportFormat.TXT.extension)

    /** What is written in front of a line of a list: "• ", "3. ", "[ ] " or "[x] ". Else "". */
    fun marker(block: MarkdownFormat.Block): String = when (block.kind) {
        MarkdownFormat.BlockKind.BULLET -> "• "
        MarkdownFormat.BlockKind.NUMBERED -> "${block.number}. "
        MarkdownFormat.BlockKind.CHECKLIST -> if (block.checked) "[x] " else "[ ] "
        else -> ""
    }
}

/** One text for the pages: the title big, headings bigger, bold words bold, list marks in front. */
internal fun buildPrintText(title: String, body: String): CharSequence {
    val out = SpannableStringBuilder()
    fun add(text: String, bold: Boolean = false, size: Float = 1f) {
        val start = out.length
        out.append(text)
        if (bold) out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (size != 1f) out.setSpan(RelativeSizeSpan(size), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    val t = title.trim()
    val b = body.trim()
    if (t.isNotEmpty()) {
        add(t, bold = true, size = 1.6f)
        if (b.isNotEmpty()) out.append("\n\n")
    }
    if (b.isNotEmpty()) {
        val lines = b.split('\n')
        for ((i, line) in lines.withIndex()) {
            val block = MarkdownFormat.blockOf(line)
            val size = when (block.kind) {
                MarkdownFormat.BlockKind.HEADING1 -> 1.4f
                MarkdownFormat.BlockKind.HEADING2 -> 1.2f
                else -> 1f
            }
            val heading = size != 1f
            add(" ".repeat(block.indent) + NotePrint.marker(block), size = size)
            for ((piece, bold) in MarkdownFormat.boldRuns(block.content)) {
                add(piece, bold = bold || heading, size = size)
            }
            if (i < lines.lastIndex) out.append('\n')
        }
    }
    return out
}

/**
 * Opens the system print screen for the note. Nothing is saved or changed. A failure is reported
 * with a message and never crashes the app.
 */
fun printNote(context: Context, title: String, body: String) {
    if (noteTextForCopy(title, body).isEmpty()) return
    val started = runCatching {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val name = NotePrint.jobName(title, body)
        manager.print(name, NotePrintAdapter(context.applicationContext, name, buildPrintText(title, body)), null)
    }.isSuccess
    if (!started) {
        Toast.makeText(context.applicationContext, "Could not open the print screen", Toast.LENGTH_SHORT).show()
    }
}

/** Lays the text out for the chosen paper and writes the pages into a PDF. */
private class NotePrintAdapter(
    private val context: Context,
    private val name: String,
    private val text: CharSequence
) : PrintDocumentAdapter() {

    private var attributes: PrintAttributes? = null
    private var pages: List<IntRange> = emptyList() // the lines of the layout on each page
    private var layout: StaticLayout? = null

    // 12 pt text on paper measured in points (1/72 inch); margins are 0.75 inch
    private val marginPt = 54
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 12f }

    override fun onLayout(
        old: PrintAttributes?,
        new: PrintAttributes,
        cancel: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancel.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        attributes = new
        val size = new.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
        val widthPt = size.widthMils * 72 / 1000
        val heightPt = size.heightMils * 72 / 1000
        val contentWidth = (widthPt - 2 * marginPt).coerceAtLeast(100)
        val contentHeight = (heightPt - 2 * marginPt).coerceAtLeast(100)

        val built = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
            .setLineSpacing(0f, 1.3f) // a little more space: Bengali vowel signs need room
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        layout = built

        val result = ArrayList<IntRange>()
        var first = 0
        while (first < built.lineCount) {
            val top = built.getLineTop(first)
            var last = first
            while (last + 1 < built.lineCount && built.getLineBottom(last + 1) - top <= contentHeight) last++
            result.add(first..last)
            first = last + 1
        }
        pages = result.ifEmpty { listOf(0..0) }

        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("$name.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pages.size)
                .build(),
            old == null || old.mediaSize != new.mediaSize
        )
    }

    override fun onWrite(
        ranges: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancel: CancellationSignal,
        callback: WriteResultCallback
    ) {
        val attrs = attributes
        val built = layout
        if (attrs == null || built == null) {
            callback.onWriteFailed("Nothing to print")
            return
        }
        val pdf = PrintedPdfDocument(context, attrs)
        try {
            for ((index, lines) in pages.withIndex()) {
                if (cancel.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }
                if (!wanted(ranges, index)) continue
                val page = pdf.startPage(index)
                val canvas = page.canvas
                val top = built.getLineTop(lines.first)
                val bottom = built.getLineBottom(lines.last)
                canvas.save()
                canvas.translate(marginPt.toFloat(), marginPt.toFloat())
                canvas.clipRect(0f, 0f, built.width.toFloat(), (bottom - top).toFloat())
                canvas.translate(0f, -top.toFloat())
                built.draw(canvas)
                canvas.restore()
                pdf.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
            runCatching { destination.close() }
        }
    }

    private fun wanted(ranges: Array<out PageRange>, page: Int): Boolean =
        ranges.any { it == PageRange.ALL_PAGES || (page >= it.start && page <= it.end) }
}
