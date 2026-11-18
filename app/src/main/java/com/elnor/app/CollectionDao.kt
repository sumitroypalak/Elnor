package com.elnor.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Storage queries for the Collections tab. The order of the list is decided in Kotlin
 * (see [sortCollections]), so this only reads and writes rows. It is an abstract class (not an
 * interface) because [delete] is one transaction made of several queries.
 */
@Dao
abstract class CollectionDao {

    /** Every collection. Updates by itself when one changes. */
    @Query("SELECT * FROM collections")
    abstract fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections")
    abstract suspend fun getAll(): List<CollectionEntity>

    /** Returns the new row id. */
    @Insert
    abstract suspend fun insert(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    /** Takes every note out of the collection (also the ones in the Archive and the Trash). */
    @Query("UPDATE notes SET collectionId = NULL WHERE collectionId = :id")
    abstract suspend fun clearNotes(id: Long)

    @Query("UPDATE tasks SET collectionId = NULL WHERE collectionId = :id")
    abstract suspend fun clearTasks(id: Long)

    @Query("DELETE FROM collections WHERE id = :id")
    abstract suspend fun deleteRow(id: Long)

    /**
     * Deletes the collection. Its notes and tasks are NOT deleted: they stay, in no collection. All
     * three steps happen together or not at all, so no note is left pointing to a collection that
     * does not exist.
     */
    @Transaction
    open suspend fun delete(id: Long) {
        clearNotes(id)
        clearTasks(id)
        deleteRow(id)
    }
}
