package com.elnor.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the "notes" table.
 *
 * - [title] and [body] are saved as the user typed them, except that spaces and line breaks at
 *   the very start and the very end are removed (NoteEditorViewModel trims them before saving).
 *   Nothing else is changed: no normalization, and spaces and line breaks inside the text stay.
 *   The automatic name for a note without a title ("first line" / "Untitled note")
 *   is NOT stored here. It is calculated when the list is drawn.
 * - [searchText] is the only calculated column. It is always built by [toEntity] from
 *   title and body, never typed by hand, so it cannot get out of date.
 * - [deletedAt] is null for a normal note. When the note is deleted it is not removed: this
 *   column gets the time of the deletion and the note is then "in the Trash". Nothing else
 *   of the note changes (title, body and updatedAt stay as they were), so restoring it puts
 *   everything back exactly as it was.
 * - [pinnedAt] is null for a normal note. A pinned note has the time it was pinned. Pinned notes
 *   are listed first (the most recently pinned on top). A note that goes to the Trash is unpinned.
 *   Saving the text never changes it.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val searchText: String,
    val deletedAt: Long? = null,
    val pinnedAt: Long? = null
)

fun NoteEntity.toNote(): Note = Note(
    id = id,
    title = title,
    body = body,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    pinnedAt = pinnedAt
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    updatedAt = updatedAt,
    searchText = SearchText.build(title, body),
    deletedAt = deletedAt,
    pinnedAt = pinnedAt
)
