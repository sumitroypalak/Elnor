package com.elnor.app


/**
 * [deletedAt] is null for a normal note. A note in the Trash has the time it was deleted.
 * It comes last and has a default, so Note(id, title, body, updatedAt) still works everywhere.
 */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** null for a normal note. A pinned note has the time it was pinned and is shown first. */
    val pinnedAt: Long? = null,
    /** null for a normal note. An archived note has the time it was archived and is not on the home list. */
    val archivedAt: Long? = null
)

private const val UNTITLED_NOTE = "Untitled note"

/**
 * Name shown in the notes list. Calculated every time the list is drawn
 * and never saved, so it follows the text when the note is edited.
 *
 * 1. User's Title, if it has any text
 * 2. otherwise the first line of the body that has text
 * 3. otherwise "Untitled note"
 */
fun getDisplayTitle(note: Note): String {
    val title = note.title.trim()
    if (title.isNotEmpty()) return title

    val firstLine = note.body
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }

    return firstLine ?: UNTITLED_NOTE
}

/**
 * Preview text shown under the name. When the first line is already used
 * as the name, it is skipped here so it does not appear twice.
 */
fun getPreviewBody(note: Note): String {
    if (note.title.isNotBlank()) return note.body.trim()

    val lines = note.body.lines()
    val firstIndex = lines.indexOfFirst { it.isNotBlank() }
    if (firstIndex == -1) return ""

    return lines.drop(firstIndex + 1).joinToString("\n").trim()
}
