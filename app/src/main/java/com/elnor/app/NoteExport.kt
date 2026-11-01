package com.elnor.app

import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The two kinds of file a note can be saved as. */
enum class NoteExportFormat(val extension: String, val mimeType: String, val label: String) {
    /** Plain text: the formatting marks (** and #) are taken away, like in the notes list. */
    TXT("txt", "text/plain", "Save as .txt"),

    /** Markdown: the note exactly as written, with the title as a "#" heading on top. */
    MD("md", "text/markdown", "Save as .md")
}

/**
 * Saves one note as a file the user chooses a place for (Android's own "Save as" screen), so the
 * note can be kept outside the app, opened on a computer, or put into another notes app.
 *
 * The rules are here, without any Android classes, so they are tested with plain unit tests.
 * Writing the file is at the end of this file.
 */
object NoteExport {

    private const val MAX_NAME_CODE_POINTS = 80
    private const val FALLBACK_NAME = "note"

    /** Characters that a file name cannot (or should not) have on phones and computers. */
    private val FORBIDDEN = Regex("""[\\/:*?"<>|]""")

    /**
     * A safe file name made from the name the note has in the notes list (its title, or its first
     * line, or "Untitled note"), for example "Shopping list.txt". Forbidden characters and line
     * breaks become spaces, runs of spaces become one, and a name is at most 80 characters long.
     * Bengali, emoji and other letters stay as they are.
     */
    fun fileName(title: String, body: String, format: NoteExportFormat): String {
        val shown = getListTitle(Note(id = 0L, title = title, body = body, updatedAt = 0L))
        val cleaned = shown
            .map { ch -> if (Character.isISOControl(ch)) ' ' else ch }
            .joinToString("")
            .replace(FORBIDDEN, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val limited = limitCodePoints(cleaned, MAX_NAME_CODE_POINTS)
            .trim()
            .trim('.') // a name must not end with (or be only) dots
            .trim()
        val base = limited.ifEmpty { FALLBACK_NAME }
        return base + "." + format.extension
    }

    /**
     * The text of the file. Spaces at the start and the end of the title and of the text are
     * removed, like when a note is saved. A part that is empty is left out, and the file ends with
     * one line break. Returns "" when the note has nothing in it.
     *
     *  - [NoteExportFormat.TXT]: title, an empty line, then the text without formatting marks.
     *  - [NoteExportFormat.MD]: "# title", an empty line, then the text exactly as written.
     */
    fun text(title: String, body: String, format: NoteExportFormat): String {
        val t = title.trim()
        val b = body.trim()
        val content = when (format) {
            NoteExportFormat.TXT -> join(t, MarkdownFormat.plainText(b))
            NoteExportFormat.MD -> join(if (t.isEmpty()) "" else "# $t", b)
        }
        return if (content.isEmpty()) "" else content + "\n"
    }

    private fun join(head: String, body: String): String = when {
        head.isEmpty() -> body
        body.isEmpty() -> head
        else -> "$head\n\n$body"
    }

    /** The first [max] characters, counted as whole characters (an emoji is never cut in half). */
    private fun limitCodePoints(text: String, max: Int): String {
        if (text.codePointCount(0, text.length) <= max) return text
        return text.substring(0, text.offsetByCodePoints(0, max))
    }
}

/**
 * Writes the note to [uri] (chosen by the user in the "Save as" screen) as UTF-8, then shows a
 * short message. The writing is done through [ApplicationScope], so leaving the page at the same
 * moment cannot stop it half-way. A failure is reported with a message and never crashes the app.
 */
fun saveNoteToUri(context: Context, uri: Uri, title: String, body: String, format: NoteExportFormat) {
    val appContext = context.applicationContext
    val text = NoteExport.text(title, body, format)
    ApplicationScope.scope.launch {
        val ok = try {
            withContext(Dispatchers.IO) {
                // "wt": an existing file is emptied first, so no old bytes stay after a shorter text
                val stream = appContext.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Cannot open the file for writing")
                stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(
                appContext,
                if (ok) "Note saved as a .${format.extension} file" else "Could not save the file",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
