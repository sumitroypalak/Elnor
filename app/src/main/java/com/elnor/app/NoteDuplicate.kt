package com.elnor.app

// "Duplicate" in the three-dot menu of a note.

/** Added to the end of the title of a copy: "Shopping" becomes "Shopping (copy)". */
internal const val COPY_SUFFIX = " (copy)"

/**
 * The note that "Duplicate" stores: the same text as [note], as a new, normal note.
 *  - The title gets [COPY_SUFFIX], so the two notes can be told apart. A note without a title
 *    keeps no title: its list name is calculated from the first line of the text, which is the
 *    same for the copy, so nothing is added there (the user did not type a title).
 *  - [Note.updatedAt] is [now], so the copy is the newest note.
 *  - The copy is not pinned, not archived and not in the Trash, whatever the original is. The
 *    id is 0: the repository gives the copy its own id.
 *  - The copy stays in the collection of the original (if it has one).
 */
internal fun duplicateOf(note: Note, now: Long): Note = Note(
    id = 0L,
    title = if (note.title.isBlank()) note.title else note.title + COPY_SUFFIX,
    body = note.body,
    updatedAt = now,
    deletedAt = null,
    pinnedAt = null,
    archivedAt = null,
    collectionId = note.collectionId
)
