package com.elnor.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row of the "notes" table.
 *
 * - [title] and [body] are saved exactly as the user typed them (nothing is normalized or
 *   changed). The automatic name for a note without a title ("first line" / "Untitled note")
 *   is NOT stored here. It is calculated when the list is drawn.
 * - [searchText] is the only calculated column. It is always built by [toEntity] from
 *   title and body, never typed by hand, so it cannot get out of date.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val searchText: String
)

fun NoteEntity.toNote(): Note = Note(id = id, title = title, body = body, updatedAt = updatedAt)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    updatedAt = updatedAt,
    searchText = SearchText.build(title, body)
)
