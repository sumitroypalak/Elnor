package com.elnor.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One collection (a folder) of the Collections tab. It only has a name: which notes and tasks are
 * in it is stored on the notes and the tasks ([NoteEntity.collectionId], [TaskEntity.collectionId]).
 *
 * [name] is stored as [CollectionNames.clean] made it (no spaces at the edges, one space between
 * words). [createdAt] is only used to keep the order stable.
 */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long
)

/** The collection as the screens see it. */
data class NoteCollection(
    val id: Long,
    val name: String,
    val createdAt: Long
)

fun CollectionEntity.toCollection(): NoteCollection = NoteCollection(id, name, createdAt)
