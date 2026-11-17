package com.elnor.app

/** One link to another note found in a text: [[Name]]. [start] to [end] is the whole "[[Name]]". */
data class NoteLinkSpan(val start: Int, val end: Int, val name: String)

/**
 * What the note page needs to follow links to other notes. [notes] are the notes a link can point
 * to (the home list and the Archive, never the Trash). [open] is called with the name of a link
 * the user chose: the screen that owns the notes opens that note, or offers to create it.
 */
class NoteLinkHost(val notes: List<Note>, val open: (name: String) -> Unit)

/**
 * Links from one note to another, written in the text as [[Name of the note]] (the way many note
 * apps do it). Nothing is stored: the links are calculated from the text, and the note they point
 * to is looked up by its name every time a link is used. So there is nothing to migrate and the
 * Backup, search and sharing need no change. The price: when a note is renamed, links that still
 * have the old name point to nothing (they offer to create a note with that name).
 *
 * Rules:
 *  - A link is two brackets, a name of 1 to [MAX_NAME_LENGTH] characters that has no bracket and
 *    no line break, and two closing brackets. Spaces at the edges of the name, and doubled
 *    spaces inside it, do not matter.
 *  - The name of a note is its title, or, when it has none, its first line without the formatting
 *    marks (the same name the notes list shows). Brackets in that name are left out, because a
 *    link cannot contain them. A note with no name (an empty note), or one whose name is longer
 *    than [MAX_NAME_LENGTH], cannot be linked to.
 *  - Names are compared without caring for upper / lower case and for the two ways of typing the
 *    same Bengali letter (the same rules as the search).
 *  - When more than one note has the name, the one with exactly the same capital letters wins, then
 *    a note that is not archived, then the one changed last.
 *
 * It has no Android code, so it is tested with plain unit tests.
 */
object NoteLinks {

    const val MAX_NAME_LENGTH = 120

    private val LINK = Regex("""\[\[([^\[\]\n]{1,$MAX_NAME_LENGTH})]]""")
    private val SPACES = Regex("""\s+""")

    /** All links of [text], in the order they appear. They never overlap. */
    fun find(text: String): List<NoteLinkSpan> {
        if (!text.contains("[[")) return emptyList()
        val result = ArrayList<NoteLinkSpan>()
        for (match in LINK.findAll(text)) {
            val name = clean(match.groupValues[1])
            if (name.isNotEmpty()) result.add(NoteLinkSpan(match.range.first, match.range.last + 1, name))
        }
        return result
    }

    /** The link the cursor is in (or right next to), or null. */
    fun linkAt(links: List<NoteLinkSpan>, cursor: Int): NoteLinkSpan? =
        links.firstOrNull { cursor in it.start..it.end }

    private fun clean(name: String): String = name.trim().replace(SPACES, " ")

    /** What two names are compared by: no capital letters, one way to write each letter. */
    fun keyOf(name: String): String = SearchText.normalize(clean(name))

    /**
     * The name to write in a link to [note], or null when the note cannot be linked to (no name,
     * or a name that is too long).
     */
    fun nameOf(note: Note): String? {
        if (note.title.isBlank() && note.body.isBlank()) return null
        val name = clean(getListTitle(note).replace('[', ' ').replace(']', ' '))
        return if (name.isEmpty() || name.length > MAX_NAME_LENGTH) null else name
    }

    /** The note a link with [name] points to, or null when there is none. */
    fun resolve(notes: List<Note>, name: String): Note? {
        val wanted = clean(name)
        val key = keyOf(wanted)
        if (key.isEmpty()) return null
        return notes
            .filter { nameOf(it)?.let(::keyOf) == key }
            .sortedWith(
                compareByDescending<Note> { nameOf(it) == wanted }
                    .thenBy { it.archivedAt != null }
                    .thenByDescending { it.updatedAt }
            )
            .firstOrNull()
    }

    /**
     * The notes to offer in the "link to a note" list: those that can be linked to, without the
     * note that is being edited ([excludeId]), whose name contains [query] (everything for a blank
     * query), the most recently changed first, at most [limit].
     */
    fun suggestions(notes: List<Note>, query: String, excludeId: Long?, limit: Int = 100): List<Note> {
        val key = keyOf(query)
        return notes
            .filter { it.id != excludeId }
            .filter { note ->
                val name = nameOf(note) ?: return@filter false
                key.isEmpty() || keyOf(name).contains(key)
            }
            .sortedByDescending { it.updatedAt }
            .take(limit)
    }

    /**
     * Writes a link with [name] into [text]: it replaces the selection (or goes to the cursor when
     * nothing is selected) and the cursor ends up right after it.
     */
    fun insertLink(text: String, selStart: Int, selEnd: Int, name: String): TextEdit {
        val a = minOf(selStart, selEnd).coerceIn(0, text.length)
        val b = maxOf(selStart, selEnd).coerceIn(a, text.length)
        val link = "[[$name]]"
        return TextEdit(text.substring(0, a) + link + text.substring(b), a + link.length, a + link.length)
    }
}
